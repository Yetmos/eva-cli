package com.eva_cli.core.log

// core 的业务打点入口，保留稳定的事件类型和名称。
object LogUtil {

    fun log(data: LogData, throwable: Throwable? = null) {
        LogManager.log(data, throwable)
    }

    fun logW(
        eventType: EventType,
        name: String,
        desc: String? = null,
        throwable: Throwable? = null,
    ) {
        LogManager.log(LogLevel.WARN, eventType, name, desc, throwable)
    }

    fun logW(
        eventType: EventType,
        name: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        LogManager.log(LogLevel.WARN, eventType, name, throwable, message)
    }

    fun logD(
        eventType: EventType,
        name: String,
        desc: String? = null,
        throwable: Throwable? = null,
    ) {
        LogManager.log(LogLevel.DEBUG, eventType, name, desc, throwable)
    }

    fun logD(
        eventType: EventType,
        name: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        LogManager.log(LogLevel.DEBUG, eventType, name, throwable, message)
    }

    fun logI(
        eventType: EventType,
        name: String,
        desc: String? = null,
        throwable: Throwable? = null,
    ) {
        LogManager.log(LogLevel.INFO, eventType, name, desc, throwable)
    }

    fun logI(
        eventType: EventType,
        name: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        LogManager.log(LogLevel.INFO, eventType, name, throwable, message)
    }

    fun logE(
        eventType: EventType,
        name: String,
        desc: String? = null,
        throwable: Throwable? = null,
    ) {
        LogManager.log(LogLevel.ERROR, eventType, name, desc, throwable)
    }

    fun logE(
        eventType: EventType,
        name: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        LogManager.log(LogLevel.ERROR, eventType, name, throwable, message)
    }
}
