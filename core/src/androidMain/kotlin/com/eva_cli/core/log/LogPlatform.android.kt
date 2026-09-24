package com.eva_cli.core.log

import android.util.Log

internal actual fun logPlatform(data: LogData) {
	val message = "${data.title}: ${data.desc.orEmpty()}"
	when (data.level) {
        LogLevel.ERROR -> Log.e(LOG_TAG, message)
        LogLevel.WARN -> Log.w(LOG_TAG, message)
        LogLevel.INFO -> Log.i(LOG_TAG, message)
        LogLevel.DEBUG -> Log.d(LOG_TAG, message)
    }
}

private const val LOG_TAG = "EvaCore"
