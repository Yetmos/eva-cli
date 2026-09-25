package com.eva_cli.core.log

import platform.Foundation.NSLog

internal actual fun logPlatform(data: LogData) {
    NSLog("[${data.level.name}][${data.eventType.name}] ${data.name}: ${data.desc.orEmpty()}")
}
