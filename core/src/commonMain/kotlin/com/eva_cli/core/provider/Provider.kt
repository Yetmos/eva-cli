package com.eva_cli.core.provider

import kotlin.uuid.Uuid

interface Provider {
    val id: Uuid
    val name: String
    val desc: String
    val topic: String
    var enable: Boolean
    // 最大运行的实例数
    var max: Int
    var status: Status
    enum class Status {
        STOP,
        STARTING,
        IDLE,
        WORKING,
    }

    suspend fun receive(event: ProviderEvent)

    var emit: (suspend (event: ProviderEvent) -> Unit)?


    fun start() = Unit

    fun stop() = Unit

    suspend fun backup()

    suspend fun restore()

}