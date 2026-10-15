package com.eva_cli.core.widgets.context

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
