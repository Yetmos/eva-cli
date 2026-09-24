package com.eva_cli.core.log

import android.util.Log

internal actual fun logPlatform(level: LogLevel, title: String, desc: String) {
    val message = "$title: $desc"
    when (level) {
        LogLevel.ERROR -> Log.e(LOG_TAG, message)
        LogLevel.WARN -> Log.w(LOG_TAG, message)
        LogLevel.INFO -> Log.i(LOG_TAG, message)
        LogLevel.DEBUG -> Log.d(LOG_TAG, message)
    }
}

private const val LOG_TAG = "EvaCore"
