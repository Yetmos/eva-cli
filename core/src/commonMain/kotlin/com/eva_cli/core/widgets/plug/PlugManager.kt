package com.eva_cli.core.widgets.plug

// 当前宿主支持的 .eva 发布包格式。
const val CURRENT_PLUGIN_FORMAT_VERSION: Int = 1

// 当前宿主公开的插件 API 版本。
const val CURRENT_PLUGIN_API_VERSION: Int = 1

// 安装插件时发现包内容不满足发布规范。
class PluginInstallException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

// 已完成校验并安装（或发现相同版本已经安装）的插件。
data class InstalledPlugin(
    val manifest: PluginManifest,
    // 以宿主运行目录为基准的已安装目录绝对路径。
    val directory: String,
    // 目标目录已经存在且清单相同，因此本次安装无需替换文件。
    val alreadyInstalled: Boolean,
    // 安装包文件的 SHA-256（小写十六进制）。
    val sha256: String,
)

object PlugManager {

    // TODO: 新增 installedList

    /**
     * 安装插件发布包。
     *
     * 本地文件在各平台通过 actual 实现；URL/Git 来源需要下载器和源码构建流程，
     * 当前会明确抛出 [UnsupportedOperationException]。
     */
    fun installPlugin(plugin: PluginSource): InstalledPlugin = when (plugin) {
        is PluginSource.File -> installLocalPlugin(plugin)
        is PluginSource.Url -> throw UnsupportedOperationException("URL 插件来源尚未实现：${plugin.url}")
        is PluginSource.Git -> throw UnsupportedOperationException("Git 插件来源尚未实现：${plugin.repository}")
    }

    fun uninstallPlugin() {

    }

    // TODO: 备份所有的插件
    fun backup() {

    }

    // TODO: 从备份中恢复插件
    fun restore() {

    }
}

internal expect fun installLocalPlugin(source: PluginSource.File): InstalledPlugin
