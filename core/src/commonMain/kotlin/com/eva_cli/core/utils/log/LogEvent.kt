package com.eva_cli.core.utils.log

import kotlin.uuid.Uuid

// INFO: 日志类型 CUSTOM - 自定义事件
enum class  EventType {
	CUSTOM,
	IO,
	CONTENT,
	RUNTIME,
}

// INFO: 日志级别
enum class LogLevel {
	WARN,
	DEBUG,
	INFO,
	ERROR,
}

data class LogData(val id: Uuid = Uuid.random(), val level: LogLevel, val eventType: EventType, val name: String, val desc: String? = null)
