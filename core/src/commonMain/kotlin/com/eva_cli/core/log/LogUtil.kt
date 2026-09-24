package com.eva_cli.core.log

// INFO: 日志工具库
object LogUtil {

	fun init() {

	}

	fun logW(eventType: EventType, title: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.WARN, eventType = eventType, title = title, desc = desc))
	}

	fun logD(eventType: EventType, title: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.DEBUG, eventType = eventType, title = title, desc = desc))
	}

	fun logI(eventType: EventType, title: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.INFO, eventType = eventType, title = title, desc = desc))
	}

	fun logE(eventType: EventType, title: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.ERROR, eventType = eventType, title = title, desc = desc))
	}

}

internal expect fun logPlatform(data: LogData)
