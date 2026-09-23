package com.eva_cli.core.io

/**
 * 与平台无关的输入事件来源。
 *
 * 平台适配器应先将原生输入转换为 [InputEvent]，再交给核心运行时处理。
 */
interface InputProvider {
    val id: String
    // Provider 的展示名称。
    val name: String
    // Provider 的功能描述。
    val desc: String
    // Provider 是否启用；状态会在下一次 IOManager.receive() 调用前同步。
    var enable: Boolean

    fun start() = Unit
    fun stop() = Unit

    // 等待下一条输入，耗尽时返回 null；挂起等待应响应协程取消，以便动态移除。
    suspend fun receive(): InputEvent?
}
