package com.eva_cli.core.widgets.context

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/** 宿主按插件创建并在组件启动前绑定；资源由宿主在卸载时释放。 */
interface WidgetContext {
    val pluginId: String
    val scope: CoroutineScope
    val network: PluginNetwork
    val preferences: PluginPreferences
    val databases: PluginDatabases
    val files: PluginFiles
    val permissions: PluginPermissions
}

/** 公共边界只使用 SDK 类型，插件无需共享宿主的 Ktor 实现。 */
interface PluginNetwork {
    suspend fun execute(request: NetworkRequest): NetworkResponse
}

data class NetworkRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

data class NetworkResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
)

/** 字符串值可由插件自行序列化；数据在插件升级、卸载加载器后保留。 */
interface PluginPreferences {
    val values: Flow<Map<String, String>>
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
}

enum class PluginFileArea { DATA, CACHE, RESOURCES }

/** 路径为所选目录内的相对路径；RESOURCES 只读。此接口不构成进程级沙盒。 */
interface PluginFiles {
    suspend fun read(path: String, area: PluginFileArea = PluginFileArea.DATA): ByteArray
    suspend fun write(path: String, bytes: ByteArray, area: PluginFileArea = PluginFileArea.DATA)
    suspend fun delete(path: String, area: PluginFileArea = PluginFileArea.DATA): Boolean
    suspend fun list(path: String = "", area: PluginFileArea = PluginFileArea.DATA): List<String>
}

/** 插件可包装自己的 RoomDatabase；仅宿主调用 close，组件停止时不要关闭共享数据库。 */
interface PluginDatabase {
    fun close()
}

fun interface PluginDatabaseFactory<T : PluginDatabase> {
    /** 使用宿主分配的绝对路径构造数据库；schema、DAO、迁移由插件提供。 */
    fun open(path: String): T
}

interface PluginDatabases {
    /**
     * 同名数据库复用同一 factory 创建的实例；同名使用不同 factory 会报错。
     * 同名初始化尚未完成时拒绝递归或并发打开，调用方可在初始化结束后重试。
     * factory 在调用线程执行，应从后台线程调用；factory 内不能卸载所属插件。
     */
    fun <T : PluginDatabase> open(name: String, factory: PluginDatabaseFactory<T>): T
}

enum class PermissionResult { GRANTED, DENIED, UNAVAILABLE }

/** permission 为平台权限标识；没有交互宿主或平台不支持时返回 UNAVAILABLE。 */
interface PluginPermissions {
    suspend fun check(permission: String): PermissionResult
    suspend fun request(permission: String): PermissionResult
}

object UnavailablePluginPermissions : PluginPermissions {
    override suspend fun check(permission: String) = PermissionResult.UNAVAILABLE
    override suspend fun request(permission: String) = PermissionResult.UNAVAILABLE
}
