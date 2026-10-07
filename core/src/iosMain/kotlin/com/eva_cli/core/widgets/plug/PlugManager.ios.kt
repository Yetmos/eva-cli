package com.eva_cli.core.widgets.plug

/** iOS 插件采用构建期接入，运行时本地 .eva 安装暂未实现。 */
internal actual fun installLocalPlugin(source: PluginSource.File): InstalledPlugin =
    throw UnsupportedOperationException("iOS 本地 .eva 安装尚未实现：${source.uri}")

internal actual fun listInstalledPlugins(): List<InstalledPluginInfo> =
    throw UnsupportedOperationException("iOS 已安装插件查询尚未实现")
