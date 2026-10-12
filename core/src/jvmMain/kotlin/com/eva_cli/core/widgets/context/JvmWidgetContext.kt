package com.eva_cli.core.widgets.context

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eva_cli.core.runtime.Configure
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readAvailable
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** One owner per loaded plugin; close before another context opens the same plugin data. */
internal class JvmWidgetContext(
    override val pluginId: String,
    runtimeDirectory: Path,
    pluginDirectory: Path,
    resourcesPath: String?,
    permissions: PluginPermissions,
    networkPolicy: PluginNetworkPolicy = PluginNetworkPolicy(),
) : WidgetContext {
    private val lock = Any()
    private val closeLock = Any()
    private var closed = false
    private val lifetime = SupervisorJob()
    private val activeScope = ThreadLocal<Boolean>()
    override val scope = CoroutineScope(lifetime + Dispatchers.IO + activeScope.asContextElement(true))
    private val runtimeRoot = runtimeDirectory.toAbsolutePath().normalize().toRealPath()
    private val pluginRoot = pluginDirectory.toAbsolutePath().normalize().toRealPath()
    private val resourcesRoot: Path?
    private val permissionBridge = permissions
    private var client: HttpClient? = null
    private val networkPolicy = networkPolicy
    private val networkPermits = Semaphore(networkPolicy.maxConcurrentRequests)
    private val requestCount = AtomicLong()
    private val failedCount = AtomicLong()
    private val sentBytes = AtomicLong()
    private val receivedBytes = AtomicLong()
    private val minuteWindow = ArrayDeque<Long>()
    private val minuteBytes = ArrayDeque<ByteCount>()
    private val rateLock = Any()
    private var store: DataStore<Preferences>? = null
    private val openDatabases = mutableMapOf<String, DatabaseEntry>()
    private val openingDatabases = mutableMapOf<String, DatabaseOpening>()

    init {
        requireSingleName(pluginId)
        resourcesRoot = resourcesPath?.let { safePath(pluginRoot, it) }
    }

    override val network: PluginNetwork = object : PluginNetwork {
        override val policy: PluginNetworkPolicy get() = networkPolicy
        override val metrics: NetworkMetrics
            get() = NetworkMetrics(
                totalRequests = requestCount.get(),
                activeRequests = networkPolicy.maxConcurrentRequests - networkPermits.availablePermits,
                failedRequests = failedCount.get(),
                bytesSent = sentBytes.get(),
                bytesReceived = receivedBytes.get(),
            )

        override suspend fun execute(request: NetworkRequest): NetworkResponse = withinLifetime {
            performNetworkRequest(request, stream = null)
        }

        override suspend fun download(
            request: NetworkRequest,
            onChunk: suspend (ByteArray) -> Unit,
        ): NetworkResponse = withinLifetime {
            requireNotNull(onChunk) { "onChunk" }
            performNetworkRequest(request, stream = onChunk)
        }
    }

    private suspend fun <T> withNetworkPermit(block: suspend () -> T): T {
        networkPermits.acquire()
        try {
            reserveRequestRateSlot()
            return block()
        } finally {
            networkPermits.release()
        }
    }

    private suspend fun reserveRequestRateSlot() {
        val limit = networkPolicy.maxRequestsPerMinute ?: return
        while (true) {
            val available = synchronized(rateLock) {
                val now = System.currentTimeMillis()
                trimRateWindow(now)
                if (minuteWindow.size < limit) {
                    minuteWindow.addLast(now)
                    true
                } else {
                    false
                }
            }
            if (available) return
            kotlinx.coroutines.delay(1_000L)
        }
    }

    private fun trimRateWindow(now: Long) {
        val cutoff = now - 60_000L
        while (minuteWindow.firstOrNull()?.let { it <= cutoff } == true) minuteWindow.removeFirst()
        while (minuteBytes.firstOrNull()?.timestamp?.let { it <= cutoff } == true) minuteBytes.removeFirst()
    }

    private fun consumeBytesQuota(bytes: Long) {
        val limit = networkPolicy.maxBytesPerMinute ?: return
        if (bytes <= 0) return
        synchronized(rateLock) {
            val now = System.currentTimeMillis()
            trimRateWindow(now)
            val used = minuteBytes.sumOf { it.bytes }
            if (used + bytes > limit) {
                throw NetworkPolicyException(NetworkPolicyViolation.BYTE_RATE_LIMIT, "Network byte rate limit exceeded")
            }
            minuteBytes.addLast(ByteCount(now, bytes))
        }
    }

    private data class ByteCount(val timestamp: Long, val bytes: Long)

    /** Executes one request, manually following redirects so each hop is policy checked. */
    private suspend fun performNetworkRequest(
        initial: NetworkRequest,
        stream: (suspend (ByteArray) -> Unit)?,
    ): NetworkResponse {
        val requestTimeout = effectiveTimeout(initial.requestTimeoutMillis) ?: Long.MAX_VALUE
        return try {
            initial.validate(networkPolicy)
            if (requestTimeout == Long.MAX_VALUE) {
                withNetworkPermit { performNetworkRequestUnbounded(initial, stream) }
            } else {
                withTimeoutOrNull(requestTimeout) {
                    withNetworkPermit { performNetworkRequestUnbounded(initial, stream) }
                }
                    ?: throw NetworkTimeoutException(requestTimeout, NetworkTimeoutPhase.REQUEST)
            }
        } catch (error: NetworkTimeoutException) {
            failedCount.incrementAndGet()
            throw error
        } catch (error: CancellationException) {
            // Preserve structured cancellation for unload/caller cancellation. An engine-level
            // cancellation while this coroutine is otherwise active is exposed uniformly.
            if (!currentCoroutineContext().isActive) throw error
            failedCount.incrementAndGet()
            throw NetworkCancelledException(error)
        } catch (error: NetworkException) {
            failedCount.incrementAndGet()
            throw error
        } catch (error: Throwable) {
            failedCount.incrementAndGet()
            throw mapNetworkError(error, initial.url)
        }
    }

    private suspend fun performNetworkRequestUnbounded(
        initial: NetworkRequest,
        stream: (suspend (ByteArray) -> Unit)?,
    ): NetworkResponse {
        val requestLimit = listOfNotNull(networkPolicy.maxRequestBytes, initial.maxRequestBytes).minOrNull()

        var currentUrl = initial.url
        var method = initial.method
        var body = initial.body
        var redirectCount = 0
        var credentialsAllowed = true
        while (true) {
            validateNetworkUrl(currentUrl, initial)
            val sent = body?.size?.toLong() ?: 0L
            if (requestLimit != null && sent > requestLimit) throw NetworkRequestTooLargeException(requestLimit, sent, null)
            consumeBytesQuota(sent)
            if (redirectCount > 0) reserveRequestRateSlot()
            sentBytes.addAndGet(sent)
            requestCount.incrementAndGet()
            val http = synchronized(lock) {
                requireOpen()
                client ?: HttpClient(OkHttp) {
                    // Redirects are handled here to enforce host and private-address checks per hop.
                    followRedirects = false
                    install(HttpTimeout) {
                        connectTimeoutMillis = networkPolicy.connectTimeoutMillis
                        requestTimeoutMillis = networkPolicy.requestTimeoutMillis
                        socketTimeoutMillis = networkPolicy.readTimeoutMillis
                    }
                }.also { client = it }
            }
            val response = http.request(currentUrl) {
                this.method = HttpMethod.parse(method)
                val sameOrigin = credentialsAllowed
                initial.headers.forEach { (name, value) ->
                    // Credentials must not cross an origin boundary during redirects.
                    if (sameOrigin || !name.equals("Authorization", true) && !name.equals("Proxy-Authorization", true) && !name.equals("Cookie", true)) {
                        headers.append(name, value)
                    }
                }
                body?.let { setBody(it) }
                timeout {
                    connectTimeoutMillis = effectiveConnectTimeout(initial.connectTimeoutMillis)
                    requestTimeoutMillis = effectiveRequestTimeout(initial.requestTimeoutMillis)
                    socketTimeoutMillis = effectiveReadTimeout(initial.readTimeoutMillis)
                }
            }
            val status = response.status.value
            val redirectStatus = status == 301 || status == 302 || status == 303 || status == 307 || status == 308
            if (redirectStatus && initial.followRedirects && networkPolicy.followRedirects) {
                val location = response.headers["Location"]
                if (!location.isNullOrBlank()) {
                    if (redirectCount >= minOf(initial.maxRedirects, networkPolicy.maxRedirects)) {
                        response.bodyAsChannel().cancel(IllegalStateException("redirect limit"))
                        throw NetworkRedirectException(minOf(initial.maxRedirects, networkPolicy.maxRedirects), null)
                    }
                    val nextUrl = URI(currentUrl).resolve(location).toString()
                    val oldUri = URI(currentUrl)
                    val newUri = URI(nextUrl)
                    val oldHost = oldUri.host?.lowercase(Locale.ROOT)
                    val newHost = newUri.host?.lowercase(Locale.ROOT)
                    val originChanged = !sameOrigin(oldUri, newUri)
                    val secureDowngrade = oldUri.scheme.equals("https", true) && newUri.scheme.equals("http", true)
                    if (secureDowngrade) {
                        response.bodyAsChannel().cancel(IllegalStateException("insecure redirect"))
                        throw NetworkPolicyException(
                            NetworkPolicyViolation.INSECURE_HTTP,
                            "HTTPS to HTTP redirects are disabled",
                        )
                    }
                    if (oldHost != newHost && (!initial.allowCrossHostRedirects || !networkPolicy.allowCrossHostRedirects)) {
                        response.bodyAsChannel().cancel(IllegalStateException("cross-host redirect"))
                        throw NetworkPolicyException(NetworkPolicyViolation.HOST_NOT_ALLOWED, "Cross-host redirects are disabled")
                    }
                    currentUrl = nextUrl
                    redirectCount++
                    if (originChanged && oldHost == newHost) {
                        // Never send credentials to a changed origin without an explicit
                        // cross-host opt-in. HTTPS downgrades are rejected above.
                        credentialsAllowed = false
                    } else if (oldHost != newHost) {
                        // Once credentials are stripped, they must not be reintroduced by a
                        // later redirect hop (for example after an HTTPS downgrade).
                        credentialsAllowed = credentialsAllowed &&
                            initial.preserveSensitiveHeadersOnRedirect &&
                            networkPolicy.preserveSensitiveHeadersOnRedirect
                    }
                    if (status == 301 || status == 302 || status == 303) {
                        // RFC 9110 allows changing method for 301/302/303.
                        method = "GET"
                        body = null
                    }
                    response.bodyAsChannel().cancel(IllegalStateException("redirecting"))
                    continue
                }
            }
            val maxResponse = listOfNotNull(networkPolicy.maxResponseBytes, initial.maxResponseBytes).minOrNull()
            val contentLength = response.headers["Content-Length"]?.toLongOrNull()
            if (maxResponse != null && contentLength != null && contentLength > maxResponse) {
                response.bodyAsChannel().cancel(IllegalStateException("response too large"))
                throw NetworkResponseTooLargeException(maxResponse, contentLength, null)
            }
            val bytes = readResponse(response, maxResponse, stream)
            receivedBytes.addAndGet(bytes.second)
            return NetworkResponse(
                // HTTP errors remain normal responses by default for compatibility.
                status = status,
                headers = response.headers.entries().associate { it.key to it.value },
                body = bytes.first,
                effectiveUrl = currentUrl,
                redirectCount = redirectCount,
                contentLength = contentLength ?: bytes.second,
            ).also { result ->
                if (initial.failOnHttpError && status >= 400) {
                    throw NetworkHttpException(status, result.headers, result.body)
                }
            }
        }
    }

    private suspend fun readResponse(
        response: HttpResponse,
        maxBytes: Long?,
        stream: (suspend (ByteArray) -> Unit)?,
    ): Pair<ByteArray, Long> {
        val channel = response.bodyAsChannel()
        val output = if (stream == null) java.io.ByteArrayOutputStream() else null
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        try {
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read == 0) {
                    kotlinx.coroutines.yield()
                    continue
                }
                total += read
                consumeBytesQuota(read.toLong())
                if (maxBytes != null && total > maxBytes) {
                    throw NetworkResponseTooLargeException(maxBytes, total, null)
                }
                val chunk = buffer.copyOf(read)
                if (stream != null) stream(chunk) else output!!.write(chunk)
            }
            return (output?.toByteArray() ?: ByteArray(0)) to total
        } catch (error: Throwable) {
            channel.cancel(error)
            throw error
        }
    }

    private fun validateNetworkUrl(url: String, request: NetworkRequest) {
        val uri = try { URI(url) } catch (error: Throwable) { throw NetworkInvalidUrlException(url, error) }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.port > 65535) {
            throw NetworkInvalidUrlException(url)
        }
        if (scheme == "http" && (!request.allowInsecureHttp || !networkPolicy.allowInsecureHttp)) {
            throw NetworkPolicyException(NetworkPolicyViolation.INSECURE_HTTP)
        }
        val host = uri.host!!.lowercase(Locale.ROOT).trimEnd('.')
        if (networkPolicy.allowedHosts.isNotEmpty() && !networkPolicy.allowedHosts.any { host == it.lowercase(Locale.ROOT).trimEnd('.') }) {
            throw NetworkPolicyException(NetworkPolicyViolation.HOST_NOT_ALLOWED, "Host is not allowed: $host")
        }
        if (request.allowedHosts.isNotEmpty() && !request.allowedHosts.any { host == it.lowercase(Locale.ROOT).trimEnd('.') }) {
            throw NetworkPolicyException(NetworkPolicyViolation.HOST_NOT_ALLOWED, "Host is not allowed: $host")
        }
        if (!request.allowPrivateAddresses || !networkPolicy.allowPrivateAddresses) {
            val addresses = try { InetAddress.getAllByName(host) } catch (error: UnknownHostException) {
                throw NetworkDnsException(host, error)
            }
            if (addresses.any(::isPrivateAddress)) {
                throw NetworkPolicyException(NetworkPolicyViolation.PRIVATE_ADDRESS, "Private address is not allowed: $host")
            }
        }
    }

    private fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return true
        val bytes = address.address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            // Carrier-grade NAT and benchmarking ranges are not globally routable.
            if (first == 100 && second in 64..127) return true
            if (first == 192 && second == 0) return true
            if (first == 198 && second in 18..19) return true
        } else if (bytes.size == 16) {
            // IPv6 unique-local addresses (fc00::/7).
            if ((bytes[0].toInt() and 0xfe) == 0xfc) return true
        }
        return false
    }

    private fun sameOrigin(first: URI, second: URI): Boolean {
        if (!first.scheme.equals(second.scheme, true)) return false
        if (!first.host.equals(second.host, true)) return false
        return effectivePort(first) == effectivePort(second)
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", true) -> 443
        else -> 80
    }

    private fun effectiveTimeout(requestValue: Long?): Long? = listOfNotNull(networkPolicy.requestTimeoutMillis, requestValue).minOrNull()
    private fun effectiveConnectTimeout(requestValue: Long?): Long? = listOfNotNull(networkPolicy.connectTimeoutMillis, requestValue).minOrNull()
    private fun effectiveRequestTimeout(requestValue: Long?): Long? = listOfNotNull(networkPolicy.requestTimeoutMillis, requestValue).minOrNull()
    private fun effectiveReadTimeout(requestValue: Long?): Long? = listOfNotNull(networkPolicy.readTimeoutMillis, requestValue).minOrNull()

    private fun mapNetworkError(error: Throwable, url: String): NetworkException = when (error) {
        is NetworkException -> error
        is HttpRequestTimeoutException -> NetworkTimeoutException(null, NetworkTimeoutPhase.REQUEST, error)
        is ConnectTimeoutException -> NetworkTimeoutException(null, NetworkTimeoutPhase.CONNECT, error)
        is java.net.SocketTimeoutException -> NetworkTimeoutException(null, NetworkTimeoutPhase.READ, error)
        is UnknownHostException -> NetworkDnsException(try { URI(url).host } catch (_: Throwable) { null }, error)
        is SSLException, is CertificateException -> NetworkTlsException(cause = error)
        is ConnectException -> NetworkConnectionException(cause = error)
        is IOException -> NetworkConnectionException(cause = error)
        is IllegalArgumentException -> NetworkPolicyException(NetworkPolicyViolation.INVALID_REQUEST, error.message ?: "Invalid request", error)
        else -> NetworkConnectionException(cause = error)
    }

    override val preferences: PluginPreferences = object : PluginPreferences {
        override val values: Flow<Map<String, String>> = channelFlow {
            withinLifetime {
                preferenceStore().data.collect { preferences ->
                    send(preferences.asMap().entries.associate { (key, value) -> key.name to value as String })
                }
            }
        }

        override suspend fun get(key: String): String? = withinLifetime {
            preferenceStore().data.first()[stringPreferencesKey(key)]
        }

        override suspend fun put(key: String, value: String) = withinLifetime {
            preferenceStore().edit { it[stringPreferencesKey(key)] = value }
            Unit
        }

        override suspend fun remove(key: String) = withinLifetime {
            preferenceStore().edit { it.remove(stringPreferencesKey(key)) }
            Unit
        }
    }

    override val files: PluginFiles = object : PluginFiles {
        override suspend fun read(path: String, area: PluginFileArea): ByteArray = withinLifetime {
            Files.readAllBytes(filePath(path, area))
        }

        override suspend fun write(path: String, bytes: ByteArray, area: PluginFileArea) = withinLifetime {
            requireWritable(area)
            val target = filePath(path, area)
            Files.createDirectories(target.parent)
            // Recheck after creating parents, including symlinks inserted since the first check.
            Files.write(filePath(path, area), bytes)
            Unit
        }

        override suspend fun delete(path: String, area: PluginFileArea): Boolean = withinLifetime {
            requireWritable(area)
            Files.deleteIfExists(filePath(path, area))
        }

        override suspend fun list(path: String, area: PluginFileArea): List<String> = withinLifetime {
            val directory = filePath(path, area, allowEmpty = true)
            if (!Files.exists(directory, NOFOLLOW_LINKS)) return@withinLifetime emptyList()
            Files.list(directory).use { entries ->
                entries.map { entry ->
                    require(!Files.isSymbolicLink(entry)) { "Symbolic links are not supported: $entry" }
                    entry.fileName.toString()
                }.sorted().toList()
            }
        }
    }

    override val databases: PluginDatabases = object : PluginDatabases {
        override fun <T : PluginDatabase> open(name: String, factory: PluginDatabaseFactory<T>): T {
            val opening = synchronized(lock) {
                requireOpen()
                requireSingleName(name)
                openDatabases[name]?.let { existing ->
                    require(existing.factory === factory) { "Database $name already has a different factory" }
                    @Suppress("UNCHECKED_CAST")
                    return existing.database as T
                }
                check(name !in openingDatabases) { "Database $name is already being opened" }
                DatabaseOpening().also { openingDatabases[name] = it }
            }
            val previousMarker = activeScope.get()
            activeScope.set(true)
            try {
                val path = safePath(dataStorageRoot(), "databases/$name")
                Files.createDirectories(path.parent)
                safePath(dataStorageRoot(), "databases/$name")
                // Plugin code can call other context services while constructing a database.
                val database = factory.open(path.toString())
                synchronized(lock) {
                    if (!closed) {
                        openDatabases[name] = DatabaseEntry(factory, database)
                        return database
                    }
                }
                val failure = IllegalStateException("WidgetContext for $pluginId closed while opening $name")
                try {
                    database.close()
                } catch (error: Throwable) {
                    opening.cleanupFailure = error
                    failure.addSuppressed(error)
                }
                throw failure
            } finally {
                synchronized(lock) { openingDatabases.remove(name) }
                opening.finished.countDown()
                if (previousMarker == null) activeScope.remove() else activeScope.set(previousMarker)
            }
        }
    }

    override val permissions: PluginPermissions = object : PluginPermissions {
        override suspend fun check(permission: String): PermissionResult = withinLifetime {
            permissionBridge.check(permission)
        }

        override suspend fun request(permission: String): PermissionResult = withinLifetime {
            permissionBridge.request(permission)
        }
    }

    /** Synchronous unload cannot join the task or database factory currently calling it. */
    internal fun checkCanClose() {
        check(activeScope.get() != true) { "Unload $pluginId from the host, outside its scope or database factory" }
    }

    /** Cancels all plugin work and waits for DataStore to release its file before returning. */
    fun close() {
        checkCanClose()
        synchronized(closeLock) { closeResources() }
    }

    private fun closeResources() {
        val resources = synchronized(lock) snapshot@ {
            if (closed) return@snapshot null
            closed = true
            ClosingResources(client, openDatabases.values.map { it.database }, openingDatabases.values.toList())
                .also { openDatabases.clear() }
        } ?: return
        var failure: Throwable? = null
        fun cleanup(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                val previous = failure
                if (previous == null) failure = error else if (previous !== error) previous.addSuppressed(error)
            }
        }
        cleanup { lifetime.cancel() }
        cleanup { resources.client?.coroutineContext?.cancel() }
        cleanup { resources.client?.close() }
        cleanup {
            runBlocking {
                lifetime.join()
                resources.client?.coroutineContext?.get(Job)?.join()
            }
        }
        resources.openings.forEach { opening ->
            cleanup { opening.finished.await() }
            opening.cleanupFailure?.let { error -> cleanup { throw error } }
        }
        resources.databases.forEach { database -> cleanup { database.close() } }
        failure?.let { throw it }
    }

    private suspend fun <T> withinLifetime(block: suspend () -> T): T {
        val operation = synchronized(lock) {
            requireOpen()
            scope.async { block() }
        }
        return try {
            operation.await()
        } finally {
            // await alone does not cancel a deferred when the caller is cancelled.
            operation.cancel()
        }
    }

    private fun preferenceStore(): DataStore<Preferences> = synchronized(lock) {
        requireOpen()
        val path = safePath(dataStorageRoot(), "preferences.preferences_pb")
        store ?: PreferenceDataStoreFactory.create(scope = scope) { path.toFile() }.also { store = it }
    }

    private fun filePath(path: String, area: PluginFileArea, allowEmpty: Boolean = false): Path {
        val root = when (area) {
            PluginFileArea.DATA -> safePath(dataStorageRoot(), "files")
            PluginFileArea.CACHE -> cacheStorageRoot()
            PluginFileArea.RESOURCES -> checkNotNull(resourcesRoot) { "Plugin does not declare resources" }
        }
        if (area == PluginFileArea.RESOURCES) {
            safePath(pluginRoot, pluginRoot.relativize(root).toString().replace('\\', '/'), allowEmpty = true)
        }
        return safePath(root, path, allowEmpty)
    }

    private fun requireOpen() = check(!closed) { "WidgetContext for $pluginId is closed" }

    private fun dataStorageRoot(): Path = safePath(runtimeRoot, "${Configure.PLUGINS_DIR}/$pluginId/Data")

    private fun cacheStorageRoot(): Path = safePath(runtimeRoot, "${Configure.PLUGINS_DIR}/$pluginId/Cache")

    private fun requireWritable(area: PluginFileArea) =
        require(area != PluginFileArea.RESOURCES) { "Plugin resources are read-only" }

    private data class DatabaseEntry(val factory: PluginDatabaseFactory<*>, val database: PluginDatabase)

    private class DatabaseOpening {
        val finished = CountDownLatch(1)
        @Volatile var cleanupFailure: Throwable? = null
    }

    private data class ClosingResources(
        val client: HttpClient?,
        val databases: List<PluginDatabase>,
        val openings: List<DatabaseOpening>,
    )
}

private fun requireSingleName(name: String) {
    require(name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == ':' }) {
        "Expected a single non-empty path name: $name"
    }
}

/** Rejects portable traversal and existing symlinks; native plugin code is still trusted in-process. */
private fun safePath(root: Path, path: String, allowEmpty: Boolean = false): Path {
    require((allowEmpty || path.isNotEmpty()) && '\\' !in path && ':' !in path) { "Invalid relative path: $path" }
    val relative = Path.of(path)
    require(!relative.isAbsolute && relative.none { it.toString() == ".." }) { "Invalid relative path: $path" }
    val target = root.resolve(relative).normalize()
    require(target.startsWith(root)) { "Path escapes plugin directory: $path" }
    require(!Files.isSymbolicLink(root)) { "Symbolic links are not supported: $root" }
    var current = root
    for (part in root.relativize(target)) {
        current = current.resolve(part)
        require(!Files.isSymbolicLink(current)) { "Symbolic links are not supported: $current" }
    }
    return target
}
