package com.eva_cli.core.log

import kotlin.uuid.Uuid

sealed interface LogEvent

// INFO: 鏃ュ織鍒嗙骇鏋氫妇
enum class LogLevel {
	WARN,
	DEBUG,
	INFO,
	ERROR,
}

data class LogData(val id: Uuid = Uuid.random(), val level: LogLevel, val title: String, val desc: String? = null)

// INFO: 鏀綢O鐩稿叧鐨勬棩蹇椾簨浠?
enum class IOLogEvent : LogEvent {

}
