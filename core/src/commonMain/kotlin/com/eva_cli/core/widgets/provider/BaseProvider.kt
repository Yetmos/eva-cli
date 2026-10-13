package com.eva_cli.core.widgets.provider

import com.eva_cli.core.widgets.BaseWidget
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.RecordsStore

abstract class BaseProvider: BaseWidget(), Provider {
    override suspend fun backup() {
        RecordsStore.write(context, ComponentRef.Kind.PROVIDER, id, records)
    }

    override suspend fun restore() {
        records = RecordsStore.read(context, ComponentRef.Kind.PROVIDER, id)
    }
}
