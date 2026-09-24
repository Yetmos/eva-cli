package com.eva_cli.core.log

internal actual fun logPlatform(level: LogLevel, title: String, desc: String) {
    println("[${level.name}] $title: $desc")
}
