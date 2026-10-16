package com.eva_cli.core.runtime

import com.eva_cli.core.widgets.dispatcher.Dispatcher
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.plug.PlugManager
import kotlin.concurrent.Volatile

/** 启动并管理从已加载插件中选出的根调度器。 */
object Booter {
    @Volatile
    private var root: Dispatcher? = null

    /** 当前运行时是否持有根调度器。 */
    internal val isRunning: Boolean
        get() = root != null

    /** 在运行时生命周期操作串行化的前提下启动根调度器。 */
    suspend fun start() = RuntimeGate.withLock { startLocked() }

    /** 在调用方已经持有 [RuntimeGate] 时启动根调度器。 */
    internal suspend fun startLocked() {
        check(root == null) { "RootDispatcher 已启动或尚未清理，请先停止" }
        val roots = PlugManager.loadedList.flatMap { plugin ->
            plugin.dispatchers.filter { it.topic == "/" }
        }
        check(roots.size == 1) {
            "已加载插件中必须有且仅有一个 RootDispatcher（topic=/），实际找到 ${roots.size} 个"
        }
        val dispatcher = roots.single()
        root = dispatcher
        dispatcher.start()
    }

    /** 在运行时生命周期操作串行化的前提下停止根调度器。 */
    suspend fun stop() = RuntimeGate.withLock { stopLocked() }

    /** 在调用方已经持有 [RuntimeGate] 时停止根调度器。 */
    internal suspend fun stopLocked() {
        val dispatcher = root ?: return
        try {
            dispatcher.stop()
        } finally {
            root = null
        }
    }

    /** 将事件分发给已启动的根调度器。 */
    suspend fun dispatch(event: Event) = RuntimeGate.withLock { dispatchLocked(event) }

    /** 在调用方已经持有 [RuntimeGate] 时分发事件。 */
    internal suspend fun dispatchLocked(event: Event) {
        checkNotNull(root) { "RootDispatcher 尚未启动" }.dispatch(event)
    }

    /** 备份完整的调度器树。 */
    suspend fun backup() = RuntimeGate.withLock { backupLocked() }

    /** 在调用方已经持有 [RuntimeGate] 时备份调度器树。 */
    internal suspend fun backupLocked() {
        checkNotNull(root) { "RootDispatcher 尚未启动" }.backupTree()
    }

    /** 恢复完整的调度器树。 */
    suspend fun restore() = RuntimeGate.withLock { restoreLocked() }

    /** 在调用方已经持有 [RuntimeGate] 时恢复调度器树。 */
    internal suspend fun restoreLocked() {
        checkNotNull(root) { "RootDispatcher 尚未启动" }.restore()
    }
}
