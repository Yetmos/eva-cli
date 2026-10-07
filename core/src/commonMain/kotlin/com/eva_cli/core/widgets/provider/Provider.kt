package com.eva_cli.core.widgets.provider

import com.eva_cli.core.widgets.Widget

interface Provider : Widget {
    var status: Status
    enum class Status {
        STOP,
        STARTING,
        IDLE,
        WORKING,
    }

    override suspend fun start() = Unit

    override suspend fun stop() = Unit
}
