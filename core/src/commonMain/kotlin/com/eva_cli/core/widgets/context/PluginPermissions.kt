package com.eva_cli.core.widgets.context

enum class PermissionResult { GRANTED, DENIED, UNAVAILABLE }

/** Platform permission access; UNAVAILABLE means the host or platform does not support it. */
interface PluginPermissions {
    suspend fun check(permission: String): PermissionResult
    suspend fun request(permission: String): PermissionResult
}

object UnavailablePluginPermissions : PluginPermissions {
    override suspend fun check(permission: String) = PermissionResult.UNAVAILABLE
    override suspend fun request(permission: String) = PermissionResult.UNAVAILABLE
}
