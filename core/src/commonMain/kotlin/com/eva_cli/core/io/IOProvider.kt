package com.eva_cli.core.io

import kotlin.uuid.Uuid

// Provider的状态，由各平台(Android/iOS/mac/windows/linux)处理
enum class IOStatus {
	STOP,
	STARTING,
	IDLE,
	WORKING,
}

interface IOProvider {
	// 实例的唯一标识，创建时确定且保持不变；需要跨重启复用时由实现持久化。
	val id: Uuid
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

// 通过 IOManager 注册平台内置 Provider；初始化失败后可能重试，应复用已注册的实例。
internal expect fun initIOProviders()
