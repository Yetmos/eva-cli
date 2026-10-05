package com.eva_cli.core.widget.provider.io

import com.eva_cli.core.widget.provider.Provider

interface IOProvider : Provider

interface InProvider : IOProvider {
	suspend fun receive(event: InEvent) {
		if (!enable) return
		event.metadata.providerId = id
	}
}

interface OutProvider : IOProvider {
	suspend fun emit(event: OutEvent)
}


