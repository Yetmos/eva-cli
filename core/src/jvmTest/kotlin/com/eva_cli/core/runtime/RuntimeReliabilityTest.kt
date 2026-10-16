package com.eva_cli.core.runtime

import com.eva_cli.core.widgets.context.WidgetContext
import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.plug.CURRENT_PLUGIN_API_VERSION
import com.eva_cli.core.widgets.plug.LoadingFixtureHooks
import com.eva_cli.core.widgets.plug.PluginEntry
import com.eva_cli.core.widgets.plug.PluginManifest
import com.eva_cli.core.widgets.plug.PlugManager
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.testplugins.BootDispatcher
import com.eva_cli.testplugins.BootEntry
import com.eva_cli.testplugins.QuietDispatcher
import com.eva_cli.testplugins.QuietEntry
import com.eva_cli.testplugins.QuietWorkerEntry
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import com.eva_cli.core.widgets.context.PluginDatabase
import com.eva_cli.core.widgets.context.PluginDatabaseFactory

/** 需要在 JVM 上加载真实插件的生命周期与清理场景。 */
class RuntimeReliabilityTest {

    @Test
    fun concurrentStartsAreSerializedAndOnlyOneRootStarts() = withRuntimeRoot { root ->
        writePlugin(root, entry = BootEntry::class.java)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        LoadingFixtureHooks.onLifecycle = { topic, action ->
            if (topic == "/" && action == "start") {
                entered.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS), "root start did not get released")
            }
        }

        runBlocking {
            val first = async(Dispatchers.Default) { Runtime.start() }
            assertTrue(entered.await(5, TimeUnit.SECONDS), "first start never reached the root")

            val second = async(Dispatchers.Default) { Runtime.start() }
            // 第二个调用方必须等待第一个生命周期操作完成。
            assertFalse(second.isCompleted)

            release.countDown()
            awaitAll(first, second)
            assertEquals(RuntimeStatus.RUNNING, Runtime.status)
        }
    }

    @Test
    fun concurrentStopsAreSerializedAndRepeatedStopIsSafe() = withRuntimeRoot { root ->
        writePlugin(root, entry = BootEntry::class.java)
        runBlocking { Runtime.start() }

        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        LoadingFixtureHooks.onLifecycle = { topic, action ->
            if (topic == "/" && action == "stop") {
                entered.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS), "root stop did not get released")
            }
        }

        runBlocking {
            val first = async(Dispatchers.Default) { Runtime.stop() }
            assertTrue(entered.await(5, TimeUnit.SECONDS), "first stop never reached the root")
            val second = async(Dispatchers.Default) { Runtime.stop() }
            assertFalse(second.isCompleted)

            release.countDown()
            awaitAll(first, second)
            assertEquals(RuntimeStatus.STOPPED, Runtime.status)
            assertTrue(PlugManager.loadedList.isEmpty())
        }
    }

    @Test
    fun failedStartCleansTheTreeAndPluginsAndCanBeRetried() = withRuntimeRoot { root ->
        writePlugin(root, entry = BootEntry::class.java)
        val failure = IllegalStateException("root start failed")
        LoadingFixtureHooks.onLifecycle = { topic, action ->
            if (topic == "/" && action == "start") throw failure
        }

        val thrown = assertFailsWith<IllegalStateException> {
            runBlocking { Runtime.start() }
        }
        assertEquals(failure, thrown)
        assertEquals(RuntimeStatus.STOPPED, Runtime.status)
        assertTrue(PlugManager.loadedList.isEmpty())

        LoadingFixtureHooks.onLifecycle = null
        runBlocking { Runtime.start() }
        assertEquals(RuntimeStatus.RUNNING, Runtime.status)
        runBlocking { Runtime.stop() }
    }

    @Test
    fun stopAttemptsEveryPluginWhenOneResourceReleaseFails() = withRuntimeRoot { root ->
        writePlugin(root, id = "com.example.root", entry = BootEntry::class.java)
        writePlugin(root, id = "com.example.failing", entry = QuietEntry::class.java)
        writePlugin(root, id = "com.example.worker", entry = QuietWorkerEntry::class.java)
        runBlocking { Runtime.start() }

        val failing = PlugManager.loadedList.single { it.manifest.id == "com.example.failing" }
        failing.context.databases.open("failure.db", PluginDatabaseFactory {
            object : PluginDatabase {
                override fun close() = error("database close failed")
            }
        })

        val thrown = assertFailsWith<IllegalStateException> {
            runBlocking { Runtime.stop() }
        }
        val failureMessages = sequenceOf(thrown, thrown.cause, *thrown.suppressed)
            .filterNotNull()
            .joinToString(" ") { it.message.orEmpty() }
        assertTrue(
            failureMessages.contains("database close failed"),
        )
        // 失败的 Context 与其他插件 Context 一样都会被移除。
        assertTrue(PlugManager.loadedList.isEmpty())
        runBlocking { Runtime.stop() }
    }

    @Test
    fun dispatchWhileStoppedIsRejected() {
        val event = HostEvent()
        runBlocking {
            assertFailsWith<IllegalStateException> { Booter.dispatch(event) }
        }
    }

    private fun withRuntimeRoot(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("eva-runtime-reliability")
        val previous = System.getProperty("user.dir")
        try {
            runBlocking { Runtime.stop() }
            PlugManager.loadedList.toList().forEach { plugin ->
                runCatching { PlugManager.unloadPlugin(plugin.manifest.id) }
            }
            System.setProperty("user.dir", root.toString())
            block(root)
        } finally {
            LoadingFixtureHooks.onConstruct = null
            LoadingFixtureHooks.onLifecycle = null
            runCatching { runBlocking { Runtime.stop() } }
            PlugManager.loadedList.toList().forEach { plugin ->
                runCatching { PlugManager.unloadPlugin(plugin.manifest.id) }
            }
            if (previous == null) System.clearProperty("user.dir") else System.setProperty("user.dir", previous)
            root.toFile().deleteRecursively()
        }
    }

    private fun writePlugin(
        root: Path,
        id: String = "com.example.runtime",
        version: String = VERSION,
        entry: Class<*> = BootEntry::class.java,
    ): Path {
        val directory = root.resolve("Plugins/$id/versions/$version")
        Files.createDirectories(directory)
        writeJar(directory.resolve("plugin.jar"), entry)
        val manifest = PluginManifest(
            formatVersion = 1,
            id = id,
            name = "Runtime reliability fixture",
            version = version,
            apiVersion = CURRENT_PLUGIN_API_VERSION,
            platforms = PluginManifest.Platforms(
                jvm = PluginManifest.Jvm("plugin.jar", entry.name),
            ),
        )
        Files.writeString(directory.resolve("plugin.json"), Json.encodeToString(manifest))
        return directory
    }

    private fun writeJar(path: Path, entry: Class<*>) {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        }
        JarOutputStream(Files.newOutputStream(path), manifest).use { jar ->
            val classes = listOf(
                entry,
                BootEntry::class.java,
                BootDispatcher::class.java,
                QuietEntry::class.java,
                QuietWorkerEntry::class.java,
                QuietDispatcher::class.java,
                PluginEntry::class.java,
                PluginProvider::class.java,
                PluginDispatcher::class.java,
                Event::class.java,
                WidgetContext::class.java,
            )
            val entries = linkedMapOf<String, ByteArray>()
            classes.forEach { type -> entries.putAll(compiledClasses(type)) }
            entries["loading-fixture.txt"] = "runtime reliability fixture".encodeToByteArray()
            for ((name, bytes) in entries) {
                jar.putNextEntry(JarEntry(name))
                jar.write(bytes)
                jar.closeEntry()
            }
        }
    }

    private fun compiledClasses(type: Class<*>): Map<String, ByteArray> {
        val resource = type.name.replace('.', '/') + ".class"
        val location = checkNotNull(type.classLoader.getResource(resource))
        val prefix = resource.removeSuffix(".class") + "$"
        if (location.protocol == "jar") {
            val connection = location.openConnection() as JarURLConnection
            connection.useCaches = false
            return connection.jarFile.use { jar ->
                jar.entries().asSequence()
                    .filter { it.name == resource || it.name.startsWith(prefix) && it.name.endsWith(".class") }
                    .associate { it.name to jar.getInputStream(it).use { stream -> stream.readBytes() } }
            }
        }
        check(location.protocol == "file") { "Unsupported fixture class location: $location" }
        val classFile = Path.of(location.toURI())
        val nestedPrefix = classFile.fileName.toString().removeSuffix(".class") + "$"
        return Files.list(classFile.parent).use { files ->
            files.filter { file ->
                file == classFile || file.fileName.toString().startsWith(nestedPrefix) && file.toString().endsWith(".class")
            }.toList().associate { resource.substringBeforeLast('/') + "/" + it.fileName to Files.readAllBytes(it) }
        }
    }

    private class HostEvent : Event {
        override val id = Uuid.random()
        override val metadata = Event.Metadata()
        override var records: List<EventRecord> = emptyList()
        override val payload = Event.Payload.Text("runtime reliability")
    }

    private companion object {
        const val VERSION = "1.0.0"
    }
}
