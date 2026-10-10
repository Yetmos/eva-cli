package com.eva_cli.examples.root

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event

/**
 * The system root for the plugin tree.
 *
 * Events without an explicit destination use [DEFAULT_TARGET_TOPIC]. An explicit
 * destination must resolve to exactly one child dispatcher. This keeps input
 * routing deterministic when output dispatchers such as `/CMD` are loaded too.
 */
class RootDispatcher : PluginDispatcher() {
    override val name = "Root Dispatcher"
    override val desc = "Routes events to a plugin dispatcher"
    override val topic = "/"

    private var defaultTargetTopic = DEFAULT_TARGET_TOPIC

    override suspend fun start() {
        defaultTargetTopic = context.preferences.get(DEFAULT_TARGET_PREFERENCE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_TARGET_TOPIC
        super.start()
    }

    override suspend fun dispatch(event: Event) {
        if (!enable) return

        val targetTopic = event.metadata.targetTopic ?: defaultTargetTopic
        val targets = dispatchers.values.filter { it.topic == targetTopic }
        check(targets.size == 1) {
            "RootDispatcher target topic=$targetTopic requires exactly one child Dispatcher; " +
                "found ${targets.size}"
        }
        targets.single().dispatch(event)
    }

    override suspend fun receive(event: Event) {
        if (!enable) return
        checkNotNull(emit) { "Bind RootDispatcher.emit before receiving events" }(event)
    }

    private companion object {
        const val DEFAULT_TARGET_TOPIC = "/Echo"
        const val DEFAULT_TARGET_PREFERENCE = "eva.root.defaultTopic"
    }
}
