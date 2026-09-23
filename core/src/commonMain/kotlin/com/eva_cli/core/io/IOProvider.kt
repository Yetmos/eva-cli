package com.eva_cli.core.io

// Provider的状态，由各平台(Android/iOS/mac/windows/linux)处理
enum class IOStatus {
	STOP,
	RUN
}
interface IOProvider {
	val id: String
	var status: IOStatus
	val name: String
	val desc: String
	// 运行中控制 是否处理InProvider接收的 InputEvent，是否给 OutProvider 分发 OutputEvent
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
