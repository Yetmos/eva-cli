package com.eva_cli.core.widgets.provider.impl

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** 一次性或周期性定时器的描述。 */
data class TimerSpec(
    val delay: Duration = Duration.ZERO,
    val interval: Duration? = null,
) {
    init {
        require(!delay.isNegative()) { "Timer delay must not be negative" }
        require(interval == null || interval > Duration.ZERO) {
            "Timer interval must be positive"
        }
    }
}

/** 调度定时器后返回的不透明句柄。 */
data class TimerHandle(val id: Uuid = Uuid.random())

/** 由定时器 Provider 和定时器调用方共用的调度能力。 */
interface TimerCapability {
    suspend fun schedule(spec: TimerSpec, action: suspend () -> Unit): TimerHandle

    suspend fun schedule(delay: Duration, action: suspend () -> Unit): TimerHandle =
        schedule(TimerSpec(delay = delay), action)

    suspend fun cancel(handle: TimerHandle): Boolean
}

/**
 * 基于协程的轻量定时器 Provider。
 *
 * 调度器由 [start] 创建，并在 [stop] 时取消。因此宿主停止 Provider 树后，
 * 不会遗留仍在运行的定时器。该类只暴露不透明句柄，调用方不依赖具体协程实现。
 */
abstract class TimerProvider : PluginProvider(), TimerCapability {
    override var status: Provider.Status = Provider.Status.STOP

    override suspend fun receive(event: Event) = Unit

    private var timerScope: CoroutineScope? = null
    private val jobs = mutableMapOf<TimerHandle, Job>()

    override suspend fun start() {
        if (status != Provider.Status.STOP) return
        status = Provider.Status.STARTING
        timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        status = Provider.Status.IDLE
    }

    override suspend fun stop() {
        jobs.values.toList().forEach(Job::cancel)
        jobs.clear()
        timerScope?.cancel()
        timerScope = null
        status = Provider.Status.STOP
    }

    override suspend fun schedule(
        spec: TimerSpec,
        action: suspend () -> Unit,
    ): TimerHandle {
        val scope = checkNotNull(timerScope) {
            "TimerProvider must be started before scheduling a timer"
        }
        check(status != Provider.Status.STOP) {
            "TimerProvider is stopped"
        }

        val handle = TimerHandle()
        // 延迟启动，确保先登记句柄，再执行可能立即完成并移除自身的零延迟回调。
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(spec.delay)
            do {
                action()
                val interval = spec.interval ?: break
                delay(interval)
            } while (isActive)
        }
        jobs[handle] = job
        job.invokeOnCompletion { jobs.remove(handle) }
        job.start()
        return handle
    }

    override suspend fun cancel(handle: TimerHandle): Boolean =
        jobs.remove(handle)?.let {
            it.cancel()
            true
        } ?: false
}
