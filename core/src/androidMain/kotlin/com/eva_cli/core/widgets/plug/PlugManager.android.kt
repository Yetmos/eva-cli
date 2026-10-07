package com.eva_cli.core.widgets.plug

/** Android 安装器尚未接入 ZipFile/DexClassLoader；保留与 JVM 一致的 API。 */
internal actual fun installLocalPlugin(source: PluginSource.File): InstalledPlugin =
    throw UnsupportedOperationException("Android 本地 .eva 安装尚未实现：${source.uri}")
