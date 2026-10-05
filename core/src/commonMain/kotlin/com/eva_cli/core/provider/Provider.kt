package com.eva_cli.core.provider

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

    // INFO: 当前组件的所有任务执行记录，需要持久化
    var context: Context

    /** 当前 Provider 对不同事件的处理记录，与事件履历共用同一条 [EventRecord]。 */
    data class Context(val items: List<EventRecord> = emptyList())

    suspend fun receive(event: ProviderEvent)

    var emit: (suspend (event: ProviderEvent) -> Unit)?


    fun start() = Unit

    fun stop() = Unit

    suspend fun backup()

    suspend fun restore()

}
