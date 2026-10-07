package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.plug.PluginEntry
import com.eva_cli.core.widgets.plug.PluginManifest
import com.eva_cli.core.widgets.provider.Provider
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EchoPluginLoadingTest {
    @Test
    fun loadsTheManifestEntryFromTheBuiltJarAndExchangesHostEvents() = runBlocking {
        assertPluginCanExchangeEvents(File(checkNotNull(System.getProperty("eva.echoPlugin.directory"))))
    }

    @Test
    fun loadsTheReleaseArtifactAndExchangesHostEvents() = runBlocking {
        assertPluginCanExchangeEvents(File(checkNotNull(System.getProperty("eva.echoPlugin.stagingDirectory"))))
    }

    private suspend fun assertPluginCanExchangeEvents(directory: File) {
        val manifest = Json.decodeFromString<PluginManifest>(directory.resolve("plugin.json").readText())
        assertEquals(1, manifest.formatVersion)
        assertEquals(1, manifest.apiVersion)
        assertEquals(System.getProperty("eva.echoPlugin.version"), manifest.version)
        val jvm = assertNotNull(manifest.platforms.jvm)
        assertTrue(Runtime.version().feature() >= assertNotNull(jvm.minJavaVersion))
        val artifact = directory.resolve(jvm.artifact)
        assertTrue(artifact.isFile, "Missing plugin artifact: $artifact")

        JarFile(artifact).use { jar ->
            val classNames = jar.entries().asSequence().map { it.name }.filter { it.endsWith(".class") }.toList()
            assertTrue(classNames.isNotEmpty())
            assertTrue(classNames.all { it.startsWith("com/eva_cli/examples/echo/") },
                "The plugin JAR must contain only plugin classes; the host supplies the API and runtime")
        }

        // jvmTest 自身的 classpath 包含示例类，屏蔽父级中的这些类，确保真实地从 JAR 加载。
        val hostLoader = object : ClassLoader(PluginEntry::class.java.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name.startsWith("com.eva_cli.examples.echo.")) throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        URLClassLoader(arrayOf(artifact.toURI().toURL()), hostLoader).use { loader ->
            val entryClass = loader.loadClass(jvm.entryClass).asSubclass(PluginEntry::class.java)
            assertSame(loader, entryClass.classLoader)
            assertEquals(artifact.canonicalFile, File(entryClass.protectionDomain.codeSource.location.toURI()).canonicalFile)
            val entry = entryClass.getConstructor().newInstance()
            val root = entry.dispatchers.single()
            assertSame(loader, root.javaClass.classLoader)
            assertEquals("/", root.topic)
            val provider = entry.providers.single()
            assertSame(loader, provider.javaClass.classLoader)
            assertEquals("/Echo", provider.topic)

            val replies = mutableListOf<Event>()
            provider.emit = { replies += it }
            provider.start()
            try {
                provider.receive(TestEvent(Event.Payload.Text("hello")))
                assertEquals(Event.Payload.Text("Echo: hello"), replies.single().payload)
                assertEquals(provider.id, replies.single().metadata.providerId)
            } finally {
                provider.stop()
                provider.emit = null
            }
            assertEquals(Provider.Status.STOP, provider.status)
        }
    }
}
