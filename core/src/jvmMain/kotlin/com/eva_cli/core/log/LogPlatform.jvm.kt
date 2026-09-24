package com.eva_cli.core.log

internal actual fun logPlatform(data: LogData) {
    println("[${data.level.name}][${data.eventType.name}] ${data.title}: ${data.desc.orEmpty()}")
}
