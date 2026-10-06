package com.eva_cli.core.widgets.provider

import com.eva_cli.core.widgets.event.InEvent
import com.eva_cli.core.widgets.event.OutEvent

abstract class IOProvider : WidgetProvider()

abstract class InProvider : IOProvider() {
	open suspend fun receive(event: InEvent) {
		if (!enable) return
		event.metadata.providerId = id
	}
}

abstract class OutProvider : IOProvider() {
	abstract suspend fun emit(event: OutEvent)
}

