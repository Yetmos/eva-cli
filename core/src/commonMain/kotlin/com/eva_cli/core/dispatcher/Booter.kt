package com.eva_cli.core.dispatcher

// INFO: 该组件只负责启动 RootDispatcher, RootDispatcher负责启动Dispatcher树，不具备任何事件调度能力
object Booter {
    lateinit var root: RootDispatcher

    suspend fun start() {
	    root = RootDispatcher()
	    root.start()
    }

    suspend fun backup() {

    }

    suspend fun restore() {

    }

}