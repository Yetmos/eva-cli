package com.eva_cli.core.runtime

import com.eva_cli.core.provider.io.IOManager

// 使用全局 IO 管理器；所有 Runtime 实例共享其启停状态。
object Runtime {

    fun start() {
        IOManager.start()
    }

    fun stop() {
        IOManager.stop()
    }
}
