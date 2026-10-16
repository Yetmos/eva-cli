package com.eva_cli.core.widgets.provider.impl

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/** 提交给 [TaskCapability] 的任务描述。 */
data class TaskRequest(
    val name: String = "",
    val event: Event? = null,
    val context: Map<String, String> = emptyMap(),
)

/** 已提交任务的不透明句柄。 */
data class TaskHandle(val id: Uuid = Uuid.random())

enum class TaskState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/** 任务终态或执行中状态的快照。 */
data class TaskResult(
    val state: TaskState,
    val value: Any? = null,
    val detail: String? = null,
)

/** 执行可取消工作单元的 Provider 能力。 */
interface TaskCapability {
    /**
     * 提交任务执行。返回的句柄可用于取消任务或查询状态，执行结果会保存到 [TaskResult.value]。
     */
    suspend fun submit(
        request: TaskRequest = TaskRequest(),
        action: suspend () -> Any? = { Unit },
    ): TaskHandle

    suspend fun cancel(handle: TaskHandle): Boolean

    fun state(handle: TaskHandle): TaskState?

    fun taskState(handle: TaskHandle): TaskState? = state(handle)

    fun result(handle: TaskHandle): TaskResult? = state(handle)?.let(::TaskResult)

    fun taskResult(handle: TaskHandle): TaskResult? = result(handle)
}

/**
 * 基于协程的轻量任务执行 Provider。
 *
 * 任务完成后仍可通过 [state] 查询任务历史。执行器的生命周期绑定到 Provider，
 * 并在 [stop] 时取消，因此宿主可以安全停止 Dispatcher 树而不遗留任务。
 */
abstract class TaskProvider : PluginProvider(), TaskCapability {
    override var status: Provider.Status = Provider.Status.STOP

    private var taskScope: CoroutineScope? = null
    private val jobs = mutableMapOf<TaskHandle, Job>()
    private val states = mutableMapOf<TaskHandle, TaskState>()
    private val results = mutableMapOf<TaskHandle, TaskResult>()

    override suspend fun start() {
        if (status != Provider.Status.STOP) return
        status = Provider.Status.STARTING
        taskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        status = Provider.Status.IDLE
    }

    override suspend fun stop() {
        jobs.values.toList().forEach { it.cancel() }
        jobs.clear()
        states.keys.forEach { handle ->
            if (states[handle] == TaskState.QUEUED || states[handle] == TaskState.RUNNING) {
                states[handle] = TaskState.CANCELLED
                results[handle] = TaskResult(TaskState.CANCELLED)
            }
        }
        taskScope?.cancel()
        taskScope = null
        status = Provider.Status.STOP
    }

    override suspend fun submit(
        request: TaskRequest,
        action: suspend () -> Any?,
    ): TaskHandle {
        val scope = checkNotNull(taskScope) {
            "TaskProvider must be started before submitting a task"
        }
        check(status != Provider.Status.STOP) {
            "TaskProvider is stopped"
        }

        val handle = TaskHandle()
        states[handle] = TaskState.QUEUED
        results[handle] = TaskResult(TaskState.QUEUED)
        // 先登记句柄再执行，避免极短任务在取消或状态查询可见前就已经完成。
        val job = scope.launch(start = CoroutineStart.LAZY) {
            states[handle] = TaskState.RUNNING
            results[handle] = TaskResult(TaskState.RUNNING)
            status = Provider.Status.WORKING
            try {
                val value = action()
                states[handle] = TaskState.SUCCEEDED
                results[handle] = TaskResult(TaskState.SUCCEEDED, value = value)
            } catch (cancelled: CancellationException) {
                states[handle] = TaskState.CANCELLED
                results[handle] = TaskResult(TaskState.CANCELLED)
                throw cancelled
            } catch (failure: Exception) {
                states[handle] = TaskState.FAILED
                results[handle] = TaskResult(
                    state = TaskState.FAILED,
                    detail = failure.message,
                )
            } finally {
                jobs.remove(handle)
                if (jobs.isEmpty() && status != Provider.Status.STOP) {
                    status = Provider.Status.IDLE
                }
            }
        }
        jobs[handle] = job
        job.invokeOnCompletion { jobs.remove(handle) }
        job.start()
        return handle
    }

    override suspend fun cancel(handle: TaskHandle): Boolean {
        val job = jobs.remove(handle) ?: return false
        states[handle] = TaskState.CANCELLED
        results[handle] = TaskResult(TaskState.CANCELLED)
        job.cancel()
        if (jobs.isEmpty() && status != Provider.Status.STOP) {
            status = Provider.Status.IDLE
        }
        return true
    }

    override fun state(handle: TaskHandle): TaskState? = states[handle]

    override fun result(handle: TaskHandle): TaskResult? = results[handle]
}
