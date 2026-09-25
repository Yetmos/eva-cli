package com.eva_cli.core.provider

import kotlin.uuid.Uuid

interface Provider {
    val id: Uuid
    val name: String
    val desc: String
    var enable: Boolean
    var status: Status
    enum class Status {
        STOP,
        STARTING,
        IDLE,
        WORKING,
    }
}