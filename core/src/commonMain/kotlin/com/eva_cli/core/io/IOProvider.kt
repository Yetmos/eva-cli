package com.eva_cli.core.io

interface IOProvider {
	val id: String
	val name: String
	val desc: String
	var enable: Boolean

	fun start() = Unit
	fun stop() = Unit
}

interface InProvider : IOProvider {
	suspend fun receive(): InputEvent?
}

interface OutProvider : IOProvider {
	suspend fun emit(event: OutputEvent)
}

// 公共层持有的输入、输出 Provider 列表，停止后保留注册结果。
internal val inProviders: MutableList<InProvider> = mutableListOf()
internal val outProviders: MutableList<OutProvider> = mutableListOf()

// 注册平台内置 Provider；初始化失败后可能重试，实现应避免重复注册。
internal expect fun initProviders()
