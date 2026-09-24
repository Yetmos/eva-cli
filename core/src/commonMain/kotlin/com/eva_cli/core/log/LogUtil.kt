package com.eva_cli.core.log

// INFO: 日志工具库
object LogUtil {

	fun init() {

	}

	fun logW(title: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.WARN, title = title, desc = desc))
	}

	fun logD(title: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.DEBUG, title = title, desc = desc))
	}

	fun logI(title: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.INFO, title = title, desc = desc))
	}

	fun logE(title: String, desc: String? = null) {
		LogManager.log(LogData(level = LogLevel.ERROR, title = title, desc = desc))
	}

}

internal expect fun logPlatform(data: LogData)
