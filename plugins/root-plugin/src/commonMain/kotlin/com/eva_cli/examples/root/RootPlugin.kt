package com.eva_cli.examples.root

import com.eva_cli.core.widgets.plug.PluginEntry

/** 提供唯一根 Dispatcher 的系统入口。 */
class RootPlugin : PluginEntry {
    override val dispatchers = listOf(RootDispatcher())
}
