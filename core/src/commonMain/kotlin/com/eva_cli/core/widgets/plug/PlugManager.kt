package com.eva_cli.core.widgets.plug

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.context.PluginPermissions
import com.eva_cli.core.widgets.context.UnavailablePluginPermissions
import com.eva_cli.core.widgets.context.WidgetContext
import kotlin.concurrent.Volatile

// 当前宿主支持的 .eva 发布包格式。
const val CURRENT_PLUGIN_FORMAT_VERSION: Int = 1

// 当前宿主公开的插件 API 版本。
const val CURRENT_PLUGIN_API_VERSION: Int = 3

// 安装插件时发现包内容不满足发布规范。
class PluginInstallException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 *  已完成校验并安装（或发现相同版本已经安装）的插件。
 *  */
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

/** 已加载的入口与组件快照；组件的注册、启动和停止由宿主运行时负责。 */
data class LoadedPlugin(
    val manifest: PluginManifest,
    val directory: String,
    val entry: PluginEntry,
    val providers: List<PluginProvider>,
    val dispatchers: List<PluginDispatcher>,
    val context: WidgetContext,
)

object PlugManager {

    /** 由宿主 UI 安装权限桥接；销毁时恢复默认值。CLI 默认不提供交互权限申请。 */
    @Volatile
    var permissions: PluginPermissions = UnavailablePluginPermissions

    /** 当前进程已加载插件的只读快照，按 ID 排序；Android/iOS 当前不支持查询。 */
    val loadedList: List<LoadedPlugin>
        get() = listLoadedPlugins()

    /**
     * 查询已加载插件中 topic 完全匹配的 Provider，区分大小写，包含未启用的组件。
     * 返回原组件实例的列表快照，顺序为插件 ID、插件内声明顺序；不注册或启动组件。
     * topic 须为规范绝对路径（如 /、/Chat、/Chat/Search），不接受空路径段、
     * 首尾空白路径段、.、..、反斜杠或控制字符，否则抛出 [IllegalArgumentException]。
     * Android/iOS 当前与 [loadedList] 一样不支持查询。
     */
    fun findProviders(topic: String): List<PluginProvider> {
        require(isValidTopic(topic)) { "topic 不是规范的绝对路径：$topic" }
        return loadedList.flatMap { plugin -> plugin.providers.filter { it.topic == topic } }
    }

    /**
     * 查询 topic 的直接子级 Dispatcher；/ 表示查询根路径，不返回自身或更深层后代。
     * 路径规则与 [findProviders] 相同；忽略 topic 不规范的 Dispatcher。
     * 返回原组件实例的列表快照，顺序为插件 ID、插件内声明顺序，包含未启用的组件。
     * 不注册或启动组件；Android/iOS 当前与 [loadedList] 一样不支持查询。
     */
    fun findChildDispatchers(topic: String): List<PluginDispatcher> {
        require(isValidTopic(topic)) { "topic 不是规范的绝对路径：$topic" }
        return loadedList.flatMap { plugin ->
            plugin.dispatchers.filter { dispatcher ->
                val childTopic = dispatcher.topic
                childTopic != "/" && isValidTopic(childTopic) &&
                    childTopic.substringBeforeLast('/').ifEmpty { "/" } == topic
            }
        }
    }

    private fun isValidTopic(topic: String): Boolean = topic == "/" ||
        topic.startsWith('/') && topic.substring(1).split('/').all { segment ->
            segment.isNotBlank() && segment == segment.trim() && segment != "." && segment != ".." &&
                segment.none { it == '\\' || it.isISOControl() }
        }

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
     * 加载已安装的插件入口及其组件，绑定插件专属 Context，不注册或启动组件。
     * 同一 ID 只允许加载一个版本；相同版本和安装目录重复加载返回同一实例。
     * id/version 无效时抛出 [IllegalArgumentException]；冲突或加载失败时抛出
     * [IllegalStateException]。同一 ID 正在加载或卸载时拒绝重入与并发操作。
     * 此操作同步访问文件并执行插件初始化，应在后台线程调用；Android/iOS 尚未支持。
     */
    fun loadPlugin(id: String, version: String): LoadedPlugin = loadLocalPlugin(id, version)

    /**
     * 取消并等待 Context 任务，关闭插件数据库、网络客户端和加载器，移除已加载记录。
     * 成功返回 true，未加载返回 false；应在宿主后台线程调用，不得从该插件 scope 内调用。
     * 宿主须先停止并注销组件，释放对入口及组件的引用；此方法不调用组件 stop。
     * 卸载、替换或恢复插件文件前也应先调用本方法。
     * id 无效时抛出 [IllegalArgumentException]，资源释放失败或同一 ID 正在变更时
     * 抛出 [IllegalStateException]；资源释放失败也会移除加载记录，避免复用已关闭的加载器。
     * Android/iOS 当前不支持。
     */
    fun unloadPlugin(id: String): Boolean = unloadLocalPlugin(id)

    /**
     * 删除指定插件版本的安装目录；删除成功返回 true，未安装返回 false。
     * 无需清单有效，保留其他版本、Data/Cache 及空的插件 ID 目录；不处理已加载的插件实例。
     * id/version 必须是安全的单个目录名，否则抛出 [IllegalArgumentException]。
     * 安装路径不是普通目录或删除失败时抛出 [IllegalStateException]。
     * 此操作同步访问文件，应在后台线程调用；Android/iOS 当前不支持卸载。
     */
    fun uninstallPlugin(id: String, version: String): Boolean = uninstallLocalPlugin(id, version)

    /**
     * 将所有有效的已安装插件版本备份到 Backup/plugins.zip；[includeRuntimeData] 为 true 时，
     * 同时备份已安装插件按 ID 共享的 Data，始终排除 Cache。
     * 当 [includeRuntimeData] 为 true 时，相关插件须先停止并卸载已加载实例，避免数据库或文件仍在写入。
     * 备份期间不得并发安装、加载、卸载插件或写入运行数据。
     * 此操作同步访问文件，应在后台线程调用；Android/iOS 当前不支持备份。
     */
    fun backup(includeRuntimeData: Boolean): Unit = backupLocalPlugins(includeRuntimeData)

    /**
     * 校验 Backup/plugins.zip 后恢复完整安装快照，移除备份后新增的插件版本。
     * 默认快照仅替换 versions；包含运行数据的快照同时替换快照内插件的 Data。
     * 快照中的 Data 缺失表示空状态，会清除该插件当前的 Data；始终保留 Cache 和快照外插件的 Data。
     * 恢复运行数据前，相关插件须先停止并卸载已加载实例；恢复期间不得并发生命周期操作或数据写入。
     * 快照不存在、损坏或恢复失败时抛出 [IllegalStateException]；不会自动卸载已加载的实例。
     * 此操作同步访问文件，应在后台线程调用；Android/iOS 当前不支持恢复。
     */
    fun restore(): Unit = restoreLocalPlugins()
}

internal expect fun installLocalPlugin(source: PluginSource.File): InstalledPlugin

internal expect fun listInstalledPlugins(): List<InstalledPluginInfo>

internal expect fun loadLocalPlugin(id: String, version: String): LoadedPlugin

internal expect fun unloadLocalPlugin(id: String): Boolean

internal expect fun listLoadedPlugins(): List<LoadedPlugin>

internal expect fun uninstallLocalPlugin(id: String, version: String): Boolean

internal expect fun backupLocalPlugins(includeRuntimeData: Boolean)

internal expect fun restoreLocalPlugins()
