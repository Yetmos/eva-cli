package com.eva_cli.core.widgets.provider

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.provider.impl.task.CheckRequest
import com.eva_cli.core.widgets.provider.impl.task.CheckResult
import com.eva_cli.core.widgets.provider.impl.task.CheckerCapability
import com.eva_cli.core.widgets.provider.impl.task.PlanRequest
import com.eva_cli.core.widgets.provider.impl.task.PlanResult
import com.eva_cli.core.widgets.provider.impl.task.PlanStep
import com.eva_cli.core.widgets.provider.impl.task.PlannerCapability
import com.eva_cli.core.widgets.provider.impl.task.TaskPlannerProvider
import com.eva_cli.core.widgets.provider.impl.task.TaskState
import com.eva_cli.core.widgets.provider.impl.task.TaskExecutionContext
import com.eva_cli.core.widgets.provider.impl.task.TaskCapability
import com.eva_cli.core.widgets.provider.impl.task.TaskResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TaskPlannerProviderTest {
    @Test
    fun runsPlanStepsAndChecksEachResult() = runBlocking {
        val provider = FixturePlanner()
        provider.start()

        val result = provider.run(PlanRequest(input = "hello"))

        assertEquals(TaskState.SUCCEEDED, result.state)
        assertEquals(listOf("first", "second"), result.steps.map { it.step.id })
        assertTrue(result.steps.all { it.check.passed })
        assertEquals(listOf("first", "second"), provider.executed)
        provider.stop()
    }

    @Test
    fun stopsWhenCheckerRejectsAResult() = runBlocking {
        val provider = RejectingPlanner()
        provider.start()

        val result = provider.run(PlanRequest(input = "hello"))

        assertEquals(TaskState.FAILED, result.state)
        assertEquals(listOf("first"), result.steps.map { it.step.id })
        assertEquals("rejected", result.detail)
        provider.stop()
    }

    @Test
    fun delegatesStepsToExternalTaskCapability() = runBlocking {
        val provider = ExternalTaskPlanner()
        provider.start()

        val result = provider.run(PlanRequest(input = "hello"))

        assertEquals(TaskState.SUCCEEDED, result.state)
        assertEquals(listOf("first", "second"), provider.delegated)
        assertEquals(listOf("external:first", "external:second"), result.steps.map { it.task.value })
        provider.stop()
    }

    @Test
    fun mapsCancellationToCancelledStepState() = runBlocking {
        val provider = CancellingPlanner()
        provider.start()

        val result = provider.run(PlanRequest(input = "hello"))

        assertEquals(TaskState.CANCELLED, result.state)
        assertEquals(TaskState.CANCELLED, result.steps.single().task.state)
        provider.stop()
    }

    private open class FixturePlanner : TaskPlannerProvider() {
        override val id = Uuid.random()
        override val name = "Task planner fixture"
        override val desc = "Task planner framework fixture"
        override val topic = "/TaskPlanner/Test"
        override var enable = true
        override var records: List<EventRecord> = emptyList()
        override var emit: (suspend (Event) -> Unit)? = null

        val executed = mutableListOf<String>()

        override val planner = object : PlannerCapability {
            override suspend fun plan(request: PlanRequest) = PlanResult(
                steps = listOf(
                    PlanStep(id = "first", payload = "one"),
                    PlanStep(id = "second", payload = "two", topic = "/TaskPlanner/Test"),
                ),
            )
        }

        override val checker = object : CheckerCapability {
            override suspend fun check(request: CheckRequest) = CheckResult.passed()
        }

        override suspend fun executeStep(context: TaskExecutionContext): Any? {
            executed += context.step.id
            return context.step.payload
        }
    }

    private class RejectingPlanner : FixturePlanner() {
        override val checker = object : CheckerCapability {
            override suspend fun check(request: CheckRequest) = CheckResult.rejected("rejected")
        }
    }

    private class ExternalTaskPlanner : FixturePlanner() {
        val delegated = mutableListOf<String>()

        override val taskProvider: TaskCapability = object : TaskCapability {
            override suspend fun execute(
                step: PlanStep,
                context: TaskExecutionContext,
            ): TaskResult {
                delegated += step.id
                return TaskResult(TaskState.SUCCEEDED, value = "external:${step.id}")
            }
        }

        override suspend fun executeStep(context: TaskExecutionContext): Any? =
            error("TaskPlannerProvider should delegate to TaskCapability.execute")
    }

    private class CancellingPlanner : FixturePlanner() {
        override suspend fun executeStep(context: TaskExecutionContext): Any? {
            throw CancellationException("cancelled by test")
        }
    }
}
