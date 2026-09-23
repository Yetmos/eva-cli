package com.eva_cli.core.io


interface IOProvider {
	val id: String
	val name: String
	val desc: String
	var enable: Boolean

	fun start() = Unit
	fun stop() = Unit
}

/**
 * 与平台无关的输入事件来源。
 *
 * 平台适配器应先将原生输入转换为 [InputEvent]，再交给核心运行时处理。
 */
interface InProvider : IOProvider {
	// 等待下一条输入，耗尽时返回 null；挂起等待应响应协程取消，以便动态移除。
	suspend fun receive(): InputEvent?
}

/**
 * 与平台无关的输出事件接收端。
 *
 * 平台适配器负责决定如何呈现或传递 [OutputEvent]。
 */
interface OutProvider : IOProvider {
	suspend fun emit(event: OutputEvent)
}
