package com.eva_cli.core.io

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * 全局输入、输出管理器。每次启动固定已注册的 Provider 列表，注册变更在下次启动生效。
 * enable 在每次输入处理、输出投递前读取，运行中修改即可生效，不改变 Provider 的生命周期。
 * Provider 的 status 由平台实现维护。
 */
object IOManager {
    private val lock = SynchronizedObject()
    private var initialized = false
    private var changingLifecycle = false
    private var session: Session? = null
    private var stoppingSession: Session? = null

    val isStarted: Boolean
        get() = synchronized(lock) { session != null }

    // 启动所有已注册的 Provider，并将启用的输入交给 [IOHandler]。重复调用不会重复启动。
    fun start() = synchronized(lock) {
        check(!changingLifecycle) { "Cannot start IOManager from a Provider lifecycle callback" }
        if (session != null) return@synchronized
        check(stoppingSession?.job?.isCompleted != false) {
            "IOManager is still stopping; call stopAndJoin() before restarting"
        }
        stoppingSession = null
        changingLifecycle = true
        val started = mutableListOf<IOProvider>()
        try {
            if (!initialized) {
                initIOProviders()
                initialized = true
            }
            val inputs = inProviders.distinctInstances()
            val outputs = outProviders.distinctInstances()
            val providers = (inputs + outputs).distinctInstances()
            for (provider in providers) {
                // start 抛错的 Provider 也可能已分配资源，必须参与回滚。
                started.add(provider)
                provider.start()
            }
            val active = Session(inputs, outputs, providers)
            session = active
            active.startInputs()
        } catch (failure: Throwable) {
            session?.let {
                session = null
                stoppingSession = it
                it.job.cancel()
            }
            stopProviders(started, failure)
            throw failure
        } finally {
            changingLifecycle = false
        }
    }

    /** 取消在途 IO 并停止全部 Provider；需要等待协程清理时使用 [stopAndJoin]。 */
    fun stop() = synchronized(lock) { stopLocked() }

    /** 停止并等待当前运行的所有输入、输出协程退出，可用于安全重启。 */
    suspend fun stopAndJoin() {
        val (stopped, failure) = synchronized(lock) {
            check(!changingLifecycle) { "Cannot stop IOManager from a Provider lifecycle callback" }
            val current = session ?: stoppingSession
            val failure = runCatching { stopLocked() }.exceptionOrNull()
            current to failure
        }
        stopped?.job?.join()
        failure?.let { throw it }
    }

    /**
     * 按注册顺序投递输出。providerId 为 null 时广播，否则仅投递给 ID 匹配的输出 Provider。
     * 每次投递前检查 enable，跳过已禁用的 Provider；已开始的投递不会因禁用而取消。
     * 显式指定的 ID 不存在或调用时没有启用的匹配项时抛出 IllegalArgumentException。
     * 普通失败在完成其他投递后聚合抛出；取消立即中断投递。
     */
    suspend fun emit(event: OutputEvent, providerId: String? = null) {
        val callerContext = currentCoroutineContext()
        callerContext.ensureActive()
        val active = synchronized(lock) { checkNotNull(session) { "IOManager is not started" } }
        val targets = if (providerId == null) active.outputs else {
            active.outputs.filter { it.id == providerId }.also {
                require(it.any { provider -> provider.enable }) {
                    "No enabled output Provider with id '$providerId'"
                }
            }
        }
        // 由运行会话管理投递任务，同时保留调用方的调度器和协程上下文。
        val delivery = active.scope.async(callerContext.minusKey(Job)) {
            var failure: Throwable? = null
            for (provider in targets) {
                currentCoroutineContext().ensureActive()
                try {
                    if (!provider.enable) continue
                    provider.emit(event.copy(metadata = event.metadata.copy(providerId = provider.id)))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (cause: Throwable) {
                    failure = aggregate(failure, cause)
                }
            }
            // 作为结果返回，避免 await 的堆栈恢复复制异常时丢失 suppressedExceptions。
            failure
        }
        try {
            delivery.await()?.let { throw it }
        } finally {
            // 调用方取消时也取消本次投递，并等待其 finally 清理完成。
            withContext(NonCancellable) { delivery.cancelAndJoin() }
        }
    }

    private fun stopLocked() {
        check(!changingLifecycle) { "Cannot stop IOManager from a Provider lifecycle callback" }
        val active = session ?: return
        changingLifecycle = true
        session = null
        stoppingSession = active
        try {
            active.job.cancel()
            stopProviders(active.providers)?.let { throw it }
        } finally {
            changingLifecycle = false
        }
    }

    private fun stopProviders(providers: List<IOProvider>, initialFailure: Throwable? = null): Throwable? {
        var failure = initialFailure
        for (provider in providers.asReversed()) {
            try {
                provider.stop()
            } catch (cause: Throwable) {
                failure = aggregate(failure, cause)
            }
        }
        return failure
    }

    private fun aggregate(failure: Throwable?, cause: Throwable): Throwable =
        failure?.also { if (it !== cause) it.addSuppressed(cause) } ?: cause

    private fun <T : IOProvider> List<T>.distinctInstances(): List<T> =
        fold(mutableListOf()) { unique, provider ->
            if (unique.none { it === provider }) unique.add(provider)
            unique
        }

    private class Session(
        val inputs: List<InProvider>,
        val outputs: List<OutProvider>,
        val providers: List<IOProvider>,
    ) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)

        fun startInputs() {
            inputs.forEach { provider -> scope.launch { readInput(provider) } }
        }

        private suspend fun readInput(provider: InProvider) {
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val received = provider.receive() ?: break
                    currentCoroutineContext().ensureActive()
                    // 接收期间也可能切换开关，因此收到后再检查；禁用期间的输入直接丢弃。
                    if (provider.enable) {
                        val event = received.copy(metadata = received.metadata.copy(providerId = provider.id))
                        IOHandler.handleInput(event)
                    }
                    yield()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                println("[IOManager] Input Provider '${provider.id}' failed: $failure")
            }
        }
    }
}
