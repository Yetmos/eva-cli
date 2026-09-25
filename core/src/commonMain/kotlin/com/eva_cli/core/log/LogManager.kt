package com.eva_cli.core.log

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit

// 统一管理 core 的日志配置和 Kermit 输出。
object LogManager {

    private const val TAG_PREFIX = "EvaCore"

    private val logger = Logger(
        config = loggerConfigInit(
            coreLogWriter(),
            minSeverity = Severity.Debug,
        ),
        tag = TAG_PREFIX,
    )

    fun log(data: LogData, throwable: Throwable? = null) {
        log(data.level, data.eventType, data.name, throwable) {
            data.desc.orEmpty()
        }
    }

    internal fun log(
        level: LogLevel,
        eventType: EventType,
        name: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        val tag = "$TAG_PREFIX.${eventType.name}"
        val formattedMessage = { formatMessage(name, message()) }
        when (level) {
            LogLevel.DEBUG -> logger.d(throwable = throwable, tag = tag, message = formattedMessage)
            LogLevel.INFO -> logger.i(throwable = throwable, tag = tag, message = formattedMessage)
            LogLevel.WARN -> logger.w(throwable = throwable, tag = tag, message = formattedMessage)
            LogLevel.ERROR -> logger.e(throwable = throwable, tag = tag, message = formattedMessage)
        }
    }

    internal fun log(
        level: LogLevel,
        eventType: EventType,
        name: String,
        desc: String?,
        throwable: Throwable? = null,
    ) {
        log(level, eventType, name, throwable) {
            desc.orEmpty()
        }
    }

    private fun formatMessage(name: String, desc: String): String = buildString {
        append(name)
        if (desc.isNotEmpty()) {
            append(": ")
            append(desc)
        }
    }
}

internal expect fun coreLogWriter(): LogWriter
