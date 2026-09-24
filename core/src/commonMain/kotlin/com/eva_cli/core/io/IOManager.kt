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
import kotlin.uuid.Uuid

/**
 * 全局输入、输出管理器。运行中注册 Provider 会立即启动并加入当前会话。
 * enable 在每次输入处理、输出投递前读取，运行中修改即可生效，不改变 Provider 的生命周期。
 * Provider 的 status 由平台实现维护。
 */
object IOManager {
    private val lock = SynchronizedObject()
    private val inProviders = LinkedHashMap<Uuid, InProvider>()
    private val outProviders = LinkedHashMap<Uuid, OutProvider>()
    private var initialized = false
    private var changingLifecycle = false
    private var inProviderCallback = false
    private var session: Session? = null
    private var stoppingSession: Session? = null

    val isStarted: Boolean
        get() = synchronized(lock) { session != null }

    /** 注册输入；运行中立即启动并接收。同一实例重复注册不改变顺序；不同实例共用 ID 时抛出 IllegalArgumentException。 */
    fun registerInput(provider: InProvider): Unit = synchronized(lock) {
        register(provider)
        inProviders[provider.id] = provider
        session?.let { active ->
            if (active.inputs.put(provider.id, provider) == null) active.startInput(provider)
        }
    }

    /** 注册输出；运行中立即启动并参与后续投递。同一实例重复注册不改变顺序；ID 在输入、输出之间也必须唯一。 */
    fun registerOutput(provider: OutProvider): Unit = synchronized(lock) {
        register(provider)
        outProviders[provider.id] = provider
        session?.outputs?.set(provider.id, provider)
    }

    /** 移除输入注册并返回原实例；不影响当前会话，也不移除同一实例的输出注册。 */
    fun unregisterInput(providerId: Uuid): InProvider? = synchronized(lock) {
        checkNotInProviderCallback()
        inProviders.remove(providerId)
    }

    /** 移除输出注册并返回原实例；不影响当前会话，也不移除同一实例的输入注册。 */
    fun unregisterOutput(providerId: Uuid): OutProvider? = synchronized(lock) {
        checkNotInProviderCallback()
        outProviders.remove(providerId)
    }

    /** 清空注册表，下次启动生效；当前会话仍使用已有 Provider，直到停止。 */
    fun clearProviders() = synchronized(lock) {
        checkNotInProviderCallback()
        inProviders.clear()
        outProviders.clear()
    }

    private fun register(provider: IOProvider) {
        checkNotInProviderCallback()
        val id = provider.id
        val active = session
        val registered = inProviders[id] ?: outProviders[id] ?: active?.providers?.get(id)
        require(registered == null || registered === provider) { "Duplicate IOProvider id '$id'" }
        if (active != null && id !in active.providers) {
            try {
                providerCallback { provider.start() }
            } catch (failure: Throwable) {
                stopProviders(listOf(provider), failure)
                throw failure
            }
            active.providers[id] = provider
        }
    }

    /**
     * 启动所有已注册的 Provider，并将启用的输入交给 [IOHandler]。重复调用不会重复启动。
     * 同时注册为输入和输出的实例只启动一次；停止后保留注册结果。
     */
    fun start() = synchronized(lock) {
        checkNotInProviderCallback()
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
            val inputs = LinkedHashMap(inProviders)
            val outputs = LinkedHashMap(outProviders)
            val providers = LinkedHashMap<Uuid, IOProvider>()
            providers.putAll(inputs)
            providers.putAll(outputs)
            for (provider in providers.values) {
                // start 抛错的 Provider 也可能已分配资源，必须参与回滚。
                started.add(provider)
                providerCallback { provider.start() }
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
            checkNotInProviderCallback()
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
     * 显式指定的 ID 不存在或对应 Provider 未启用时抛出 IllegalArgumentException。
     * 普通失败在完成其他投递后聚合抛出；取消立即中断投递。
     */
    suspend fun emit(event: OutputEvent, providerId: Uuid? = null) {
        val callerContext = currentCoroutineContext()
        callerContext.ensureActive()
        val (active, targets) = synchronized(lock) {
            val active = checkNotNull(session) { "IOManager is not started" }
            val targets = if (providerId == null) active.outputs.values.toList() else {
                val target = active.outputs[providerId]
                require(target != null && target.enable) {
                    "No enabled output Provider with id '$providerId'"
                }
                listOf(target)
            }
            active to targets
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
        checkNotInProviderCallback()
        check(!changingLifecycle) { "Cannot stop IOManager from a Provider lifecycle callback" }
        val active = session ?: return
        changingLifecycle = true
        session = null
        stoppingSession = active
        try {
            active.job.cancel()
            stopProviders(active.providers.values.toList())?.let { throw it }
        } finally {
            changingLifecycle = false
        }
    }

    private fun stopProviders(providers: List<IOProvider>, initialFailure: Throwable? = null): Throwable? {
        var failure = initialFailure
        for (provider in providers.asReversed()) {
            try {
                providerCallback { provider.stop() }
            } catch (cause: Throwable) {
                failure = aggregate(failure, cause)
            }
        }
        return failure
    }

    private fun aggregate(failure: Throwable?, cause: Throwable): Throwable =
        failure?.also { if (it !== cause) it.addSuppressed(cause) } ?: cause

    private fun checkNotInProviderCallback() {
        check(!inProviderCallback) { "Cannot change IOManager from a Provider lifecycle callback" }
    }

    private inline fun <T> providerCallback(block: () -> T): T {
        inProviderCallback = true
        return try {
            block()
        } finally {
            inProviderCallback = false
        }
    }

    private class Session(
        val inputs: LinkedHashMap<Uuid, InProvider>,
        val outputs: LinkedHashMap<Uuid, OutProvider>,
        val providers: LinkedHashMap<Uuid, IOProvider>,
    ) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)

        fun startInputs() {
            inputs.values.forEach { startInput(it) }
        }

        fun startInput(provider: InProvider) {
            scope.launch { readInput(provider) }
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
