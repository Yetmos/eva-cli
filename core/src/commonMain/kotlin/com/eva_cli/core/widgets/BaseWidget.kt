package com.eva_cli.core.widgets

import com.eva_cli.core.widgets.context.WidgetContext
import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.event.EventStore

/** 保存宿主绑定；组件构造期间不能访问 context，也不能切换到其他插件的 context。 */
abstract class BaseWidget : Widget {
    private var boundContext: WidgetContext? = null
    protected abstract val componentKind: ComponentRef.Kind

    final override val context: WidgetContext
        get() = checkNotNull(boundContext) { "Widget 尚未绑定宿主 Context" }

    final override fun attach(context: WidgetContext) {
        check(boundContext == null || boundContext === context) { "Widget 已绑定其他 Context" }
        boundContext = context
    }

    /** 业务确定动作后追加同一条记录；调用方须串行更新同一事件与组件。 */
    protected fun record(event: Event, action: Action): EventRecord {
        val record = EventRecord(
            eventId = event.id,
            actor = ComponentRef(id, componentKind),
            action = action,
        )
        event.records = event.records + record
        records = records + record
        return record
    }

    override suspend fun update(event: Event) {
        val actor = ComponentRef(id, componentKind)
        val knownIds = records.mapTo(mutableSetOf()) { it.id }
        val additions = event.records.filter {
            it.eventId == event.id && it.actor == actor && knownIds.add(it.id)
        }
        if (additions.isNotEmpty()) records = records + additions
        EventStore.update(event)
    }
}
