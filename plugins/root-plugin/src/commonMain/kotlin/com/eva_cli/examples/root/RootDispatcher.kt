package com.eva_cli.examples.root

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.DispatchType
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.Result
import kotlin.uuid.Uuid

/** The system root for the plugin tree. */
class RootDispatcher : PluginDispatcher() {
    override val id: Uuid = Uuid.parse("11111111-1111-4111-8111-111111111111")
    override val name = "Root Dispatcher"
    override val desc = "Routes events to child dispatchers"
    override val topic = "/"

    private val activeDispatches = mutableMapOf<Uuid, MutableSet<Uuid>>()

    override suspend fun dispatch(event: Event) {
        if (!enable) return

        dispatchToChildren(event)
    }

    override suspend fun receive(event: Event) {
        if (!enable) return
        if (!dispatchToChildren(event)) {
            checkNotNull(emit) { "Bind RootDispatcher.emit before receiving events" }(event)
            update(event)
        }
    }

    private suspend fun dispatchToChildren(event: Event): Boolean {
        val active = activeDispatches[event.id]
        val visited = active ?: mutableSetOf<Uuid>().also { activeDispatches[event.id] = it }
        try {
            return dispatchToChildren(event, visited)
        } finally {
            if (active == null) activeDispatches.remove(event.id)
        }
    }

    private suspend fun dispatchToChildren(event: Event, visited: MutableSet<Uuid>): Boolean {
        val lastRecord = event.records.lastOrNull {
            it.eventId == event.id && it.actor.kind == ComponentRef.Kind.PROVIDER && it.action is Action.Handle
        }
        val result = (lastRecord?.action as? Action.Handle)?.result
        val reportingProviderId = if (
            lastRecord?.actor?.kind == ComponentRef.Kind.PROVIDER &&
            (result == Result.BACK || result == Result.DO_BACK)
        ) {
            lastRecord.actor.id
        } else {
            event.metadata.providerId
        }
        val targets = dispatchers.values.filterNot { dispatcher ->
            dispatcher.id in visited ||
                (reportingProviderId == null && dispatcher.topic == REPORTING_TOPIC) ||
                reportingProviderId?.let { providerId ->
                    dispatcher.providers.values.any { provider -> provider.id == providerId }
                } == true
        }
        var dispatched = false
        targets.forEach { dispatcher ->
            if (!visited.add(dispatcher.id)) return@forEach
            dispatched = true
            record(event, DispatchType.DISPATCH, dispatcher)
            try {
                dispatcher.dispatch(event)
            } catch (failure: Throwable) {
                runCatching { update(event) }.exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            }
            update(event)
        }
        return dispatched
    }

    private companion object {
        /** The command dispatcher is the host's default event reporting endpoint. */
        const val REPORTING_TOPIC = "/CMD"
    }
}
