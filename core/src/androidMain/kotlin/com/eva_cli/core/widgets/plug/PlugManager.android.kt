package com.eva_cli.core.widgets.plug

/** Android 安装器尚未接入 ZipFile/DexClassLoader；保留与 JVM 一致的 API。 */
internal actual fun installLocalPlugin(source: PluginSource.File): InstalledPlugin =
    throw UnsupportedOperationException("Android 本地 .eva 安装尚未实现：${source.uri}")

internal actual fun listInstalledPlugins(): List<InstalledPluginInfo> =
    throw UnsupportedOperationException("Android 已安装插件查询尚未实现")

internal actual fun uninstallLocalPlugin(id: String, version: String): Boolean =
    throw UnsupportedOperationException("Android 插件卸载尚未实现：$id:$version")

internal actual fun backupLocalPlugins(): Unit =
    throw UnsupportedOperationException("Android 插件备份尚未实现")

internal actual fun restoreLocalPlugins(): Unit =
    throw UnsupportedOperationException("Android 插件恢复尚未实现")
