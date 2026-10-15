package com.eva_cli.core.widgets.context

import kotlinx.coroutines.flow.Flow

/** String preferences persisted by the host for a plugin. */
interface PluginPreferences {
    val values: Flow<Map<String, String>>
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
}
