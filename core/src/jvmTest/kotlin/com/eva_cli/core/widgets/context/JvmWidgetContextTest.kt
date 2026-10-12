package com.eva_cli.core.widgets.context

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JvmWidgetContextTest {
    @Test
    fun preferencesPersistAcrossReloadAndAreIsolatedByPlugin() = withRuntime { root, plugin ->
        runBlocking {
            val first = context(root, plugin, "first")
            val second = context(root, plugin, "second")
            try {
                first.preferences.put("greeting", "hello")
                first.preferences.put("removed", "value")
                first.preferences.remove("removed")
                assertNull(first.preferences.get("removed"))
                assertEquals(mapOf("greeting" to "hello"), first.preferences.values.first())
                assertNull(second.preferences.get("greeting"))
                second.preferences.put("greeting", "world")
            } finally {
                first.close()
                second.close()
            }
            val reloaded = context(root, plugin, "first")
            try {
                assertEquals("hello", reloaded.preferences.get("greeting"))
            } finally {
                reloaded.close()
            }
        }
    }

    @Test
    fun filesArePersistentAndIsolatedAndResourcesAreReadOnly() = withRuntime { root, plugin ->
        Files.createDirectories(plugin.resolve("resources"))
        Files.writeString(plugin.resolve("resources/greeting.txt"), "packaged")
        runBlocking {
            val first = context(root, plugin, "first", "resources")
            val second = context(root, plugin, "second")
            try {
                assertEquals(emptyList(), first.files.list())
                first.files.write("nested/note.txt", "data".encodeToByteArray())
                first.files.write("note.txt", "cache".encodeToByteArray(), PluginFileArea.CACHE)
                assertEquals(listOf("nested"), first.files.list())
                assertEquals(listOf("note.txt"), first.files.list("nested"))
                assertContentEquals("data".encodeToByteArray(), first.files.read("nested/note.txt"))
                assertContentEquals("cache".encodeToByteArray(), first.files.read("note.txt", PluginFileArea.CACHE))
                assertEquals(emptyList(), second.files.list())
                assertContentEquals("packaged".encodeToByteArray(), first.files.read("greeting.txt", PluginFileArea.RESOURCES))
                assertFailsWith<IllegalArgumentException> {
                    first.files.write("greeting.txt", byteArrayOf(), PluginFileArea.RESOURCES)
                }
                assertFailsWith<IllegalArgumentException> {
                    first.files.delete("greeting.txt", PluginFileArea.RESOURCES)
                }
                assertFailsWith<IllegalStateException> { second.files.list(area = PluginFileArea.RESOURCES) }
                assertTrue(first.files.delete("note.txt", PluginFileArea.CACHE))
                assertFalse(first.files.delete("note.txt", PluginFileArea.CACHE))
            } finally {
                first.close()
                second.close()
            }
            val reloaded = context(root, plugin, "first")
            try {
                assertContentEquals("data".encodeToByteArray(), reloaded.files.read("nested/note.txt"))
            } finally {
                reloaded.close()
            }
        }
    }

    @Test
    fun rejectsTraversalAndSymbolicLinksInEveryStorageArea() = withRuntime { root, plugin ->
        Files.createDirectories(plugin.resolve("resources"))
        val host = context(root, plugin, resources = "resources")
        try {
            runBlocking {
                for (path in listOf("../outside", "nested/../../outside", "/absolute", "C:\\outside", "nested\\outside")) {
                    for (area in PluginFileArea.entries) {
                        assertFailsWith<IllegalArgumentException>("$area: $path") { host.files.read(path, area) }
                        assertFailsWith<IllegalArgumentException>("$area: $path") { host.files.list(path, area) }
                    }
                    assertFailsWith<IllegalArgumentException> { host.files.write(path, byteArrayOf()) }
                    assertFailsWith<IllegalArgumentException> { host.files.delete(path) }
                }
                val outside = Files.createDirectories(root.resolve("outside"))
                Files.writeString(outside.resolve("secret"), "private")
                for ((area, directory) in listOf(
                    PluginFileArea.DATA to root.resolve("Plugins/test/Data/files"),
                    PluginFileArea.CACHE to root.resolve("Plugins/test/Cache"),
                    PluginFileArea.RESOURCES to plugin.resolve("resources"),
                )) {
                    Files.createDirectories(directory)
                    Files.createSymbolicLink(directory.resolve("link"), outside)
                    assertFailsWith<IllegalArgumentException> { host.files.read("link/secret", area) }
                    assertFailsWith<IllegalArgumentException> { host.files.list("link", area) }
                    assertFailsWith<IllegalArgumentException> { host.files.write("link/secret", byteArrayOf(), area) }
                    assertFailsWith<IllegalArgumentException> { host.files.delete("link/secret", area) }
                }
                assertEquals("private", Files.readString(outside.resolve("secret")))
            }
        } finally {
            host.close()
        }
        assertFailsWith<IllegalArgumentException> { context(root, plugin, "../outside") }
        assertFailsWith<IllegalArgumentException> { context(root, plugin, resources = "../outside") }
        Files.createSymbolicLink(plugin.resolve("linked-resources"), root.resolve("outside"))
        assertFailsWith<IllegalArgumentException> { context(root, plugin, resources = "linked-resources") }
    }

    @Test
    fun rejectsSymlinkedPreferenceAndDatabaseDirectories() = withRuntime { root, plugin ->
        val outside = Files.createDirectory(root.resolve("outside"))
        Files.createDirectories(root.resolve("Plugins/test"))
        Files.createSymbolicLink(root.resolve("Plugins/test/Data"), outside)
        val host = context(root, plugin)
        try {
            runBlocking {
                assertFailsWith<IllegalArgumentException> { host.preferences.put("key", "value") }
                assertFailsWith<IllegalArgumentException> { host.files.write("file", byteArrayOf()) }
            }
            assertFailsWith<IllegalArgumentException> {
                host.databases.open("test.db") { error("Factory must not run") }
            }
            assertEquals(0L, Files.list(outside).use { it.count() })
        } finally {
            host.close()
        }
    }

    @Test
    fun databasesReuseHandlesRejectConflictingFactoriesAndCloseDespiteFailures() = withRuntime { root, plugin ->
        val host = context(root, plugin)
        val paths = mutableListOf<String>()
        val closes = mutableListOf<String>()
        val failure = IllegalStateException("first close failed")
        val firstFactory = PluginDatabaseFactory {
            paths += it
            object : PluginDatabase {
                override fun close() { closes += "first"; throw failure }
            }
        }
        val secondFactory = PluginDatabaseFactory {
            paths += it
            object : PluginDatabase {
                override fun close() { closes += "second"; error("second close failed") }
            }
        }
        val first = host.databases.open("first.db", firstFactory)
        assertSame(first, host.databases.open("first.db", firstFactory))
        host.databases.open("second.db", secondFactory)
        assertFailsWith<IllegalArgumentException> { host.databases.open("first.db", secondFactory) }
        assertFailsWith<IllegalArgumentException> { host.databases.open("../outside", firstFactory) }
        assertEquals(root.toRealPath().resolve("Plugins/test/Data/databases/first.db").toString(), paths.first())
        assertNotEquals(paths.first(), paths.last())
        assertSame(failure, assertFailsWith<IllegalStateException> { host.close() })
        assertEquals(listOf("first", "second"), closes)
        assertEquals("second close failed", failure.suppressed.single().message)
        assertTrue(host.scope.coroutineContext[Job]!!.isCompleted)
        host.close()
        assertEquals(2, closes.size)
        assertFailsWith<IllegalStateException> { host.databases.open("first.db", firstFactory) }
    }

    @Test
    fun networkPreservesRequestAndResponseAndReturnsHttpErrorBodies() = withRuntime { root, plugin ->
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo") { exchange ->
            val body = "${exchange.requestMethod}:${exchange.requestHeaders.getFirst("X-Test")}:".encodeToByteArray() +
                exchange.requestBody.readAllBytes()
            exchange.responseHeaders.add("X-Reply", "present")
            exchange.sendResponseHeaders(418, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val host = context(root, plugin)
        try {
            runBlocking {
                val response = host.network.execute(NetworkRequest(
                    url = "http://127.0.0.1:${server.address.port}/echo",
                    method = "POST",
                    headers = mapOf("X-Test" to "header"),
                    body = "payload".encodeToByteArray(),
                ))
                assertEquals(418, response.status)
                assertContentEquals("POST:header:payload".encodeToByteArray(), response.body)
                assertEquals(listOf("present"), response.headers.entries.single { it.key.equals("X-Reply", true) }.value)
            }
        } finally {
            host.close()
            server.stop(0)
        }
    }

    @Test
    fun networkPoliciesLimitBodiesValidateRequestsAndClassifyHttpErrors() = withRuntime { root, plugin ->
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/large") { exchange ->
            val body = "123456".encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/error") { exchange ->
            val body = "bad".encodeToByteArray()
            exchange.sendResponseHeaders(400, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val host = context(
            root,
            plugin,
            networkPolicy = PluginNetworkPolicy(
                maxRequestBytes = 2,
                maxResponseBytes = 3,
                allowPrivateAddresses = true,
            ),
        )
        val base = "http://127.0.0.1:${server.address.port}"
        try {
            runBlocking {
                assertFailsWith<NetworkRequestTooLargeException> {
                    host.network.execute(NetworkRequest("$base/large", body = byteArrayOf(1, 2, 3)))
                }
                assertFailsWith<NetworkResponseTooLargeException> {
                    host.network.execute(NetworkRequest("$base/large"))
                }
                val failure = assertFailsWith<NetworkHttpException> {
                    host.network.execute(NetworkRequest("$base/error", failOnHttpError = true))
                }
                assertEquals(400, failure.status)
                assertContentEquals("bad".encodeToByteArray(), failure.responseBody)
                assertFailsWith<NetworkPolicyException> {
                    host.network.execute(NetworkRequest("ftp://example.test/file"))
                }
                assertFailsWith<NetworkPolicyException> {
                    host.network.execute(NetworkRequest("$base/large", allowInsecureHttp = false))
                }
                assertFailsWith<NetworkPolicyException> {
                    host.network.execute(NetworkRequest("$base/large", allowedHosts = setOf("example.test")))
                }
                assertFailsWith<NetworkPolicyException> {
                    host.network.execute(NetworkRequest("$base/large", allowPrivateAddresses = false))
                }
                assertFailsWith<NetworkPolicyException> {
                    host.network.execute(NetworkRequest("$base/large", headers = mapOf("Bad Header" to "x")))
                }
            }
        } finally {
            host.close()
            server.stop(0)
        }
    }

    @Test
    fun networkDownloadStreamsAndReportsMetrics() = withRuntime { root, plugin ->
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/stream") { exchange ->
            val body = "streamed-response".encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val host = context(root, plugin, networkPolicy = PluginNetworkPolicy(allowPrivateAddresses = true))
        try {
            runBlocking {
                val chunks = mutableListOf<ByteArray>()
                val response = host.network.download(
                    NetworkRequest("http://127.0.0.1:${server.address.port}/stream"),
                ) { chunks += it }
                assertEquals(200, response.status)
                assertTrue(response.body.isEmpty())
                assertContentEquals("streamed-response".encodeToByteArray(), chunks.fold(byteArrayOf()) { a, b -> a + b })
                assertEquals(1, host.network.metrics.totalRequests)
                assertTrue(host.network.metrics.bytesReceived >= "streamed-response".length)
            }
        } finally {
            host.close()
            server.stop(0)
        }
    }

    @Test
    fun networkRejectsCrossHostRedirectWhenPolicyDisallowsIt() = withRuntime { root, plugin ->
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "http://localhost:${server.address.port}/target")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/target") { exchange ->
            val body = "target".encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val host = context(
            root,
            plugin,
            networkPolicy = PluginNetworkPolicy(allowCrossHostRedirects = false, allowPrivateAddresses = true),
        )
        try {
            runBlocking {
                assertFailsWith<NetworkPolicyException> {
                    host.network.execute(NetworkRequest("http://127.0.0.1:${server.address.port}/redirect"))
                }
            }
        } finally {
            host.close()
            server.stop(0)
        }
    }

    @Test
    fun networkStripsCredentialsWhenRedirectChangesPort() = withRuntime { root, plugin ->
        val target = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        target.createContext("/target") { exchange ->
            val body = (exchange.requestHeaders.getFirst("Authorization") ?: "missing").encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        target.start()
        val redirect = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        redirect.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${target.address.port}/target")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        redirect.start()
        val host = context(
            root,
            plugin,
            networkPolicy = PluginNetworkPolicy(
                allowPrivateAddresses = true,
                preserveSensitiveHeadersOnRedirect = true,
            ),
        )
        try {
            runBlocking {
                val response = host.network.execute(
                    NetworkRequest(
                        "http://127.0.0.1:${redirect.address.port}/redirect",
                        headers = mapOf("Authorization" to "secret"),
                        preserveSensitiveHeadersOnRedirect = true,
                    ),
                )
                assertContentEquals("missing".encodeToByteArray(), response.body)
            }
        } finally {
            host.close()
            redirect.stop(0)
            target.stop(0)
        }
    }

    @Test
    fun networkAppliesTimeoutAndByteRateLimit() = withRuntime { root, plugin ->
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/slow") { exchange ->
            Thread.sleep(250)
            val body = "slow".encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/quota") { exchange ->
            val body = "1234".encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val host = context(
            root,
            plugin,
            networkPolicy = PluginNetworkPolicy(maxBytesPerMinute = 3, allowPrivateAddresses = true),
        )
        val base = "http://127.0.0.1:${server.address.port}"
        try {
            runBlocking {
                assertFailsWith<NetworkTimeoutException> {
                    host.network.execute(NetworkRequest("$base/slow", requestTimeoutMillis = 50))
                }
                assertFailsWith<NetworkPolicyException> {
                    host.network.execute(NetworkRequest("$base/quota"))
                }.also { assertEquals(NetworkPolicyViolation.BYTE_RATE_LIMIT, it.violation) }
            }
        } finally {
            host.close()
            server.stop(0)
        }
    }

    @Test
    fun invalidRequestDoesNotConsumeRequestRateQuota() = withRuntime { root, plugin ->
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { exchange ->
            val body = "ok".encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val host = context(
            root,
            plugin,
            networkPolicy = PluginNetworkPolicy(
                maxRequestsPerMinute = 1,
                allowPrivateAddresses = true,
            ),
        )
        try {
            runBlocking {
                assertFailsWith<NetworkPolicyException> {
                    host.network.execute(NetworkRequest("ftp://example.test/file"))
                }
                val response = withTimeout(2_000) {
                    host.network.execute(NetworkRequest("http://127.0.0.1:${server.address.port}/ok"))
                }
                assertEquals(200, response.status)
            }
        } finally {
            host.close()
            server.stop(0)
        }
    }

    @Test
    fun redirectHopsConsumeRequestRateQuota() = withRuntime { root, plugin ->
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/target")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/target") { exchange ->
            val body = "target".encodeToByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val host = context(
            root,
            plugin,
            networkPolicy = PluginNetworkPolicy(
                maxRequestsPerMinute = 1,
                allowPrivateAddresses = true,
            ),
        )
        try {
            runBlocking {
                assertFailsWith<NetworkTimeoutException> {
                    host.network.execute(
                        NetworkRequest(
                            "http://127.0.0.1:${server.address.port}/redirect",
                            requestTimeoutMillis = 100,
                        ),
                    )
                }
                assertEquals(1, host.network.metrics.totalRequests)
            }
        } finally {
            host.close()
            server.stop(0)
        }
    }

    @Test
    fun closeCancelsPluginWorkAndAllServicesRejectFurtherUse() = withRuntime { root, plugin ->
        val host = context(root, plugin)
        val cancelled = CompletableDeferred<Unit>()
        host.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        host.close()
        assertTrue(cancelled.isCompleted)
        runBlocking {
            assertFailsWith<IllegalStateException> { host.files.list() }
            assertFailsWith<IllegalStateException> { host.preferences.get("key") }
            assertFailsWith<IllegalStateException> { host.preferences.values.first() }
            assertFailsWith<IllegalStateException> { host.network.execute(NetworkRequest("http://localhost")) }
            assertFailsWith<IllegalStateException> { host.permissions.request("test") }
        }
    }

    @Test
    fun rejectsClosingFromItsOwnScopeBeforeCancellingAnything() = withRuntime { root, plugin ->
        val host = context(root, plugin)
        runBlocking {
            withTimeout(5_000) {
                host.scope.async {
                    assertFailsWith<IllegalStateException> { host.checkCanClose() }
                    assertFailsWith<IllegalStateException> { host.close() }
                }.await()
            }
            host.preferences.put("still", "active")
            assertEquals("active", host.preferences.get("still"))
        }
        host.close()
    }

    @Test
    fun databaseFactoriesCanReadPreferencesAndRejectRecursiveOpenAndSelfClose() = withRuntime { root, plugin ->
        val host = context(root, plugin)
        var closed = false
        val factory = PluginDatabaseFactory {
            assertEquals("value", runBlocking { host.preferences.get("key") })
            assertFailsWith<IllegalStateException> { host.close() }
            assertFailsWith<IllegalStateException> {
                host.databases.open("test.db") { error("Recursive factory must not run") }
            }
            object : PluginDatabase {
                override fun close() { closed = true }
            }
        }
        runBlocking { host.preferences.put("key", "value") }
        assertFailsWith<IllegalArgumentException> {
            host.databases.open("test.db") { throw IllegalArgumentException("first open failed") }
        }
        host.databases.open("test.db", factory)
        host.close()
        assertTrue(closed)
    }

    @Test
    fun closeWaitsForAnOpeningDatabaseAndClosesItsLateHandle() = withRuntime { root, plugin ->
        val host = context(root, plugin)
        val enteredFactory = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        val closing = CountDownLatch(1)
        val closes = AtomicInteger()
        val executor = Executors.newFixedThreadPool(2)
        host.scope.coroutineContext[Job]!!.invokeOnCompletion { closing.countDown() }
        try {
            val opening = executor.submit<PluginDatabase> {
                host.databases.open("late.db") {
                    enteredFactory.countDown()
                    check(releaseFactory.await(5, TimeUnit.SECONDS))
                    object : PluginDatabase {
                        override fun close() {
                            assertFailsWith<IllegalStateException> { host.close() }
                            closes.incrementAndGet()
                        }
                    }
                }
            }
            assertTrue(enteredFactory.await(5, TimeUnit.SECONDS))
            val close = executor.submit { host.close() }
            assertTrue(closing.await(5, TimeUnit.SECONDS))
            assertFalse(close.isDone)
            releaseFactory.countDown()
            val failure = assertFailsWith<ExecutionException> { opening.get(5, TimeUnit.SECONDS) }
            assertTrue(failure.cause is IllegalStateException)
            close.get(5, TimeUnit.SECONDS)
            assertEquals(1, closes.get())
        } finally {
            releaseFactory.countDown()
            executor.shutdownNow()
            host.close()
        }
    }

    @Test
    fun permissionBridgeReceivesCallsAndCallerCancellationCancelsItsWork() = withRuntime { root, plugin ->
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val permissions = object : PluginPermissions {
            override suspend fun check(permission: String) =
                if (permission == "granted") PermissionResult.GRANTED else PermissionResult.DENIED

            override suspend fun request(permission: String): PermissionResult {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        }
        val host = JvmWidgetContext("test", root, plugin, null, permissions)
        try {
            runBlocking {
                assertEquals(PermissionResult.GRANTED, host.permissions.check("granted"))
                assertEquals(PermissionResult.DENIED, host.permissions.check("denied"))
                val request = async { host.permissions.request("pending") }
                withTimeout(5_000) { started.await() }
                request.cancelAndJoin()
                withTimeout(5_000) { cancelled.await() }
            }
        } finally {
            host.close()
        }
    }

    private fun context(
        root: Path,
        plugin: Path,
        id: String = "test",
        resources: String? = null,
        networkPolicy: PluginNetworkPolicy = PluginNetworkPolicy(),
    ) = JvmWidgetContext(id, root, plugin, resources, UnavailablePluginPermissions, networkPolicy)

    private fun withRuntime(block: (Path, Path) -> Unit) {
        val root = Files.createTempDirectory("eva-widget-context-")
        try {
            block(root, Files.createDirectory(root.resolve("plugin")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
