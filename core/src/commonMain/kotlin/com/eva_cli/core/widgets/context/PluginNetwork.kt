package com.eva_cli.core.widgets.context

/** Public network boundary exposed to plugins. */
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
