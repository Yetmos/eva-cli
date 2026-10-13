package com.eva_cli.core.runtime

import com.eva_cli.core.widgets.dispatcher.Dispatcher
import com.eva_cli.core.widgets.plug.PlugManager

// INFO: 该组件只负责启动 RootDispatcher, RootDispatcher负责启动Dispatcher树，不具备任何事件调度能力
object Booter {
    private var root: Dispatcher? = null

    /** Whether a root dispatcher is currently retained by the runtime. */
    internal val isRunning: Boolean
        get() = root != null

    /**
     * 启动已加载插件中唯一 topic=/ 的根调度器，由其负责启动整棵树。
     * 生命周期操作须串行调用；启动失败时保留根引用，须先 stop 清理再重试。
     */
    suspend fun start() {
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

    suspend fun stop() {
        val dispatcher = root ?: return
        try {
            dispatcher.stop()
        } finally {
            root = null
        }
    }

    /** 将宿主输入提交到已启动的根 Dispatcher。 */
    suspend fun dispatch(event: com.eva_cli.core.widgets.event.Event) {
        checkNotNull(root) { "RootDispatcher 尚未启动" }.dispatch(event)
    }

    // INFO: 备份整个决策树
    suspend fun backup() {
        checkNotNull(root) { "RootDispatcher 尚未启动" }.backupTree()
    }

    // INFO: 恢复根调度器
    suspend fun restore() {
        checkNotNull(root) { "RootDispatcher 尚未启动" }.restore()
    }

}
