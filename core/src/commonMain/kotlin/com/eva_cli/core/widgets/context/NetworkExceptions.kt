package com.eva_cli.core.widgets.context

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
