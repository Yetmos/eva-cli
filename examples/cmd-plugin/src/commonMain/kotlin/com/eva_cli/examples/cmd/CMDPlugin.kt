package com.eva_cli.examples.cmd

import com.eva_cli.core.widgets.plug.PluginEntry
import com.eva_cli.core.widgets.provider.PluginProvider

class CMDPlugin : PluginEntry {
    override val providers: List<PluginProvider> = listOf(CMDProvider())
    override val dispatchers = listOf(CMDDispatcher())
}
