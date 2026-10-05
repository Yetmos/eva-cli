package com.eva_cli.core.widget.dispatcher

import com.eva_cli.core.widget.impl.dispatchers.RootDispatcher

// INFO: 该组件只负责启动 RootDispatcher, RootDispatcher负责启动Dispatcher树，不具备任何事件调度能力
object Booter {
    lateinit var root: RootDispatcher

    suspend fun start() {
	    root = RootDispatcher()
	    root.start()
    }

	suspend fun stop() {
		root.stop()
	}

	// INFO: 备份整个决策树
    suspend fun backup() {
		root.backupTree()
    }

	// INFO: 恢复整个决策树
    suspend fun restore() {
		root.restore()
    }

}