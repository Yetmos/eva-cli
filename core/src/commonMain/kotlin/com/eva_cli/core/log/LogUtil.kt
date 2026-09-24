package com.eva_cli.core.log

import kotlin.uuid.Uuid

// INFO: 日志分级枚举
enum class LogLevel {
	WARN,
	DEBUG,
	INFO,
	ERROR,
}

data class LogData(val id: Uuid = Uuid.random(), val title: String, val desc: String)

// INFO: 日志工具库
object LogUtil {

	fun init() {

	}

	fun log(level: LogLevel, title: String, desc: String) {
		// 打印到对应平台的日志输出
		logPlatform(level, title, desc)
		LogManager.log(LogData(title = title, desc = desc))
	}

}

internal expect fun logPlatform(level: LogLevel, title: String, desc: String)
