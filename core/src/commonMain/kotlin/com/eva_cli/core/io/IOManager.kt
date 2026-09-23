package com.eva_cli.core.io

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.selectUnbiased

/**
 * 全局输入、输出适配器注册表，负责所有 Provider 的生命周期。
 *
 * 输入并发合并，输出按注册顺序广播。运行中添加 Provider 会立即启动，移除时会停止。
 * 同一个实例只注册一次；停止管理器会保留注册列表，供下次启动使用。
 * Provider 的 stop 实现应能安全处理 start 部分完成或失败的情况。
 */
object IOManager {

    /**
     * 保存 IOManager 从启动到停止这一轮运行的资源。
     *
     * 每次启动时新建，停止时取消其中的读取任务并关闭 Channel，避免重用已取消的资源。
     * Provider 注册列表由 IOManager 保留，重启时据此创建新一轮的 Session。
     * 正在等待的 receive() 绑定当前 Session，确保前后两轮运行的输入相互隔离。
     */
    private class Session {
        // 统一管理本轮输入读取协程，停止管理器时整体取消。
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // 本轮输入任务列表；增删时通知等待中的 receive() 重新选择输入源。
        val inputs = MutableStateFlow<List<Input>>(emptyList())

        // 为已启动的 Provider 创建读取任务，先加入列表，再开始读取事件。
        fun addInput(provider: InputProvider) {
            val input = Input(provider, scope)
            inputs.value += input
            input.job.start()
        }
    }

    /**
     * 将单个输入提供者包装为可由 [IOManager.receive] 统一消费的事件通道。
     *
     * 每个输入提供者对应一个实例；实例负责在独立协程中读取事件，并在结束或失败时
     * 关闭通道。调用 [cancel] 会同时停止读取协程和取消通道。
     */
    private class Input(val provider: InputProvider, scope: CoroutineScope) {
        /** 输入提供者产生的事件通道；使用 rendezvous 模式逐个交付事件。 */
        val events = Channel<InputEvent>(Channel.RENDEZVOUS)

        /** 延迟启动的读取任务，持续从 [provider] 接收事件并发送到 [events]。 */
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (true) {
                    val event = provider.receive() ?: break
                    events.send(event)
                }
                events.close()
            } catch (cause: Throwable) {
                events.close(cause)
                if (cause is CancellationException) throw cause
            }
        }

        /** 取消读取任务并关闭事件通道，使等待中的接收操作及时结束。 */
        fun cancel() {
            job.cancel()
            events.cancel()
        }
    }

    private val lock = SynchronizedObject()
    private val registeredInputs = mutableListOf<InputProvider>()
    private val registeredOutputs = mutableListOf<OutputProvider>()
    private var session: Session? = null

    val inputProviders: List<InputProvider>
        get() = synchronized(lock) { registeredInputs.toList() }

    val outputProviders: List<OutputProvider>
        get() = synchronized(lock) { registeredOutputs.toList() }

    val isStarted: Boolean
        get() = synchronized(lock) { session != null }

    fun addInputProvider(provider: InputProvider): Boolean = synchronized(lock) {
        if (registeredInputs.any { it === provider }) return@synchronized false

        val current = session
        if (current != null) startProvider(provider::start, provider::stop)
        registeredInputs.add(provider)
        current?.addInput(provider)
        true
    }

    fun removeInputProvider(provider: InputProvider): Boolean = synchronized(lock) {
        val index = registeredInputs.indexOfFirst { it === provider }
        if (index < 0) return@synchronized false

        registeredInputs.removeAt(index)
        session?.let { current ->
            val input = current.inputs.value.first { it.provider === provider }
            current.inputs.value = current.inputs.value.filterNot { it === input }
            input.cancel()
            provider.stop()
        }
        true
    }

    fun addOutputProvider(provider: OutputProvider): Boolean = synchronized(lock) {
        if (registeredOutputs.any { it === provider }) return@synchronized false

        if (session != null) startProvider(provider::start, provider::stop)
        registeredOutputs.add(provider)
        true
    }

    fun removeOutputProvider(provider: OutputProvider): Boolean = synchronized(lock) {
        val index = registeredOutputs.indexOfFirst { it === provider }
        if (index < 0) return@synchronized false

        registeredOutputs.removeAt(index)
        if (session != null) provider.stop()
        true
    }

    fun start(): Unit = synchronized(lock) {
        if (session != null) return@synchronized

        val cleanup = mutableListOf<() -> Unit>()
        try {
            registeredInputs.forEach { provider ->
                cleanup.add(provider::stop)
                provider.start()
            }
            registeredOutputs.forEach { provider ->
                cleanup.add(provider::stop)
                provider.start()
            }
        } catch (cause: Throwable) {
            cleanup.asReversed().forEach { stopAfterStartFailure(it, cause) }
            throw cause
        }

        session = Session().also { current ->
            registeredInputs.forEach(current::addInput)
        }
    }

    fun stop(): Unit = synchronized(lock) {
        val current = session ?: return@synchronized
        session = null
        val inputs = current.inputs.value
        current.inputs.value = emptyList()
        inputs.forEach { it.cancel() }
        current.scope.cancel()

        var failure: Throwable? = null
        val stops = registeredInputs.map { it::stop } + registeredOutputs.map { it::stop }
        stops.forEach { stop ->
            try {
                stop()
            } catch (cause: Throwable) {
                failure = combineFailures(failure, cause)
            }
        }
        failure?.let { throw it }
    }

    // 接收任一输入源的事件；停止、没有输入源或当前所有输入源耗尽时返回 null。
    suspend fun receive(): InputEvent? = coroutineScope {
        val current = synchronized(lock) { session } ?: return@coroutineScope null
        val changes = current.inputs.produceIn(this)
        val exhausted = mutableSetOf<Input>()
        try {
            var event: InputEvent? = null
            while (event == null) {
                val inputs = current.inputs.value.filterNot { it in exhausted }
                if (inputs.isEmpty()) break

                selectUnbiased<Unit> {
                    changes.onReceive { /* 重新读取动态注册列表。 */ }
                    inputs.forEach { input ->
                        input.events.onReceiveCatching { result ->
                            if (input in current.inputs.value) {
                                result.exceptionOrNull()?.let { throw it }
                                if (result.isClosed) {
                                    exhausted.add(input)
                                } else {
                                    event = result.getOrThrow()
                                }
                            }
                        }
                    }
                }
            }
            event
        } finally {
            changes.cancel()
        }
    }

    // 广播到当前输出源；单个输出失败不影响其他输出，协程取消会立即向上传播。
    suspend fun emit(event: OutputEvent) {
        val (current, outputs) = synchronized(lock) {
            checkNotNull(session) { "IOManager is not started" } to registeredOutputs.toList()
        }
        var failure: Throwable? = null
        outputs.forEach { provider ->
            val active = synchronized(lock) {
                session === current && registeredOutputs.any { it === provider }
            }
            if (active) {
                try {
                    provider.emit(event)
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    failure = combineFailures(failure, cause)
                }
            }
        }
        failure?.let { throw it }
    }

    private fun startProvider(start: () -> Unit, stop: () -> Unit) {
        try {
            start()
        } catch (cause: Throwable) {
            stopAfterStartFailure(stop, cause)
            throw cause
        }
    }

    private fun stopAfterStartFailure(stop: () -> Unit, cause: Throwable) {
        try {
            stop()
        } catch (cleanupFailure: Throwable) {
            combineFailures(cause, cleanupFailure)
        }
    }

    private fun combineFailures(first: Throwable?, next: Throwable): Throwable =
        first?.also { if (it !== next) it.addSuppressed(next) } ?: next

}
