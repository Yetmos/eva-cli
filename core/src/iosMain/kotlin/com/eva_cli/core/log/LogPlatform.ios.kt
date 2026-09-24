package com.eva_cli.core.log

import platform.Foundation.NSLog

internal actual fun logPlatform(data: LogData) {
    NSLog("[${data.level.name}] ${data.title}: ${data.desc.orEmpty()}")
}
