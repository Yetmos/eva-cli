package com.eva_cli.core.io

/**
 * 与平台无关的输入事件来源。
 *
 * 平台适配器应先将原生输入转换为 [InputEvent]，再交给核心运行时处理。
 */
interface InputProvider {
    fun start() = Unit
    fun stop() = Unit
    suspend fun receive(): InputEvent?
}
