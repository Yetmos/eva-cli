package com.eva_cli.core.widget.provider

import com.eva_cli.core.widget.event.Event
import com.eva_cli.core.widget.event.EventRecord
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

    /**
     * 当前 Provider 的处理记录，按追加顺序排列；调用方保证 actor 指向当前组件。
     * [List] 只读接口不保证深度不可变，应避免修改底层集合，并发更新需同步。
     */
    var records: List<EventRecord>

    suspend fun receive(event: Event)

    var emit: (suspend (event: Event) -> Unit)?

    fun start() = Unit

    fun stop() = Unit

    suspend fun backup()

    suspend fun restore()

}
