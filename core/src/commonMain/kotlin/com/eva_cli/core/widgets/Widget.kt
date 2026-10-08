package com.eva_cli.core.widgets

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.context.WidgetContext
import kotlin.uuid.Uuid

interface Widget {
    val context: WidgetContext

    /** 宿主在首次生命周期调用前绑定，同一实例仅可绑定同一个 Context。 */
    fun attach(context: WidgetContext)

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

    var emit: (suspend (event: Event) -> Unit)?

    suspend fun start()

    suspend fun stop()

    suspend fun receive(event: Event)

    suspend fun backup()

    suspend fun restore()
}
