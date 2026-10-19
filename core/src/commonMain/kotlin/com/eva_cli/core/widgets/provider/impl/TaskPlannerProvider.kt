package com.eva_cli.core.widgets.provider.impl

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.Result
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider
import kotlinx.coroutines.CancellationException

/** Context passed to one task step implementation. */
data class TaskExecutionContext(
    val request: PlanRequest,
    val plan: PlanResult,
    val step: PlanStep,
    val event: Event? = request.event,
)

data class TaskStepResult(
    val step: PlanStep,
    val task: TaskResult,
    val check: CheckResult,
)

data class TaskRunResult(
    val state: TaskState,
    val plan: PlanResult,
    val steps: List<TaskStepResult> = emptyList(),
    val detail: String? = null,
)

/**
 * Core task-planning Provider that plugins can inherit.
 *
 * The base class owns the plan -> execute -> check flow. A plugin normally
 * overrides [planner] and [checker], then either supplies a [taskProvider] or
 * overrides [executeStep] for inline execution. The plugin also supplies the
 * regular Widget metadata required by [PluginProvider].
 */
abstract class TaskPlannerProvider : PluginProvider() {
    override var status: Provider.Status = Provider.Status.STOP

    protected open val planner: PlannerCapability = object : PlannerCapability {}
    protected open val taskProvider: TaskCapability? = null
    protected open val checker: CheckerCapability = object : CheckerCapability {}

    override suspend fun start() {
        if (status != Provider.Status.STOP) return
        status = Provider.Status.STARTING
        try {
            onStart()
            status = Provider.Status.IDLE
        } catch (failure: Throwable) {
            status = Provider.Status.STOP
            throw failure
        }
    }

    override suspend fun stop() {
        if (status == Provider.Status.STOP) return
        onStop()
        status = Provider.Status.STOP
    }

    /** Hook for a plugin to start an injected external capability. */
    protected open suspend fun onStart() = Unit

    /** Hook for a plugin to stop an injected external capability. */
    protected open suspend fun onStop() = Unit

    /** Converts the default text event into a planning request. */
    protected open fun requestFrom(event: Event): PlanRequest? =
        (event.payload as? Event.Payload.Text)?.let {
            PlanRequest(input = it.value, event = event)
        }

    /** Executes one step when no external [taskProvider] is supplied. */
    protected open suspend fun executeStep(context: TaskExecutionContext): Any? =
        context.step.payload ?: context.step.description

    /** Runs planning, task execution and checking without requiring an Event. */
    suspend fun run(request: PlanRequest): TaskRunResult {
        check(status != Provider.Status.STOP) {
            "TaskPlannerProvider must be started before running a task"
        }

        val plan = planner.plan(request)
        if (!plan.complete) {
            return TaskRunResult(TaskState.QUEUED, plan, detail = plan.detail)
        }

        val completed = mutableSetOf<String>()
        val stepResults = mutableListOf<TaskStepResult>()
        for (step in plan.steps) {
            if (!step.dependsOn.all(completed::contains)) {
                return TaskRunResult(
                    state = TaskState.FAILED,
                    plan = plan,
                    steps = stepResults,
                    detail = "Task step dependencies are not satisfied: ${step.id}",
                )
            }

            val context = TaskExecutionContext(request, plan, step, request.event)
            val taskResult = executeTask(context)
            val check = if (taskResult.state == TaskState.SUCCEEDED) {
                checker.check(
                    CheckRequest(
                        value = taskResult.value,
                        event = request.event,
                        context = step.context,
                    ),
                )
            } else {
                CheckResult.rejected(taskResult.detail ?: "Task step did not succeed")
            }
            val stepResult = TaskStepResult(step, taskResult, check)
            stepResults += stepResult
            if (taskResult.state != TaskState.SUCCEEDED || !check.passed) {
                return TaskRunResult(
                    state = if (taskResult.state == TaskState.CANCELLED) {
                        TaskState.CANCELLED
                    } else {
                        TaskState.FAILED
                    },
                    plan = plan,
                    steps = stepResults,
                    detail = check.detail ?: taskResult.detail,
                )
            }
            completed += step.id
        }

        return TaskRunResult(TaskState.SUCCEEDED, plan, stepResults, plan.detail)
    }

    private suspend fun executeTask(context: TaskExecutionContext): TaskResult {
        val provider = taskProvider
        return try {
            if (provider == null) {
                TaskResult(TaskState.SUCCEEDED, value = executeStep(context))
            } else {
                // Execute through the capability contract so external providers can
                // apply their own queueing, retry, timeout, and tool integration.
                provider.execute(context.step, context)
            }
        } catch (cancelled: CancellationException) {
            TaskResult(TaskState.CANCELLED, detail = cancelled.message)
        } catch (failure: Exception) {
            TaskResult(TaskState.FAILED, detail = failure.message)
        }
    }

    override suspend fun receive(event: Event) {
        if (!enable || status == Provider.Status.STOP) return
        val request = requestFrom(event)
        if (request == null) {
            record(event, Result.END, "TaskPlannerProvider only supports text events by default")
            update(event)
            return
        }

        status = Provider.Status.WORKING
        try {
            val result = run(request)
            val eventResult = when (result.state) {
                TaskState.SUCCEEDED -> Result.DONE
                TaskState.PENDING, TaskState.QUEUED -> Result.DO_BACK
                TaskState.FAILED, TaskState.CANCELLED, TaskState.RUNNING -> Result.BACK
            }
            record(event, eventResult, result.detail)
            update(event)
            onResult(event, result)
        } finally {
            if (status != Provider.Status.STOP) status = Provider.Status.IDLE
        }
    }

    /** Hook for a plugin to emit a domain-specific result event. */
    protected open suspend fun onResult(event: Event, result: TaskRunResult) = Unit
}
