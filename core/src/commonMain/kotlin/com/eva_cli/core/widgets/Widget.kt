package com.eva_cli.core.widgets

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import kotlin.uuid.Uuid

interface Widget {
    val id: Uuid
    val name: String
    val desc: String
    val topic: String
    var enable: Boolean

    /**
     * 当前组件的记录，按追加顺序排列；调用方保证 actor 指向当前组件。
     * [List] 只读接口不保证深度不可变，应避免修改底层集合，并发更新需同步。
     */
    var records: List<EventRecord>

    /** 输出事件到所属或上级 Dispatcher；尚未绑定或根节点没有上级时为 null。 */
    var emit: (suspend (event: Event) -> Unit)?

    suspend fun start()

    suspend fun stop()

    suspend fun receive(event: Event)

    suspend fun backup()

    suspend fun restore()
}
