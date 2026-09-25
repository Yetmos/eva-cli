package com.eva_cli.core.log

// INFO: 日志工具库
object LogUtil {

	fun init() {

	}

	fun log(data: LogData) {
		LogManager.log(data)
	}

	fun logW(eventType: EventType, name: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.WARN, eventType = eventType, name = name, desc = desc))
	}

	fun logD(eventType: EventType, name: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.DEBUG, eventType = eventType, name = name, desc = desc))
	}

	fun logI(eventType: EventType, name: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.INFO, eventType = eventType, name = name, desc = desc))
	}

	fun logE(eventType: EventType, name: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.ERROR, eventType = eventType, name = name, desc = desc))
	}

}

internal expect fun logPlatform(data: LogData)
