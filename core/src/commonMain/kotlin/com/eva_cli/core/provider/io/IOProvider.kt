package com.eva_cli.core.provider.io

import com.eva_cli.core.provider.Provider
import kotlin.uuid.Uuid

interface IOProvider : Provider{

}

interface InProvider : IOProvider {
	suspend fun receive(event: InEvent) {
		if (!enable) return
		event.metadata.providerId = id
	}
}

interface OutProvider : IOProvider {
	suspend fun emit(event: OutEvent)
}


