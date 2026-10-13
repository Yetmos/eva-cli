package com.eva_cli.examples.root

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event
import kotlin.uuid.Uuid

/** The system root for the plugin tree. */
class RootDispatcher : PluginDispatcher() {
    override val id: Uuid = Uuid.parse("11111111-1111-4111-8111-111111111111")
    override val name = "Root Dispatcher"
    override val desc = "Routes events to child dispatchers"
    override val topic = "/"

    override suspend fun dispatch(event: Event) {
        if (!enable) return

        dispatchToChildren(event)
    }

    override suspend fun receive(event: Event) {
        if (!enable) return
        if (!dispatchToChildren(event)) {
            checkNotNull(emit) { "Bind RootDispatcher.emit before receiving events" }(event)
        }
    }

    private suspend fun dispatchToChildren(event: Event): Boolean {
        val reportingProviderId = event.metadata.providerId
        val targets = dispatchers.values.filterNot { dispatcher ->
            (reportingProviderId == null && dispatcher.topic == REPORTING_TOPIC) ||
                reportingProviderId?.let { providerId ->
                    dispatcher.providers.values.any { provider -> provider.id == providerId }
                } == true
        }
        targets.forEach { dispatcher ->
            dispatcher.dispatch(event)
        }
        return targets.isNotEmpty()
    }

    private companion object {
        /** The command dispatcher is the host's default event reporting endpoint. */
        const val REPORTING_TOPIC = "/CMD"
    }
}
