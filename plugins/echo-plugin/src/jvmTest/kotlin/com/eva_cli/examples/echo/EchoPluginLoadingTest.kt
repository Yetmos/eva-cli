package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.Widget
import com.eva_cli.core.widgets.context.PluginDatabases
import com.eva_cli.core.widgets.context.PluginFiles
import com.eva_cli.core.widgets.context.PluginNetwork
import com.eva_cli.core.widgets.context.PluginPreferences
import com.eva_cli.core.widgets.context.UnavailablePluginPermissions
import com.eva_cli.core.widgets.context.WidgetContext
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.plug.PluginEntry
import com.eva_cli.core.widgets.plug.PluginManifest
import com.eva_cli.core.widgets.provider.Provider
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertEquals(4, manifest.apiVersion)
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
            assertSame(WidgetContext::class.java, loader.loadClass(WidgetContext::class.java.name))
            assertSame(PluginNetwork::class.java, loader.loadClass(PluginNetwork::class.java.name))
            val entryClass = loader.loadClass(jvm.entryClass).asSubclass(PluginEntry::class.java)
            assertSame(loader, entryClass.classLoader)
            assertEquals(artifact.canonicalFile, File(entryClass.protectionDomain.codeSource.location.toURI()).canonicalFile)
            val entry = entryClass.getConstructor().newInstance()
            assertEquals(1, entry.dispatchers.size)
            val echo = entry.dispatchers.single { it.topic == "/Echo" }
            assertSame(loader, echo.javaClass.classLoader)
            assertEquals("/Echo", echo.topic)
            val provider = entry.providers.single()
            assertSame(loader, provider.javaClass.classLoader)
            assertEquals("/Echo", provider.topic)
            val widget: Widget = provider

            assertFailsWith<IllegalStateException> { widget.context }
            val pluginScope = CoroutineScope(currentCoroutineContext())
            val context = object : WidgetContext {
                override val pluginId = manifest.id
                override val scope = pluginScope
                override val permissions = UnavailablePluginPermissions
                override val network: PluginNetwork get() = error("Echo does not use networking")
                override val preferences: PluginPreferences get() = error("Echo does not use preferences")
                override val databases: PluginDatabases get() = error("Echo does not use databases")
                override val files: PluginFiles get() = error("Echo does not use files")
            }
            widget.attach(context)
            echo.attach(context)
            assertSame(context, widget.context)
            assertSame(widget.context, echo.context)
            assertEquals(manifest.id, widget.context.pluginId)

            val replies = mutableListOf<Event>()
            echo.providers[provider.id] = provider
            echo.emit = { replies += it }
            widget.emit = echo::receive
            widget.start()
            try {
                echo.dispatch(TestEvent(Event.Payload.Text("hello")))
                assertEquals(Event.Payload.Text("Echo: hello"), replies.single().payload)
                assertEquals(widget.id, replies.single().metadata.providerId)
            } finally {
                widget.stop()
                widget.emit = null
                echo.emit = null
            }
            assertEquals(Provider.Status.STOP, provider.status)
        }
    }
}
