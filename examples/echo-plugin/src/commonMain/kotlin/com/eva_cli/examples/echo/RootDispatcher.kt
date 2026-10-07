package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.dispatcher.WidgetDispatcher
import com.eva_cli.core.widgets.event.Event

/**
 * 示例插件提供的根调度器。宿主通过 topic="/" 发现并启动它，
 * 因此根调度器不再属于宿主内置实现。
 */
class RootDispatcher : WidgetDispatcher() {
    override val name = "Root Dispatcher"
    override val desc = "Root dispatcher supplied by the Echo plugin"
    override val topic = "/"

    override suspend fun dispatch(event: Event) = Unit

    override suspend fun receive(event: Event) = Unit
}
