package com.eva_cli.core.widgets.context

/** A plugin-owned database that the host closes during plugin teardown. */
interface PluginDatabase {
    fun close()
}

fun interface PluginDatabaseFactory<T : PluginDatabase> {
    /** The host supplies an absolute path; schema, DAOs, and migrations come from the plugin. */
    fun open(path: String): T
}

interface PluginDatabases {
    /** Opens or returns the named database, rejecting concurrent initialization. */
    fun <T : PluginDatabase> open(name: String, factory: PluginDatabaseFactory<T>): T
}
