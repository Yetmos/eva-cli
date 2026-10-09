package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event

/**
 * 示例插件提供的根调度器。宿主通过 topic="/" 发现并启动它，
 * 因此根调度器不再属于宿主内置实现。
 */
class RootDispatcher : PluginDispatcher() {
    override val name = "Root Dispatcher"
    override val desc = "Root dispatcher supplied by the Echo plugin"
    override val topic = "/"

    override suspend fun dispatch(event: Event) {
        if (!enable) return

        val echo = dispatchers.values.filter { it.topic == "/Echo" }
        check(echo.size == 1) {
            "topic=/ 必须且只能有一个 /Echo Dispatcher，实际找到 ${echo.size} 个"
        }
        echo.single().dispatch(event)
    }

    override suspend fun receive(event: Event) {
        if (!enable) return
        checkNotNull(emit) { "Bind RootDispatcher.emit before receiving events" }(event)
    }
}
