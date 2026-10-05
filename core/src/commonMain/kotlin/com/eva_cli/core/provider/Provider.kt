package com.eva_cli.core.provider

import com.eva_cli.core.context.Context
import kotlin.uuid.Uuid

interface Provider {
    val id: Uuid
    val name: String
    val desc: String
    val topic: String
    var enable: Boolean
    var status: Status
    enum class Status {
        STOP,
        STARTING,
        IDLE,
        WORKING,
    }

    /** 当前 Provider 对不同事件的处理记录，需要持久化。 */
    var context: Context

    suspend fun receive(event: ProviderEvent)

    var emit: (suspend (event: ProviderEvent) -> Unit)?


    fun start() = Unit

    fun stop() = Unit

    suspend fun backup()

    suspend fun restore()

}
