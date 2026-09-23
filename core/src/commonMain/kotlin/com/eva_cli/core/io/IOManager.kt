package com.eva_cli.core.io

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.selectUnbiased
import kotlin.time.Clock

/**
 * 全局输入、输出适配器注册表，负责所有 Provider 的生命周期。
 *
 * 输入并发合并，输出按注册顺序广播。运行中添加已启用的 Provider 会立即启动，移除时会停止已启动的 Provider。
 * Provider 的 enable 状态会在下一次 receive() 或 emit() 调用前同步。
 * 输入接收后及每次输出投递前，在事件副本上填写对应的 providerId 和 time。
 * 同一个实例只注册一次；停止管理器会保留注册列表，供下次启动使用。
 * Provider 的 stop 实现应能安全处理 start 部分完成或失败的情况。
 */
object IOManager {
    fun start() {

    }

    fun stop() {

    }

}
