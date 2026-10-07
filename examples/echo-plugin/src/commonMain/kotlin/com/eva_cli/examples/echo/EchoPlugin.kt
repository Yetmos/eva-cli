package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.plug.PluginEntry
import com.eva_cli.core.widgets.provider.PluginProvider

/** 公开无参入口，由宿主按照 plugin.json 中的 entryClass 创建。 */
class EchoPlugin : PluginEntry {
    override val providers: List<PluginProvider> = listOf(EchoProvider())
    override val dispatchers = listOf(RootDispatcher())
}
