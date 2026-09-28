package com.eva_cli.core.dispatcher

// INFO: 该组件只负责启动 RootDispatcher, RootDispatcher负责启动Dispatcher树，不具备任何事件调度能力
object Booter {
    fun start() {
        val root = findRootDispatcher()
        root.start()
    }

    fun findRootDispatcher(): Dispatcher {
        // TODO: 查找RootDispatcher
        return TODO("Provide the return value")
    }

}