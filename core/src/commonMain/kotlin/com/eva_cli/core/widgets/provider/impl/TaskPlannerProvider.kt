package com.eva_cli.core.widgets.provider.impl

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.Result
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider
import kotlinx.coroutines.CancellationException

/** 传递给单个任务步骤实现的上下文。 */
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
 * 供插件继承的核心任务规划 Provider。
 *
 * 基类负责计划 -> 执行 -> 检查流程。插件通常覆盖 [planner] 和 [checker]，
 * 然后提供 [taskProvider]，或覆盖 [executeStep] 以内置方式执行步骤。
 * 插件还需要提供 [PluginProvider] 要求的常规 Widget 元数据。
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

    /** 插件启动注入的外部能力时使用的扩展钩子。 */
    protected open suspend fun onStart() = Unit

    /** 插件停止注入的外部能力时使用的扩展钩子。 */
    protected open suspend fun onStop() = Unit

    /** 将默认文本事件转换为规划请求。 */
    protected open fun requestFrom(event: Event): PlanRequest? =
        (event.payload as? Event.Payload.Text)?.let {
            PlanRequest(input = it.value, event = event)
        }

    /** 未提供外部 [taskProvider] 时执行单个步骤。 */
    protected open suspend fun executeStep(context: TaskExecutionContext): Any? =
        context.step.payload ?: context.step.description

    /** 无需 Event 即可运行规划、任务执行和检查流程。 */
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
                // 通过能力契约执行，使外部 Provider 可以应用自己的排队、重试、超时和工具集成策略。
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

    /** 插件发送领域专用结果事件时使用的扩展钩子。 */
    protected open suspend fun onResult(event: Event, result: TaskRunResult) = Unit
}
