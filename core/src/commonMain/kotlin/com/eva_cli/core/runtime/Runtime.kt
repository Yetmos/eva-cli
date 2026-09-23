package com.eva_cli.core.runtime

import com.eva_cli.core.io.IOManager

// 使用全局 IO 管理器；所有 Runtime 实例共享其启停状态。
object Runtime {
    val ioManager: IOManager
        get() = IOManager

    val isStarted: Boolean
        get() = ioManager.isStarted

    fun start() {
        ioManager.start()
    }

    fun stop() {
        ioManager.stop()
    }
}
