package com.eva_cli.core.widgets.provider

import com.eva_cli.core.widgets.BaseWidget
import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.event.RecordsStore
import com.eva_cli.core.widgets.event.Result

abstract class BaseProvider: BaseWidget(), Provider {
    override val componentKind: ComponentRef.Kind = ComponentRef.Kind.PROVIDER

    protected fun record(event: Event, result: Result, detail: String? = null): EventRecord =
        record(event, Action.Handle(result, detail))

    override suspend fun backup() {
        RecordsStore.write(context, ComponentRef.Kind.PROVIDER, id, records)
    }

    override suspend fun restore() {
        records = RecordsStore.read(context, ComponentRef.Kind.PROVIDER, id)
    }
}
