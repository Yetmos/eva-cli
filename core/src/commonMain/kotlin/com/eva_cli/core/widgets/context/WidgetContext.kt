package com.eva_cli.core.widgets.context

import kotlinx.coroutines.CoroutineScope

/** Host-created capabilities bound to a widget before it starts. */
interface WidgetContext {
    val pluginId: String
    val scope: CoroutineScope
    val network: PluginNetwork
    val preferences: PluginPreferences
    val databases: PluginDatabases
    val files: PluginFiles
    val permissions: PluginPermissions
}
