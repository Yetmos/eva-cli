package com.eva_cli.core.widgets.context

enum class PluginFileArea { DATA, CACHE, RESOURCES }

/** Paths are relative to the selected plugin area; RESOURCES is read-only. */
interface PluginFiles {
    suspend fun read(path: String, area: PluginFileArea = PluginFileArea.DATA): ByteArray
    suspend fun write(path: String, bytes: ByteArray, area: PluginFileArea = PluginFileArea.DATA)
    suspend fun delete(path: String, area: PluginFileArea = PluginFileArea.DATA): Boolean
    suspend fun list(path: String = "", area: PluginFileArea = PluginFileArea.DATA): List<String>
}
