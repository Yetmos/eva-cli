package com.eva_cli.core.runtime

import com.eva_cli.core.io.IOManager

/** 绑定一个已配置 IO 管理器的运行时实例。 */
class Runtime(
    val ioManager: IOManager,
) {
    private var started = false

    val isStarted: Boolean
        get() = started

    fun start() {
        if (started) return

        ioManager.start()
        started = true
    }

    fun stop() {
        if (!started) return

        try {
            ioManager.stop()
        } finally {
            started = false
        }
    }
}
