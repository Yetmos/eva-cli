package com.eva_cli.core.widgets.provider

import com.eva_cli.core.widgets.BaseWidget
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.WidgetRecordsPersistence

abstract class BaseProvider: BaseWidget(), Provider {
    override suspend fun backup() {
        WidgetRecordsPersistence.write(context, ComponentRef.Kind.PROVIDER, id, records)
    }

    override suspend fun restore() {
        records = WidgetRecordsPersistence.read(context, ComponentRef.Kind.PROVIDER, id)
    }
}
