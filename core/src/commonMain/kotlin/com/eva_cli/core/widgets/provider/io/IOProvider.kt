package com.eva_cli.core.widgets.provider.io

import com.eva_cli.core.widgets.provider.WidgetProvider

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

