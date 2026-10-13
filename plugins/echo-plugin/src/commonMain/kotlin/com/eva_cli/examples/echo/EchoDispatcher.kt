package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event
import kotlin.uuid.Uuid

/** Routes events on /Echo to the plugin's single Echo provider. */
class EchoDispatcher : PluginDispatcher() {
    override val id: Uuid = Uuid.parse("22222222-2222-4222-8222-222222222222")
    override val name = "Echo Dispatcher"
    override val desc = "Routes text events to the Echo provider"
    override val topic = "/Echo"

    override suspend fun dispatch(event: Event) {
        if (!enable) return

        check(providers.size == 1) {
            "topic=$topic 必须且只能有一个 Provider，实际找到 ${providers.size} 个"
        }
        val provider = providers.values.single()
        provider.receive(event)
    }

    override suspend fun receive(event: Event) {
        if (!enable) return
        checkNotNull(emit) { "Bind EchoDispatcher.emit before forwarding events" }(event)
    }
}
