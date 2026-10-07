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

/** 从安装目录读取的插件信息；每个已安装版本对应一项。 */
data class InstalledPluginInfo(
    val manifest: PluginManifest,
    // 插件安装目录的绝对路径。
    val directory: String,
)

object PlugManager {

    /**
     * 已安装插件的只读快照，每次读取均扫描磁盘，按 ID、版本字符串排序。
     * JVM 跳过临时目录、无效或不兼容的安装项；Android/iOS 当前不支持查询。
     * 此操作同步读取文件，应在后台线程调用。
     */
    val installedList: List<InstalledPluginInfo>
        get() = listInstalledPlugins()

    /**
     * 安装插件发布包。
     * 本地文件在各平台通过 actual 实现；URL/Git 来源需要下载器和源码构建流程，
     * 当前会明确抛出 [UnsupportedOperationException]。
     */
    fun installPlugin(plugin: PluginSource): InstalledPlugin = when (plugin) {
        is PluginSource.File -> installLocalPlugin(plugin)
        is PluginSource.Url -> throw UnsupportedOperationException("URL 插件来源尚未实现：${plugin.url}")
        is PluginSource.Git -> throw UnsupportedOperationException("Git 插件来源尚未实现：${plugin.repository}")
    }

    /**
     * 删除指定插件版本的安装目录；删除成功返回 true，未安装返回 false。
     * 无需清单有效，保留其他版本及空的插件 ID 目录；不处理已加载的插件实例。
     * id/version 必须是安全的单个目录名，否则抛出 [IllegalArgumentException]。
     * 安装路径不是普通目录或删除失败时抛出 [IllegalStateException]。
     * 此操作同步访问文件，应在后台线程调用；Android/iOS 当前不支持卸载。
     */
    fun uninstallPlugin(id: String, version: String): Boolean = uninstallLocalPlugin(id, version)

    /**
     * 将所有有效的已安装插件版本备份到 Backup/plugins.zip，成功后替换上一次快照。
     * 包含插件目录中的资源、源码和空目录；失败时抛出 [IllegalStateException]。
     * 此操作同步访问文件，应在后台线程调用；Android/iOS 当前不支持备份。
     */
    fun backup(): Unit = backupLocalPlugins()

    /**
     * 校验 Backup/plugins.zip 后恢复完整安装快照，移除备份后新增的插件版本。
     * 快照不存在、损坏或恢复失败时抛出 [IllegalStateException]；不处理已加载的实例。
     * 此操作同步访问文件，应在后台线程调用；Android/iOS 当前不支持恢复。
     */
    fun restore(): Unit = restoreLocalPlugins()
}

internal expect fun installLocalPlugin(source: PluginSource.File): InstalledPlugin

internal expect fun listInstalledPlugins(): List<InstalledPluginInfo>

internal expect fun uninstallLocalPlugin(id: String, version: String): Boolean

internal expect fun backupLocalPlugins()

internal expect fun restoreLocalPlugins()
