package com.eva_cli.examples.root

import com.eva_cli.core.widgets.plug.PluginEntry

/** System entry point that supplies the single root dispatcher. */
class RootPlugin : PluginEntry {
    override val dispatchers = listOf(RootDispatcher())
}
