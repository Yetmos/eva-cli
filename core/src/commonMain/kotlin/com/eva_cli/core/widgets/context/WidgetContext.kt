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
    /** Host-wide controls. Implementations may expose a stricter policy. */
    val policy: PluginNetworkPolicy
        get() = PluginNetworkPolicy()

    /** Point-in-time counters. Hosts may override with live counters. */
    val metrics: NetworkMetrics
        get() = NetworkMetrics()

    suspend fun execute(request: NetworkRequest): NetworkResponse

    /** Streaming-compatible API; hosts should override to avoid response buffering. */
    suspend fun download(request: NetworkRequest, onChunk: suspend (ByteArray) -> Unit): NetworkResponse {
        val response = execute(request)
        if (response.body.isNotEmpty()) onChunk(response.body)
        return response
    }

    suspend fun stream(request: NetworkRequest, onChunk: suspend (ByteArray) -> Unit): NetworkResponse =
        download(request, onChunk)
}

data class NetworkRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    /** If true, 4xx/5xx responses are raised as [NetworkHttpException]. */
    val failOnHttpError: Boolean = false,
    val connectTimeoutMillis: Long? = null,
    val requestTimeoutMillis: Long? = null,
    val readTimeoutMillis: Long? = null,
    val maxRequestBytes: Long? = null,
    val maxResponseBytes: Long? = null,
    val followRedirects: Boolean = true,
    val maxRedirects: Int = 5,
    /** Whether redirects may change the request host. */
    val allowCrossHostRedirects: Boolean = true,
    /** Whether credentials/cookies may be retained across a host change. */
    val preserveSensitiveHeadersOnRedirect: Boolean = false,
    val allowInsecureHttp: Boolean = true,
    val allowedHosts: Set<String> = emptySet(),
    val allowPrivateAddresses: Boolean = true,
)

data class NetworkResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
    val effectiveUrl: String = "",
    val redirectCount: Int = 0,
    val contentLength: Long? = null,
) {
    val isSuccessful: Boolean get() = status in 200..299
}

/** Host-wide network controls. Null limits preserve the old unrestricted behavior. */
data class PluginNetworkPolicy(
    val connectTimeoutMillis: Long? = null,
    val requestTimeoutMillis: Long? = null,
    val readTimeoutMillis: Long? = null,
    val maxRequestBytes: Long? = null,
    val maxResponseBytes: Long? = null,
    val followRedirects: Boolean = true,
    val maxRedirects: Int = 5,
    val allowCrossHostRedirects: Boolean = true,
    val preserveSensitiveHeadersOnRedirect: Boolean = false,
    val allowInsecureHttp: Boolean = true,
    val allowedHosts: Set<String> = emptySet(),
    val allowPrivateAddresses: Boolean = true,
    val maxConcurrentRequests: Int = 64,
    /** Optional rolling one-minute request quota. */
    val maxRequestsPerMinute: Int? = null,
    /** Optional rolling one-minute byte quota (request + response bytes). */
    val maxBytesPerMinute: Long? = null,
) {
    init {
        require(connectTimeoutMillis == null || connectTimeoutMillis > 0) { "connectTimeoutMillis must be positive" }
        require(requestTimeoutMillis == null || requestTimeoutMillis > 0) { "requestTimeoutMillis must be positive" }
        require(readTimeoutMillis == null || readTimeoutMillis > 0) { "readTimeoutMillis must be positive" }
        require(maxRequestBytes == null || maxRequestBytes >= 0) { "maxRequestBytes must be non-negative" }
        require(maxResponseBytes == null || maxResponseBytes >= 0) { "maxResponseBytes must be non-negative" }
        require(maxRedirects >= 0) { "maxRedirects must be non-negative" }
        require(maxConcurrentRequests > 0) { "maxConcurrentRequests must be positive" }
        require(maxRequestsPerMinute == null || maxRequestsPerMinute > 0) { "maxRequestsPerMinute must be positive" }
        require(maxBytesPerMinute == null || maxBytesPerMinute >= 0) { "maxBytesPerMinute must be non-negative" }
    }
}

/** Counters exposed by a host network implementation for diagnostics and quotas. */
data class NetworkMetrics(
    val totalRequests: Long = 0,
    val activeRequests: Int = 0,
    val failedRequests: Long = 0,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
)

open class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

enum class NetworkTimeoutPhase { CONNECT, REQUEST, READ }

class NetworkTimeoutException(
    val timeoutMillis: Long?,
    val phase: NetworkTimeoutPhase = NetworkTimeoutPhase.REQUEST,
    cause: Throwable? = null,
) : NetworkException("Network $phase timeout${timeoutMillis?.let { " after ${it}ms" } ?: ""}", cause)

class NetworkInvalidUrlException(val url: String, cause: Throwable? = null) :
    NetworkException("Invalid network URL: $url", cause)

class NetworkRequestTooLargeException(val limit: Long, val actual: Long? = null, cause: Throwable? = null) :
    NetworkException("Request body exceeds limit of $limit bytes", cause)

class NetworkResponseTooLargeException(val limit: Long, val actual: Long? = null, cause: Throwable? = null) :
    NetworkException("Response body exceeds limit of $limit bytes", cause)

enum class NetworkPolicyViolation {
    INVALID_URL, UNSUPPORTED_SCHEME, INSECURE_HTTP, HOST_NOT_ALLOWED, PRIVATE_ADDRESS,
    REDIRECT_LIMIT, INVALID_REQUEST, INVALID_HEADER, CONCURRENCY_LIMIT, RATE_LIMIT, BYTE_RATE_LIMIT,
}

class NetworkPolicyException(
    val violation: NetworkPolicyViolation,
    message: String = violation.name,
    cause: Throwable? = null,
) : NetworkException(message, cause)

class NetworkConnectionException(message: String = "Network connection failed", cause: Throwable? = null) :
    NetworkException(message, cause)

class NetworkDnsException(val host: String? = null, cause: Throwable? = null) :
    NetworkException("DNS resolution failed${host?.let { " for $it" } ?: ""}", cause)

class NetworkTlsException(message: String = "TLS handshake failed", cause: Throwable? = null) :
    NetworkException(message, cause)

class NetworkRedirectException(val maxRedirects: Int, cause: Throwable? = null) :
    NetworkException("Maximum redirects exceeded: $maxRedirects", cause)

class NetworkCancelledException(cause: Throwable? = null) : NetworkException("Network request was cancelled", cause)

class NetworkHttpException(
    val status: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: ByteArray = ByteArray(0),
) : NetworkException("HTTP request failed with status $status") {
    val responseBody: ByteArray get() = body
}

/** Validates engine-independent request constraints before dispatch. */
fun NetworkRequest.validate(policy: PluginNetworkPolicy = PluginNetworkPolicy()) {
    if (url.isBlank()) throw NetworkInvalidUrlException(url)
    val schemeEnd = url.indexOf(':')
    val scheme = if (schemeEnd > 0) url.substring(0, schemeEnd).lowercase() else ""
    if (scheme != "http" && scheme != "https") {
        throw NetworkPolicyException(NetworkPolicyViolation.UNSUPPORTED_SCHEME, "Unsupported URL scheme: $scheme")
    }
    if (schemeEnd + 2 >= url.length || url[schemeEnd + 1] != '/' || url[schemeEnd + 2] != '/') {
        throw NetworkInvalidUrlException(url)
    }
    val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), startIndex = schemeEnd + 3)
    val authority = url.substring(schemeEnd + 3, if (authorityEnd < 0) url.length else authorityEnd)
    if (authority.isBlank() || authority.any { it.isWhitespace() } || authority.endsWith(':')) {
        throw NetworkInvalidUrlException(url)
    }
    if (scheme == "http" && (!allowInsecureHttp || !policy.allowInsecureHttp)) {
        throw NetworkPolicyException(NetworkPolicyViolation.INSECURE_HTTP)
    }
    if (!method.matches(Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$"))) {
        throw NetworkPolicyException(NetworkPolicyViolation.INVALID_REQUEST, "Invalid HTTP method: $method")
    }
    headers.forEach { (name, value) ->
        if (!name.matches(Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")) || value.any { it == '\r' || it == '\n' }) {
            throw NetworkPolicyException(NetworkPolicyViolation.INVALID_HEADER, "Invalid HTTP header: $name")
        }
    }
    requirePositive(connectTimeoutMillis, "connectTimeoutMillis")
    requirePositive(requestTimeoutMillis, "requestTimeoutMillis")
    requirePositive(readTimeoutMillis, "readTimeoutMillis")
    requireNonNegative(maxRequestBytes, "maxRequestBytes")
    requireNonNegative(maxResponseBytes, "maxResponseBytes")
    if (maxRedirects < 0) throw NetworkPolicyException(NetworkPolicyViolation.INVALID_REQUEST, "maxRedirects must be non-negative")
    body?.let {
        val limit = listOfNotNull(policy.maxRequestBytes, maxRequestBytes).minOrNull()
        if (limit != null && it.size.toLong() > limit) throw NetworkRequestTooLargeException(limit, it.size.toLong())
    }
}

private fun requirePositive(value: Long?, name: String) {
    if (value != null && value <= 0) throw NetworkPolicyException(NetworkPolicyViolation.INVALID_REQUEST, "$name must be positive")
}

private fun requireNonNegative(value: Long?, name: String) {
    if (value != null && value < 0) throw NetworkPolicyException(NetworkPolicyViolation.INVALID_REQUEST, "$name must be non-negative")
}

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
