package com.eva_cli.core.widgets.plug

/** iOS 插件采用构建期接入，运行时本地 .eva 安装暂未实现。 */
internal actual fun installLocalPlugin(source: PluginSource.File): InstalledPlugin =
    throw UnsupportedOperationException("iOS 本地 .eva 安装尚未实现：${source.uri}")

internal actual fun listInstalledPlugins(): List<InstalledPluginInfo> =
    throw UnsupportedOperationException("iOS 已安装插件查询尚未实现")

internal actual fun loadLocalPlugin(id: String, version: String): LoadedPlugin =
    throw UnsupportedOperationException("iOS 运行时插件加载尚未实现：$id:$version")

internal actual fun unloadLocalPlugin(id: String): Boolean =
    throw UnsupportedOperationException("iOS 插件加载资源释放尚未实现：$id")

internal actual fun listLoadedPlugins(): List<LoadedPlugin> =
    throw UnsupportedOperationException("iOS 已加载插件查询尚未实现")

internal actual fun uninstallLocalPlugin(id: String, version: String): Boolean =
    throw UnsupportedOperationException("iOS 插件卸载尚未实现：$id:$version")

internal actual fun backupLocalPlugins(includeRuntimeData: Boolean): Unit =
    throw UnsupportedOperationException("iOS 插件备份尚未实现")

internal actual fun restoreLocalPlugins(): Unit =
    throw UnsupportedOperationException("iOS 插件恢复尚未实现")
