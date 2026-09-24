package com.eva_cli.core.log

import platform.Foundation.NSLog

internal actual fun logPlatform(level: LogLevel, title: String, desc: String) {
    NSLog("[${level.name}] $title: $desc")
}
