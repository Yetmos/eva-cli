package com.eva_cli.core.runtime

// 使用全局 IO 管理器；所有 Runtime 实例共享其启停状态。
object Runtime {

    suspend fun start() {
        Configure.start()
        Plugin.start()
        Booter.start()
    }

    suspend fun stop() {
        Booter.stop()
    }

}
