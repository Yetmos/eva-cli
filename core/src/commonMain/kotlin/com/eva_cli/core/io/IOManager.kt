package com.eva_cli.core.io

import kotlinx.atomicfu.atomic

/**
 * 全局输入、输出管理，负责所有 Provider 的生命周期。
 */
object IOManager {
    private val started = atomic(false)

    // 首次启动时初始化；成功后停止、重启均复用已有注册结果。
    private val providersInitialized: Unit by lazy { initProviders() }

    /** 管理器自身的启停状态；Provider 的生命周期管理尚未实现。 */
    val isStarted: Boolean
        get() = started.value

    fun start() {
        providersInitialized
        started.value = true
    }

    fun stop() {
        started.value = false
    }
}
