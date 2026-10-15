package com.eva_cli.core.widgets.context

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
