package com.eva_cli.core.provider.io

import com.eva_cli.core.provider.Provider
import kotlin.uuid.Uuid

interface IOProvider : Provider{
	fun start() = Unit
	fun stop() = Unit
}

interface InProvider : IOProvider {
	suspend fun receive(event: InEvent) {
		if (!enable) return
		event.metadata.providerId = id
		IOManager.receive(event)
	}
}

interface OutProvider : IOProvider {
	suspend fun emit(event: OutEvent)
}

// 通过 IOManager 注册平台内置 Provider；初始化失败后可能重试，应复用已注册的实例。
internal expect fun initIOProviders()
