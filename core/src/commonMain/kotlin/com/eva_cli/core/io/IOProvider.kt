package com.eva_cli.core.io

// Provider的状态，由各平台(Android/iOS/mac/windows/linux)处理
enum class IOStatus {
	STARTING,
	RUN,
	PAUSE,
	STOP,
}
interface IOProvider {
	val id: String
	var status: IOStatus
	val name: String
	val desc: String
	/**
	 * 实时控制输入处理和输出投递，不控制 start/stop。
	 * 禁用时仍接收输入，但丢弃事件；重新启用后处理后续收到的事件。
	 * 已开始的输入处理和输出投递不受后续开关变更影响。
	 * 实现必须保证跨线程读写可见性，例如使用原子变量或平台的 volatile 属性。
	 */
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
internal expect fun initIOProviders()
