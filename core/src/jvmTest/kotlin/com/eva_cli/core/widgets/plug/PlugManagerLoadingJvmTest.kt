package com.eva_cli.core.widgets.plug

import com.eva_cli.core.runtime.Booter
import com.eva_cli.core.runtime.Plugin as RuntimePlugin
import com.eva_cli.core.widgets.dispatcher.BaseDispatcher
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.provider.Provider
import com.eva_cli.core.widgets.context.PermissionResult
import com.eva_cli.core.widgets.context.PluginFileArea
import com.eva_cli.core.widgets.context.PluginDatabase
import com.eva_cli.core.widgets.context.PluginDatabaseFactory
import com.eva_cli.core.widgets.context.PluginPermissions
import com.eva_cli.core.widgets.context.WidgetContext
import com.eva_cli.testplugins.BootDispatcher
import com.eva_cli.testplugins.BootEntry
import com.eva_cli.testplugins.ConstructorFailureEntry
import com.eva_cli.testplugins.DispatchersFailureEntry
import com.eva_cli.testplugins.DuplicateIdsEntry
import com.eva_cli.testplugins.DuplicateRootsEntry
import com.eva_cli.testplugins.HookEntry
import com.eva_cli.testplugins.LoadingDispatcher
import com.eva_cli.testplugins.LoadingEntry
import com.eva_cli.testplugins.LoadingProvider
import com.eva_cli.testplugins.NotAnEntry
import com.eva_cli.testplugins.OtherScanEntry
import com.eva_cli.testplugins.PrivateConstructorEntry
import com.eva_cli.testplugins.ProvidersFailureEntry
import com.eva_cli.testplugins.QueryEntry
import com.eva_cli.testplugins.RepeatedComponentsEntry
import com.eva_cli.testplugins.RequiredArgumentEntry
import com.eva_cli.testplugins.ScanEntry
import com.eva_cli.testplugins.dependencies.PrivateDependency
import java.net.JarURLConnection
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import javax.tools.ToolProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Shared through the host API class loader to exercise constructor reentrancy. */
object LoadingFixtureHooks {
    @Volatile
    var onConstruct: (() -> Unit)? = null

    var onLifecycle: ((String, String) -> Unit)? = null
}

class PlugManagerLoadingJvmTest {
    @Test
    fun runtimeDataBackupAndRestoreRequireTheAffectedPluginToBeUnloaded() = withRuntimeRoot { root ->
        writePlugin(root)
        val context = PlugManager.loadPlugin(ID, VERSION).context
        runBlocking { context.files.write("state.txt", "saved".encodeToByteArray()) }
        PlugManager.backup(includeRuntimeData = false)
        val snapshot = root.resolve("Backup/plugins.zip")
        val previous = Files.readAllBytes(snapshot)

        assertFailsWith<IllegalStateException> { PlugManager.backup(includeRuntimeData = true) }
        assertContentEquals(previous, Files.readAllBytes(snapshot))

        PlugManager.unloadPlugin(ID)
        PlugManager.backup(includeRuntimeData = true)
        val reloaded = PlugManager.loadPlugin(ID, VERSION)
        runBlocking { reloaded.context.files.write("state.txt", "current".encodeToByteArray()) }

        assertFailsWith<IllegalStateException> { PlugManager.restore() }
        assertSame(reloaded, PlugManager.loadedList.single())
        runBlocking { assertEquals("current", reloaded.context.files.read("state.txt").decodeToString()) }

        PlugManager.unloadPlugin(ID)
        PlugManager.restore()
        assertEquals("saved", Files.readString(root.resolve("Plugins/$ID/Data/files/state.txt")))
    }

    @Test
    fun unloadFromPluginScopeFailsWithoutRemovingOrClosingItsRegistration() = withRuntimeRoot { root ->
        writePlugin(root)
        val loaded = PlugManager.loadPlugin(ID, VERSION)
        runBlocking {
            withTimeout(5_000) {
                loaded.context.scope.async {
                    assertFailsWith<IllegalStateException> { PlugManager.unloadPlugin(ID) }
                }.await()
            }
        }
        assertSame(loaded, PlugManager.loadedList.single())
        assertTrue(loaded.context.scope.isActive)
        assertTrue(PlugManager.unloadPlugin(ID))
    }

    @Test
    fun loaderBindsOneContextPerPluginBeforeLifecycleAndRejectsRebinding() = withRuntimeRoot { root ->
        writePlugin(root)
        writePlugin(root, id = "com.example.other")
        val loaded = PlugManager.loadPlugin(ID, VERSION)
        val other = PlugManager.loadPlugin("com.example.other", VERSION)
        val provider = loaded.providers.single()

        assertEquals(ID, loaded.context.pluginId)
        assertSame(loaded.context, provider.context)
        assertSame(loaded.context, loaded.dispatchers.single().context)
        assertNotSame(loaded.context, other.context)
        provider.attach(loaded.context)
        assertFailsWith<IllegalStateException> { provider.attach(other.context) }
        assertSame(loaded.context, provider.context)
        assertSame(WidgetContext::class.java, loaded.entry.javaClass.classLoader.loadClass(WidgetContext::class.java.name))
    }

    @Test
    fun contextSurvivesReloadThroughPersistentStorageAndCancelsTasksOnUnload() = withRuntimeRoot { root ->
        writePlugin(root)
        val context = PlugManager.loadPlugin(ID, VERSION).context
        var taskFinished = false
        val task = context.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { taskFinished = true }
        }
        runBlocking {
            context.preferences.put("theme", "dark")
            context.files.write("nested/note.txt", "saved".encodeToByteArray())
        }

        assertTrue(PlugManager.unloadPlugin(ID))
        assertFalse(context.scope.isActive)
        assertTrue(task.isCompleted)
        assertTrue(taskFinished)
        val reloaded = PlugManager.loadPlugin(ID, VERSION).context
        assertNotSame(context, reloaded)
        runBlocking {
            assertEquals("dark", reloaded.preferences.get("theme"))
            assertEquals("saved", reloaded.files.read("nested/note.txt").decodeToString())
        }
    }

    @Test
    fun switchingVersionsAndUninstallingThemPreservesPluginStorage() = withRuntimeRoot { root ->
        val first = writePlugin(root)
        val second = writePlugin(root, version = "2.0.0")
        val original = PlugManager.loadPlugin(ID, VERSION).context
        runBlocking {
            original.preferences.put("theme", "dark")
            original.files.write("note.txt", "saved".encodeToByteArray())
            original.files.write("index.txt", "cached".encodeToByteArray(), PluginFileArea.CACHE)
        }
        PlugManager.unloadPlugin(ID)
        assertTrue(PlugManager.uninstallPlugin(ID, VERSION))

        val upgraded = PlugManager.loadPlugin(ID, "2.0.0").context
        runBlocking {
            assertEquals("dark", upgraded.preferences.get("theme"))
            assertEquals("saved", upgraded.files.read("note.txt").decodeToString())
            assertEquals("cached", upgraded.files.read("index.txt", PluginFileArea.CACHE).decodeToString())
        }
        PlugManager.unloadPlugin(ID)
        assertTrue(PlugManager.uninstallPlugin(ID, "2.0.0"))

        assertFalse(Files.exists(first))
        assertFalse(Files.exists(second))
        assertTrue(Files.isRegularFile(root.resolve("Plugins/$ID/Data/preferences.preferences_pb")))
        assertEquals("saved", Files.readString(root.resolve("Plugins/$ID/Data/files/note.txt")))
        assertEquals("cached", Files.readString(root.resolve("Plugins/$ID/Cache/index.txt")))
        assertFalse(Files.exists(root.resolve("Data/$ID")))
        assertFalse(Files.exists(root.resolve("Cache/$ID")))
    }

    @Test
    fun databaseCloseFailureStillClosesLoaderAndAllowsReload() = withRuntimeRoot { root ->
        writePlugin(root)
        val loaded = PlugManager.loadPlugin(ID, VERSION)
        val loader = assertIs<URLClassLoader>(loaded.entry.javaClass.classLoader)
        val closed = mutableListOf<String>()
        for (name in listOf("first.db", "second.db")) {
            loaded.context.databases.open(name, PluginDatabaseFactory {
                object : PluginDatabase {
                    override fun close() {
                        closed.add(name)
                        error("close $name")
                    }
                }
            })
        }

        assertFailsWith<IllegalStateException> { PlugManager.unloadPlugin(ID) }
        assertEquals(setOf("first.db", "second.db"), closed.toSet())
        assertNull(loader.findResource("loading-fixture.txt"))
        assertTrue(PlugManager.loadedList.isEmpty())
        assertFalse(loaded.context.scope.isActive)
        assertNotSame(loaded.context, PlugManager.loadPlugin(ID, VERSION).context)
    }

    @Test
    fun loadedContextUsesTheCurrentPermissionHostAfterUiReplacement() = withRuntimeRoot { root ->
        writePlugin(root)
        val context = PlugManager.loadPlugin(ID, VERSION).context
        fun host(result: PermissionResult) = object : PluginPermissions {
            override suspend fun check(permission: String) = result
            override suspend fun request(permission: String) = result
        }
        val previous = PlugManager.permissions
        try {
            runBlocking {
                PlugManager.permissions = host(PermissionResult.DENIED)
                assertEquals(PermissionResult.DENIED, context.permissions.check("camera"))
                PlugManager.permissions = host(PermissionResult.GRANTED)
                assertEquals(PermissionResult.GRANTED, context.permissions.request("camera"))
            }
        } finally {
            PlugManager.permissions = previous
        }
    }

    @Test
    fun booterStartsTheLoadedRootAndDelegatesLifecycleToItsTree() = withRuntimeRoot { root ->
        writePlugin(root, entry = BootEntry::class.java)
        val loaded = PlugManager.loadPlugin(ID, VERSION)
        val child = loaded.dispatchers[0]
        val dispatcher = loaded.dispatchers[1]
        val events = mutableListOf<String>()
        LoadingFixtureHooks.onLifecycle = { topic, action -> events.add("$topic:$action") }
        assertSame(loaded.context, dispatcher.context)
        assertSame(loaded.context, child.context)

        runBlocking {
            Booter.start()
            assertEquals(listOf("/:restore", "/Child:restore", "/Child:start", "/:start"), events)
            assertSame(child, dispatcher.dispatchers.values.single())
            assertNotNull(child.emit)
            assertNull(dispatcher.emit)
            assertFailsWith<IllegalStateException> { Booter.start() }
            assertEquals(4, events.size)

            events.clear()
            Booter.backup()
            assertEquals(listOf("/:backup", "/Child:backup"), events)
            events.clear()
            Booter.restore()
            assertEquals(listOf("/:restore"), events)

            events.clear()
            Booter.stop()
            assertEquals(listOf("/:backup", "/Child:backup", "/Child:stop", "/:stop"), events)
            Booter.stop()
            assertEquals(4, events.size)
            assertFailsWith<IllegalStateException> { Booter.backup() }
            assertFailsWith<IllegalStateException> { Booter.restore() }

            events.clear()
            Booter.start()
            assertEquals(listOf("/:restore", "/Child:restore", "/Child:start", "/:start"), events)
        }
    }

    @Test
    fun booterRejectsMissingRootWithoutLoadingInstalledPlugins() = withRuntimeRoot { root ->
        writePlugin(root, entry = BootEntry::class.java)
        writePlugin(root, id = "com.example.other")
        val other = PlugManager.loadPlugin("com.example.other", VERSION)

        val failure = assertFailsWith<IllegalStateException> { runBlocking { Booter.start() } }

        assertTrue(failure.message.orEmpty().contains("实际找到 0 个"))
        assertEquals(listOf(other), PlugManager.loadedList)
        PlugManager.unloadPlugin("com.example.other")
        PlugManager.loadPlugin(ID, VERSION)
        runBlocking { Booter.start() }
    }

    @Test
    fun booterRejectsMultipleRootsBeforeStartingAnyComponents() {
        for (samePlugin in listOf(true, false)) {
            withRuntimeRoot { root ->
                writePlugin(root, entry = if (samePlugin) DuplicateRootsEntry::class.java else BootEntry::class.java)
                PlugManager.loadPlugin(ID, VERSION)
                if (!samePlugin) {
                    writePlugin(root, id = "com.example.other", entry = BootEntry::class.java)
                    PlugManager.loadPlugin("com.example.other", VERSION)
                }
                LoadingFixtureHooks.onLifecycle = { _, _ -> error("Conflicting roots must not be started") }

                val failure = assertFailsWith<IllegalStateException> { runBlocking { Booter.start() } }

                assertTrue(failure.message.orEmpty().contains("实际找到 2 个"))
            }
        }
    }

    @Test
    fun booterRetainsAFailedRootForCleanupAndAllowsRetryAfterStopping() = withRuntimeRoot { root ->
        writePlugin(root, entry = BootEntry::class.java)
        PlugManager.loadPlugin(ID, VERSION)
        val failure = IllegalStateException("Root start failed after starting its child")
        val events = mutableListOf<String>()
        LoadingFixtureHooks.onLifecycle = { topic, action ->
            events.add("$topic:$action")
            if (topic == "/" && action == "start") throw failure
        }

        runBlocking {
            assertSame(failure, assertFailsWith<IllegalStateException> { Booter.start() })
            assertEquals(listOf("/:restore", "/Child:restore", "/Child:start", "/:start"), events)
            assertFailsWith<IllegalStateException> { Booter.start() }
            assertEquals(4, events.size)
            events.clear()
            Booter.stop()
            assertEquals(listOf("/:backup", "/Child:backup", "/Child:stop", "/:stop"), events)

            LoadingFixtureHooks.onLifecycle = { topic, action -> events.add("$topic:$action") }
            events.clear()
            Booter.start()
            assertEquals(listOf("/:restore", "/Child:restore", "/Child:start", "/:start"), events)
        }
    }

    @Test
    fun runtimePluginStartWithNoInstallationsDoesNotCreatePluginDirectory() = withRuntimeRoot { root ->
        RuntimePlugin.start()

        assertTrue(PlugManager.loadedList.isEmpty())
        assertFalse(Files.exists(root.resolve("Plugins")))
    }

    @Test
    fun runtimePluginStartLoadsEverySingleVersionAndLeavesComponentsDetached() = withRuntimeRoot { root ->
        writePlugin(root, id = "com.example.b", entry = LoadingEntry::class.java)
        writePlugin(root, id = "com.example.a", entry = ScanEntry::class.java)

        RuntimePlugin.start()

        val loaded = PlugManager.loadedList
        assertEquals(listOf("com.example.a", "com.example.b"), loaded.map { it.manifest.id })
        assertEquals(listOf(loaded[0].providers[0], loaded[0].providers[4]), PlugManager.findProviders("/Room"))
        assertEquals(listOf(loaded[1].providers.single()), PlugManager.findProviders("/Loading"))
        assertEquals(
            listOf(loaded[0].dispatchers[1], loaded[0].dispatchers[2]),
            PlugManager.findChildDispatchers("/Room"),
        )
        loaded.flatMap { it.providers }.forEach {
            assertEquals(Provider.Status.STOP, it.status)
            assertNull(it.emit)
        }
        loaded.flatMap { it.dispatchers }.forEach { assertNull(it.emit) }
    }

    @Test
    fun runtimePluginStartIsIdempotentAndLoadsInstallationsAddedLater() = withRuntimeRoot { root ->
        writePlugin(root, id = "com.example.a")
        RuntimePlugin.start()
        val first = PlugManager.loadedList.single()

        RuntimePlugin.start()
        assertSame(first, PlugManager.loadedList.single())

        writePlugin(root, id = "com.example.b")
        RuntimePlugin.start()
        assertEquals(listOf("com.example.a", "com.example.b"), PlugManager.loadedList.map { it.manifest.id })
        assertSame(first, PlugManager.loadedList.first())
    }

    @Test
    fun runtimePluginStartRejectsMultipleVersionsBeforeLoadingAndPreservesExistingEntries() = withRuntimeRoot { root ->
        writePlugin(root, id = "com.example.loaded")
        val existing = PlugManager.loadPlugin("com.example.loaded", VERSION)
        writePlugin(root, id = "com.example.conflict", version = "1.0.0")
        writePlugin(root, id = "com.example.conflict", version = "2.0.0")

        val failure = assertFailsWith<IllegalStateException> { RuntimePlugin.start() }

        assertTrue(failure.message.orEmpty().contains("com.example.conflict"))
        assertTrue(failure.message.orEmpty().contains("1.0.0"))
        assertTrue(failure.message.orEmpty().contains("2.0.0"))
        assertEquals(listOf(existing), PlugManager.loadedList)
        assertSame(existing, PlugManager.loadedList.single())
    }

    @Test
    fun runtimePluginStartRollsBackOnlyNewEntriesWhenAPluginFailsAndCanRetryAfterRepair() = withRuntimeRoot { root ->
        writePlugin(root, id = "com.example.a.preloaded")
        val preloaded = PlugManager.loadPlugin("com.example.a.preloaded", VERSION)
        writePlugin(root, id = "com.example.b.new")
        writePlugin(root, id = "com.example.c.broken", entry = ConstructorFailureEntry::class.java)

        assertFailsWith<IllegalStateException> { RuntimePlugin.start() }
        assertEquals(listOf(preloaded), PlugManager.loadedList)
        assertSame(preloaded, PlugManager.loadedList.single())

        writePlugin(root, id = "com.example.c.broken", entry = LoadingEntry::class.java)
        RuntimePlugin.start()

        assertEquals(
            listOf("com.example.a.preloaded", "com.example.b.new", "com.example.c.broken"),
            PlugManager.loadedList.map { it.manifest.id },
        )
        assertSame(preloaded, PlugManager.loadedList.single { it.manifest.id == "com.example.a.preloaded" })
    }

    @Test
    fun runtimePluginStartSkipsInvalidInstallationsReportedByInstalledList() = withRuntimeRoot { root ->
        writePlugin(root, id = "com.example.valid")
        val invalid = writePlugin(root, id = "com.example.invalid")
        Files.delete(invalid.resolve("plugin.jar"))

        RuntimePlugin.start()

        assertEquals(listOf("com.example.valid"), PlugManager.loadedList.map { it.manifest.id })
    }

    @Test
    fun scansExactProvidersAndDirectChildrenInPluginAndDeclarationOrder() = withRuntimeRoot { root ->
        writePlugin(root, id = "com.example.z", entry = ScanEntry::class.java)
        writePlugin(root, id = "com.example.a", entry = OtherScanEntry::class.java)
        val z = PlugManager.loadPlugin("com.example.z", VERSION)
        val a = PlugManager.loadPlugin("com.example.a", VERSION)
        a.providers[0].enable = false
        z.dispatchers[1].enable = false
        val scanner = ScanningDispatcher("/Room")

        runBlocking {
            scanner.scanProviders()
            scanner.scanDispatchers()
        }

        val providers = scanner.providers.values.toList()
        val dispatchers = scanner.dispatchers.values.toList()
        assertEquals(listOf(a.providers[0], z.providers[0], z.providers[4]), providers)
        assertEquals(listOf(a.dispatchers[0], z.dispatchers[1], z.dispatchers[2]), dispatchers)
        assertSame(a.providers[0], providers[0])
        assertSame(z.providers[4], providers[2])
        assertSame(a.dispatchers[0], dispatchers[0])
        assertSame(z.dispatchers[2], dispatchers[2])
        assertFalse(providers[0].enable)
        assertFalse(dispatchers[1].enable)
        val rootScanner = ScanningDispatcher("/")
        runBlocking { rootScanner.scanDispatchers() }
        assertSame(z.dispatchers[0], rootScanner.dispatchers.values.single())
    }

    @Test
    fun repeatedScansRefreshStaleEntriesAfterUnloadAndKeepMapInstances() = withRuntimeRoot { root ->
        writePlugin(root, entry = ScanEntry::class.java)
        PlugManager.loadPlugin(ID, VERSION)
        val scanner = ScanningDispatcher("/Room")
        val providers = scanner.providers
        val dispatchers = scanner.dispatchers
        val staleProvider = LoadingProvider("/Room")
        val staleDispatcher = ScanningDispatcher("/Room/Stale")
        providers[staleProvider.id] = staleProvider
        dispatchers[staleDispatcher.id] = staleDispatcher
        runBlocking {
            scanner.scanProviders()
            scanner.scanDispatchers()
        }
        assertFalse(staleProvider.id in providers)
        assertFalse(staleDispatcher.id in dispatchers)
        val expectedProviders = providers.toMap()
        val expectedDispatchers = dispatchers.toMap()

        runBlocking {
            scanner.scanProviders()
            scanner.scanDispatchers()
        }
        assertEquals(expectedProviders, providers)
        assertEquals(expectedDispatchers, dispatchers)
        PlugManager.unloadPlugin(ID)
        runBlocking {
            scanner.scanProviders()
            scanner.scanDispatchers()
        }

        assertSame(providers, scanner.providers)
        assertSame(dispatchers, scanner.dispatchers)
        assertTrue(providers.isEmpty())
        assertTrue(dispatchers.isEmpty())
    }

    @Test
    fun eachScanOnlyUpdatesItsOwnMapWithoutLifecycleBindingOrRecursiveScanning() = withRuntimeRoot { root ->
        writePlugin(root, entry = ScanEntry::class.java)
        val loaded = PlugManager.loadPlugin(ID, VERSION)
        val scanner = ScanningDispatcher("/Room")
        val staleDispatcher = ScanningDispatcher("/Room/Stale")
        scanner.dispatchers[staleDispatcher.id] = staleDispatcher
        val onEvent: suspend (Event) -> Unit = { error("Scanning must not deliver events") }
        loaded.providers.forEach { it.emit = onEvent }
        loaded.dispatchers.forEach { it.emit = onEvent }
        val child = loaded.dispatchers[1]
        val childProvider = LoadingProvider("/Room/Zeta")
        val childDispatcher = ScanningDispatcher("/Room/Zeta/Stale")
        child.providers[childProvider.id] = childProvider
        child.dispatchers[childDispatcher.id] = childDispatcher

        runBlocking { scanner.scanProviders() }
        assertSame(staleDispatcher, scanner.dispatchers.values.single())
        val scannedProviders = scanner.providers.toMap()
        runBlocking { scanner.scanDispatchers() }

        assertEquals(scannedProviders, scanner.providers)
        assertSame(childProvider, child.providers.values.single())
        assertSame(childDispatcher, child.dispatchers.values.single())
        loaded.providers.forEach {
            assertSame(onEvent, it.emit)
            assertEquals(Provider.Status.STOP, it.status)
        }
        loaded.dispatchers.forEach { assertSame(onEvent, it.emit) }
    }

    @Test
    fun duplicateIdsAndRepeatedInstancesFailWithoutReplacingExistingMaps() {
        for (entry in listOf(DuplicateIdsEntry::class.java, RepeatedComponentsEntry::class.java)) {
            withRuntimeRoot { root ->
                writePlugin(root, entry = entry)
                PlugManager.loadPlugin(ID, VERSION)
                val scanner = ScanningDispatcher("/Room")
                val oldProvider = LoadingProvider("/Room")
                val oldDispatcher = ScanningDispatcher("/Room/Existing")
                scanner.providers[oldProvider.id] = oldProvider
                scanner.dispatchers[oldDispatcher.id] = oldDispatcher

                assertFailsWith<IllegalStateException> { runBlocking { scanner.scanProviders() } }
                assertSame(oldProvider, scanner.providers.values.single())
                assertSame(oldDispatcher, scanner.dispatchers.values.single())
                assertFailsWith<IllegalStateException> { runBlocking { scanner.scanDispatchers() } }
                assertSame(oldProvider, scanner.providers.values.single())
                assertSame(oldDispatcher, scanner.dispatchers.values.single())
            }
        }
    }

    @Test
    fun duplicateChildTopicsFailAtomicallyWhileProvidersCanShareATopic() = withRuntimeRoot { root ->
        writePlugin(root, entry = QueryEntry::class.java)
        val loaded = PlugManager.loadPlugin(ID, VERSION)
        val scanner = ScanningDispatcher("/Room")
        val oldDispatcher = ScanningDispatcher("/Room/Existing")
        scanner.dispatchers[oldDispatcher.id] = oldDispatcher

        runBlocking { scanner.scanProviders() }
        assertEquals(listOf(loaded.providers[0], loaded.providers[4]), scanner.providers.values.toList())
        assertFailsWith<IllegalStateException> { runBlocking { scanner.scanDispatchers() } }

        assertSame(oldDispatcher, scanner.dispatchers.values.single())
        assertEquals(listOf(loaded.providers[0], loaded.providers[4]), scanner.providers.values.toList())
    }

    @Test
    fun aChildCannotReuseTheParentDispatcherId() = withRuntimeRoot { root ->
        writePlugin(root, entry = ScanEntry::class.java)
        val loaded = PlugManager.loadPlugin(ID, VERSION)
        val scanner = ScanningDispatcher("/Room", id = loaded.dispatchers[2].id)
        val oldDispatcher = ScanningDispatcher("/Room/Existing")
        scanner.dispatchers[oldDispatcher.id] = oldDispatcher

        assertFailsWith<IllegalStateException> { runBlocking { scanner.scanDispatchers() } }

        assertSame(oldDispatcher, scanner.dispatchers.values.single())
    }

    @Test
    fun invalidParentTopicsDoNotClearEitherMap() = withRuntimeRoot { _ ->
        for (topic in listOf("", "Room", "/Room/", "/Room//Child", "/Room/..")) {
            val scanner = ScanningDispatcher(topic)
            val oldProvider = LoadingProvider("/Room")
            val oldDispatcher = ScanningDispatcher("/Room/Existing")
            scanner.providers[oldProvider.id] = oldProvider
            scanner.dispatchers[oldDispatcher.id] = oldDispatcher

            assertFailsWith<IllegalArgumentException> { runBlocking { scanner.scanProviders() } }
            assertFailsWith<IllegalArgumentException> { runBlocking { scanner.scanDispatchers() } }

            assertSame(oldProvider, scanner.providers.values.single())
            assertSame(oldDispatcher, scanner.dispatchers.values.single())
        }
    }

    @Test
    fun topicQueriesDiscoverOnlyLoadedPluginsAndUpdateAfterUnload() = withRuntimeRoot { root ->
        assertTrue(PlugManager.findProviders("/Loading").isEmpty())
        assertTrue(PlugManager.findChildDispatchers("/").isEmpty())
        assertFalse(Files.exists(root.resolve("Plugins")))
        writePlugin(root)
        assertTrue(PlugManager.findProviders("/Loading").isEmpty())
        assertTrue(PlugManager.findChildDispatchers("/").isEmpty())

        val loaded = PlugManager.loadPlugin(ID, VERSION)
        assertSame(loaded.providers.single(), PlugManager.findProviders("/Loading").single())
        assertSame(loaded.dispatchers.single(), PlugManager.findChildDispatchers("/").single())

        PlugManager.unloadPlugin(ID)
        assertTrue(PlugManager.findProviders("/Loading").isEmpty())
        assertTrue(PlugManager.findChildDispatchers("/").isEmpty())
    }

    @Test
    fun providerQueriesMatchOnlyExactCaseSensitiveTopics() = withRuntimeRoot { root ->
        writePlugin(root, entry = QueryEntry::class.java)
        val loaded = PlugManager.loadPlugin(ID, VERSION)

        assertEquals(listOf(loaded.providers[0], loaded.providers[4]), PlugManager.findProviders("/Room"))
        assertSame(loaded.providers[1], PlugManager.findProviders("/Room/Child").single())
        assertSame(loaded.providers[2], PlugManager.findProviders("/Roommate").single())
        assertSame(loaded.providers[3], PlugManager.findProviders("/room").single())
        assertSame(loaded.providers[5], PlugManager.findProviders("/").single())
        assertSame(loaded.providers[6], PlugManager.findProviders("/Room with space").single())
        assertTrue(PlugManager.findProviders("/Roo").isEmpty())
        assertTrue(PlugManager.findProviders("/ROOM").isEmpty())
    }

    @Test
    fun dispatcherQueriesReturnOnlyValidDirectChildrenInDeclarationOrder() = withRuntimeRoot { root ->
        writePlugin(root, entry = QueryEntry::class.java)
        val loaded = PlugManager.loadPlugin(ID, VERSION)

        assertEquals(listOf(loaded.dispatchers[0], loaded.dispatchers[2]), PlugManager.findChildDispatchers("/"))
        assertEquals(
            listOf(loaded.dispatchers[3], loaded.dispatchers[4], loaded.dispatchers[8]),
            PlugManager.findChildDispatchers("/Room"),
        )
        assertSame(loaded.dispatchers[5], PlugManager.findChildDispatchers("/Room/First").single())
        assertSame(loaded.dispatchers[6], PlugManager.findChildDispatchers("/Roommate").single())
        assertSame(loaded.dispatchers[7], PlugManager.findChildDispatchers("/room").single())
        assertTrue(PlugManager.findChildDispatchers("/Roo").isEmpty())
        assertTrue(PlugManager.findChildDispatchers("/Room/Second").isEmpty())
        assertTrue(PlugManager.findChildDispatchers("/ROOM").isEmpty())
    }

    @Test
    fun queriesPreservePluginOrderComponentIdentityAndDisabledComponents() = withRuntimeRoot { root ->
        writePlugin(root, id = "com.example.z", entry = QueryEntry::class.java)
        writePlugin(root, id = "com.example.a", entry = QueryEntry::class.java)
        val z = PlugManager.loadPlugin("com.example.z", VERSION)
        val a = PlugManager.loadPlugin("com.example.a", VERSION)
        a.providers[0].enable = false
        z.dispatchers[3].enable = false

        val providers = PlugManager.findProviders("/Room")
        val dispatchers = PlugManager.findChildDispatchers("/Room")

        val expectedProviders = listOf(a.providers[0], a.providers[4], z.providers[0], z.providers[4])
        val expectedDispatchers = listOf(
            a.dispatchers[3], a.dispatchers[4], a.dispatchers[8],
            z.dispatchers[3], z.dispatchers[4], z.dispatchers[8],
        )
        assertEquals(expectedProviders.size, providers.size)
        assertEquals(expectedDispatchers.size, dispatchers.size)
        expectedProviders.zip(providers).forEach { (expected, actual) -> assertSame(expected, actual) }
        expectedDispatchers.zip(dispatchers).forEach { (expected, actual) -> assertSame(expected, actual) }
        assertFalse(providers.first().enable)
        assertFalse(dispatchers[3].enable)
        assertTrue(providers.all { it.status == Provider.Status.STOP && it.emit == null })
        assertTrue(dispatchers.all { it.emit == null })
    }

    @Test
    fun queriesUseLoadedComponentSnapshotsAndReturnDetachedResults() = withRuntimeRoot { root ->
        writePlugin(root, entry = QueryEntry::class.java)
        val loaded = PlugManager.loadPlugin(ID, VERSION)
        val providers = PlugManager.findProviders("/Room")
        val dispatchers = PlugManager.findChildDispatchers("/Room")
        (loaded.entry.providers as MutableList<*>).clear()
        (loaded.entry.dispatchers as MutableList<*>).clear()

        assertEquals(providers, PlugManager.findProviders("/Room"))
        assertEquals(dispatchers, PlugManager.findChildDispatchers("/Room"))

        PlugManager.unloadPlugin(ID)
        assertTrue(PlugManager.findProviders("/Room").isEmpty())
        assertTrue(PlugManager.findChildDispatchers("/Room").isEmpty())
        assertEquals(2, providers.size)
        assertEquals(3, dispatchers.size)
    }

    @Test
    fun rejectsMalformedTopicQueriesEvenWhenNothingIsLoaded() = withRuntimeRoot { root ->
        for (topic in listOf(
            "", " ", "Room", " Room", "//", "/Room/", "/Room//Child", "/.", "/..", "/Room/.", "/Room/..",
            "/ ", "/ leading", "/trailing ", "/Room/ leading", "/Room/trailing ",
            "\\Room", "/Room\\Child", "/Room/\nChild", "/Room/\u0000Child", "/Room/\u007fChild",
        )) {
            assertFailsWith<IllegalArgumentException>(topic) { PlugManager.findProviders(topic) }
            assertFailsWith<IllegalArgumentException>(topic) { PlugManager.findChildDispatchers(topic) }
        }
        assertFalse(Files.exists(root.resolve("Plugins")))
    }

    @Test
    fun readingAnEmptyRegistryAndUnloadingMissingPluginsDoesNotCreateDirectories() = withRuntimeRoot { root ->
        assertTrue(PlugManager.loadedList.isEmpty())
        assertFalse(PlugManager.unloadPlugin(ID))
        assertFalse(Files.exists(root.resolve("Plugins")))
    }

    @Test
    fun loadsRealPluginAndPrivateDependencyClassesWhileSharingHostEventsAndApis() = withRuntimeRoot { root ->
        val directory = writePlugin(root)

        val loaded = PlugManager.loadPlugin(ID, VERSION)

        assertEquals(ID, loaded.manifest.id)
        assertEquals(directory.toString(), loaded.directory)
        assertSame(loaded, PlugManager.loadedList.single())
        val loader = assertIs<URLClassLoader>(loaded.entry.javaClass.classLoader)
        assertNotSame(LoadingEntry::class.java.classLoader, loader)
        assertEquals(directory.resolve("plugin.jar").toUri(), loaded.entry.javaClass.protectionDomain.codeSource.location.toURI())
        assertSame(loader, loaded.providers.single().javaClass.classLoader)
        assertSame(loader, loaded.dispatchers.single().javaClass.classLoader)
        assertSame(loader, loader.loadClass(PrivateDependency::class.java.name).classLoader)
        assertSame(PluginEntry::class.java, loader.loadClass(PluginEntry::class.java.name))
        assertSame(Event::class.java, loader.loadClass(Event::class.java.name))
        assertEquals("Plugin private dependency", loaded.providers.single().name)

        val provider = loaded.providers.single()
        val dispatcher = loaded.dispatchers.single()
        assertEquals(Provider.Status.STOP, provider.status)
        assertNull(provider.emit)
        assertNull(dispatcher.emit)
        val event = HostEvent()
        val received = mutableListOf<Event>()
        val onEvent: suspend (Event) -> Unit = { received += it }
        provider.emit = onEvent
        dispatcher.emit = onEvent
        runBlocking {
            provider.receive(event)
            dispatcher.receive(event)
        }
        assertEquals(2, received.size)
        assertTrue(received.all { it === event })

        assertTrue(PlugManager.unloadPlugin(ID))
        assertEquals(Provider.Status.STOP, provider.status)
        assertSame(onEvent, provider.emit)
        assertSame(onEvent, dispatcher.emit)
    }

    @Test
    fun repeatedLoadReturnsTheSameEntryButASecondVersionIsRejected() = withRuntimeRoot { root ->
        writePlugin(root)
        writePlugin(root, version = "2.0.0")
        val loaded = PlugManager.loadPlugin(ID, VERSION)

        assertSame(loaded, PlugManager.loadPlugin(ID, VERSION))
        assertFailsWith<IllegalStateException> { PlugManager.loadPlugin(ID, "2.0.0") }
        assertSame(loaded, PlugManager.loadedList.single())

        assertTrue(PlugManager.unloadPlugin(ID))
        assertEquals("2.0.0", PlugManager.loadPlugin(ID, "2.0.0").manifest.version)
    }

    @Test
    fun sharedNamespaceDependenciesCanLoadWhenTheHostDoesNotProvideTheClass() = withRuntimeRoot { root ->
        val directory = writePlugin(root)
        val className = "javax.evaplugintest.Helper"
        val source = root.resolve("Helper.java")
        Files.writeString(source, "package javax.evaplugintest; public class Helper { public static int value() { return 42; } }")
        val compiled = Files.createDirectory(root.resolve("compiled"))
        val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler(), "JVM loading tests require a JDK")
        assertEquals(0, compiler.run(null, null, null, "-d", compiled.toString(), source.toString()))
        val classResource = className.replace('.', '/') + ".class"
        JarOutputStream(Files.newOutputStream(directory.resolve("dependencies/shared-namespace.jar"))).use { jar ->
            jar.putNextEntry(JarEntry(classResource))
            jar.write(Files.readAllBytes(compiled.resolve(classResource)))
            jar.closeEntry()
        }
        assertFailsWith<ClassNotFoundException> { LoadingEntry::class.java.classLoader.loadClass(className) }

        val loaded = PlugManager.loadPlugin(ID, VERSION)
        val loader = loaded.entry.javaClass.classLoader
        val helper = loader.loadClass(className)

        assertSame(loader, helper.classLoader)
        assertEquals(42, helper.getMethod("value").invoke(null))
    }

    @Test
    fun changingRuntimeRootCannotReuseAnEntryFromAnotherDirectory() = withRuntimeRoot { root ->
        writePlugin(root)
        val loaded = PlugManager.loadPlugin(ID, VERSION)
        val otherRoot = Files.createDirectory(root.resolve("other-runtime"))
        writePlugin(otherRoot)
        System.setProperty("user.dir", otherRoot.toString())

        assertFailsWith<IllegalStateException> { PlugManager.loadPlugin(ID, VERSION) }
        assertSame(loaded, PlugManager.loadedList.single())

        assertTrue(PlugManager.unloadPlugin(ID))
        assertEquals(otherRoot.resolve("Plugins/$ID/versions/$VERSION").toString(), PlugManager.loadPlugin(ID, VERSION).directory)
    }

    @Test
    fun registryAndComponentListsAreDetachedSnapshots() = withRuntimeRoot { root ->
        writePlugin(root, id = "com.example.z")
        writePlugin(root, id = "com.example.a")
        val z = PlugManager.loadPlugin("com.example.z", VERSION)
        val onePluginSnapshot = PlugManager.loadedList
        val a = PlugManager.loadPlugin("com.example.a", VERSION)
        val twoPluginSnapshot = PlugManager.loadedList

        assertEquals(listOf(z), onePluginSnapshot)
        assertEquals(listOf(a, z), twoPluginSnapshot)
        (z.entry.providers as MutableList<*>).clear()
        (z.entry.dispatchers as MutableList<*>).clear()
        assertEquals(1, z.providers.size)
        assertEquals(1, z.dispatchers.size)

        PlugManager.unloadPlugin("com.example.a")
        assertEquals(listOf(z), PlugManager.loadedList)
        assertEquals(listOf(a, z), twoPluginSnapshot)
    }

    @Test
    fun unloadClosesTheLoaderKeepsInstalledFilesAndAllowsFreshReload() = withRuntimeRoot { root ->
        val directory = writePlugin(root)
        val loaded = PlugManager.loadPlugin(ID, VERSION)
        val loader = assertIs<URLClassLoader>(loaded.entry.javaClass.classLoader)
        assertNotNull(loader.findResource("loading-fixture.txt"))

        assertTrue(PlugManager.unloadPlugin(ID))

        assertNull(loader.findResource("loading-fixture.txt"))
        assertTrue(PlugManager.loadedList.isEmpty())
        assertFalse(PlugManager.unloadPlugin(ID))
        assertTrue(Files.isRegularFile(directory.resolve("plugin.jar")))
        assertTrue(Files.isRegularFile(directory.resolve("plugin.json")))
        val reloaded = PlugManager.loadPlugin(ID, VERSION)
        assertNotSame(loaded.entry, reloaded.entry)
        assertNotSame(loader, reloaded.entry.javaClass.classLoader)
        assertEquals(loaded.manifest, reloaded.manifest)
    }

    @Test
    fun rejectsInvalidIdsAndVersionsBeforeAccessingTheFilesystem() = withRuntimeRoot { root ->
        for (invalid in listOf("", " ", ".", "..", "../outside", "dir/name", "dir\\name", "a:b", " padded ", "a\u0000b")) {
            assertFailsWith<IllegalArgumentException> { PlugManager.loadPlugin(invalid, VERSION) }
            assertFailsWith<IllegalArgumentException> { PlugManager.loadPlugin(ID, invalid) }
            assertFailsWith<IllegalArgumentException> { PlugManager.unloadPlugin(invalid) }
        }
        assertFalse(Files.exists(root.resolve("Plugins")))
    }

    @Test
    fun missingPluginFailsWithoutPublishingOrCreatingAnInstallation() = withRuntimeRoot { root ->
        assertFailsWith<IllegalStateException> { PlugManager.loadPlugin(ID, VERSION) }
        assertTrue(PlugManager.loadedList.isEmpty())
        assertFalse(Files.exists(root.resolve("Plugins")))
        writePlugin(root)
        assertEquals(ID, PlugManager.loadPlugin(ID, VERSION).manifest.id)
    }

    @Test
    fun invalidManifestAndMissingOrCorruptArtifactsLeaveTheRegistryEmptyAndAllowRetry() {
        val mutations: List<(Path) -> Unit> = listOf(
            { Files.writeString(it.resolve("plugin.json"), "not json") },
            { changeManifest(it) { manifest -> manifest.copy(apiVersion = 1) } },
            { changeManifest(it) { manifest -> manifest.copy(apiVersion = CURRENT_PLUGIN_API_VERSION + 1) } },
            { changeManifest(it) { manifest -> manifest.copy(formatVersion = 2) } },
            { changeManifest(it) { manifest -> manifest.copy(id = "another.id") } },
            { changeManifest(it) { manifest -> manifest.copy(version = "2.0.0") } },
            { changeManifest(it) { manifest -> manifest.copy(platforms = PluginManifest.Platforms()) } },
            { changeJvm(it) { jvm -> jvm.copy(minJavaVersion = Runtime.version().feature() + 1) } },
            { changeJvm(it) { jvm -> jvm.copy(artifact = "../outside.jar") } },
            { changeJvm(it) { jvm -> jvm.copy(dependencies = "../outside") } },
            { Files.delete(it.resolve("plugin.jar")) },
            { Files.writeString(it.resolve("plugin.jar"), "not a jar") },
            { Files.writeString(it.resolve("dependencies/private.jar"), "not a jar") },
        )
        for (mutate in mutations) {
            withRuntimeRoot { root ->
                mutate(writePlugin(root))
                assertFailsWith<IllegalStateException> { PlugManager.loadPlugin(ID, VERSION) }
                assertTrue(PlugManager.loadedList.isEmpty())
                writePlugin(root)
                assertEquals(ID, PlugManager.loadPlugin(ID, VERSION).manifest.id)
            }
        }
    }

    @Test
    fun constructionAndComponentFailuresDoNotPublishAnEntryAndAllowRetry() {
        for (entry in listOf(
            ConstructorFailureEntry::class.java,
            ProvidersFailureEntry::class.java,
            DispatchersFailureEntry::class.java,
            PrivateConstructorEntry::class.java,
            RequiredArgumentEntry::class.java,
            NotAnEntry::class.java,
        )) {
            withRuntimeRoot { root ->
                writePlugin(root, entry = entry)
                assertFailsWith<IllegalStateException> { PlugManager.loadPlugin(ID, VERSION) }
                assertTrue(PlugManager.loadedList.isEmpty())
                writePlugin(root)
                assertEquals(LoadingEntry::class.java.name, PlugManager.loadPlugin(ID, VERSION).entry.javaClass.name)
            }
        }
    }

    @Test
    fun anEntryAvailableOnlyOnTheHostOrInADependencyIsRejected() {
        for (inDependency in listOf(false, true)) {
            withRuntimeRoot { root ->
                val directory = writePlugin(root)
                writeJar(directory.resolve("plugin.jar"), listOf(LoadingProvider::class.java, LoadingDispatcher::class.java))
                if (inDependency) {
                    writeJar(directory.resolve("dependencies/entry.jar"), listOf(LoadingEntry::class.java))
                }
                assertFailsWith<IllegalStateException> { PlugManager.loadPlugin(ID, VERSION) }
                assertTrue(PlugManager.loadedList.isEmpty())
            }
        }
    }

    @Test
    fun refusesJarClasspathAndIndexThatCouldExpandTheDeclaredClassPath() {
        for (inDependency in listOf(false, true)) {
            for (withIndex in listOf(false, true)) {
                withRuntimeRoot { root ->
                    val directory = writePlugin(root)
                    val jar = if (inDependency) directory.resolve("dependencies/private.jar") else directory.resolve("plugin.jar")
                    val classes = if (inDependency) listOf(PrivateDependency::class.java) else mainClasses(LoadingEntry::class.java)
                    writeJar(jar, classes, classPath = if (withIndex) null else "../../outside.jar", withIndex = withIndex)

                    assertFailsWith<IllegalStateException> { PlugManager.loadPlugin(ID, VERSION) }
                    assertTrue(PlugManager.loadedList.isEmpty())
                }
            }
        }
    }

    @Test
    fun refusesSymlinksAtInstallationAndArtifactBoundaries() {
        if (System.getProperty("os.name").startsWith("Windows")) return
        for (relative in listOf(
            "Plugins",
            "Plugins/$ID",
            "Plugins/$ID/versions",
            "Plugins/$ID/versions/$VERSION",
            "Plugins/$ID/versions/$VERSION/plugin.json",
            "Plugins/$ID/versions/$VERSION/plugin.jar",
            "Plugins/$ID/versions/$VERSION/dependencies",
            "Plugins/$ID/versions/$VERSION/dependencies/private.jar",
        )) {
            withRuntimeRoot { root ->
                writePlugin(root)
                val original = root.resolve(relative)
                val outside = root.resolve("outside")
                Files.move(original, outside)
                Files.createSymbolicLink(original, outside)

                assertFailsWith<IllegalStateException> { PlugManager.loadPlugin(ID, VERSION) }
                assertTrue(PlugManager.loadedList.isEmpty())
                assertTrue(Files.exists(outside))
                Files.delete(original)
            }
        }
    }

    @Test
    fun constructorsCanReadTheRegistryButCannotLoadOrUnloadTheirOwnReservation() = withRuntimeRoot { root ->
        writePlugin(root, entry = HookEntry::class.java)
        var invoked = false
        LoadingFixtureHooks.onConstruct = {
            invoked = true
            assertTrue(PlugManager.loadedList.isEmpty())
            assertFailsWith<IllegalStateException> { PlugManager.loadPlugin(ID, VERSION) }
            assertFailsWith<IllegalStateException> { PlugManager.unloadPlugin(ID) }
        }

        val loaded = PlugManager.loadPlugin(ID, VERSION)

        assertTrue(invoked)
        assertSame(loaded, PlugManager.loadedList.single())
    }

    @Test
    fun concurrentInitializationReservesOnlyItsOwnPluginAndIsNotPublishedEarly() = withRuntimeRoot { root ->
        writePlugin(root, entry = HookEntry::class.java)
        writePlugin(root, id = "com.example.other")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        LoadingFixtureHooks.onConstruct = {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "Timed out waiting for test release" }
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val loading = executor.submit<LoadedPlugin> { PlugManager.loadPlugin(ID, VERSION) }
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Plugin constructor never started")
            val other = executor.submit<LoadedPlugin> {
                assertTrue(PlugManager.loadedList.isEmpty())
                assertFailsWith<IllegalStateException> { PlugManager.loadPlugin(ID, VERSION) }
                assertFailsWith<IllegalStateException> { PlugManager.unloadPlugin(ID) }
                PlugManager.loadPlugin("com.example.other", VERSION)
            }.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(other), PlugManager.loadedList)
            release.countDown()
            val loaded = loading.get(5, TimeUnit.SECONDS)
            assertEquals(setOf(loaded, other), PlugManager.loadedList.toSet())
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun writePlugin(
        root: Path,
        id: String = ID,
        version: String = VERSION,
        entry: Class<*> = LoadingEntry::class.java,
    ): Path {
        val directory = root.resolve("Plugins/$id/versions/$version")
        Files.createDirectories(directory.resolve("dependencies"))
        writeJar(directory.resolve("plugin.jar"), mainClasses(entry))
        writeJar(directory.resolve("dependencies/private.jar"), listOf(PrivateDependency::class.java))
        val manifest = PluginManifest(
            formatVersion = 1,
            id = id,
            name = "Loading fixture",
            version = version,
            apiVersion = CURRENT_PLUGIN_API_VERSION,
            platforms = PluginManifest.Platforms(
                jvm = PluginManifest.Jvm("plugin.jar", entry.name, dependencies = "dependencies"),
            ),
        )
        Files.writeString(directory.resolve("plugin.json"), Json.encodeToString(manifest))
        return directory
    }

    private fun mainClasses(entry: Class<*>): List<Class<*>> = listOf(
        entry,
        LoadingProvider::class.java,
        LoadingDispatcher::class.java,
        BootDispatcher::class.java,
        // Deliberately bundled API duplicates must still resolve to the host definitions.
        PluginEntry::class.java,
        Event::class.java,
        WidgetContext::class.java,
    )

    private fun writeJar(
        path: Path,
        classes: List<Class<*>>,
        classPath: String? = null,
        withIndex: Boolean = false,
    ) {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            if (classPath != null) mainAttributes[Attributes.Name.CLASS_PATH] = classPath
        }
        JarOutputStream(Files.newOutputStream(path), manifest).use { jar ->
            val entries = linkedMapOf<String, ByteArray>()
            classes.forEach { fixtureClass -> entries.putAll(compiledClasses(fixtureClass)) }
            entries["loading-fixture.txt"] = "resource held by the plugin loader".encodeToByteArray()
            if (withIndex) entries["META-INF/INDEX.LIST"] = "JarIndex-Version: 1.0\n\n".encodeToByteArray()
            for ((name, bytes) in entries) {
                jar.putNextEntry(JarEntry(name))
                jar.write(bytes)
                jar.closeEntry()
            }
        }
    }

    /** Include compiler-generated nested classes, including coroutine continuations. */
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

    private fun changeManifest(directory: Path, change: (PluginManifest) -> PluginManifest) {
        val path = directory.resolve("plugin.json")
        val manifest = Json.decodeFromString<PluginManifest>(Files.readString(path))
        Files.writeString(path, Json.encodeToString(change(manifest)))
    }

    private fun changeJvm(directory: Path, change: (PluginManifest.Jvm) -> PluginManifest.Jvm) {
        changeManifest(directory) { manifest ->
            manifest.copy(platforms = manifest.platforms.copy(jvm = change(checkNotNull(manifest.platforms.jvm))))
        }
    }

    private fun withRuntimeRoot(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("eva-plugin-loading")
        val previous = System.getProperty("user.dir")
        try {
            PlugManager.loadedList.forEach { PlugManager.unloadPlugin(it.manifest.id) }
            System.setProperty("user.dir", root.toString())
            block(root)
        } finally {
            LoadingFixtureHooks.onConstruct = null
            LoadingFixtureHooks.onLifecycle = null
            runBlocking { Booter.stop() }
            PlugManager.loadedList.forEach { PlugManager.unloadPlugin(it.manifest.id) }
            if (previous == null) System.clearProperty("user.dir") else System.setProperty("user.dir", previous)
            root.toFile().deleteRecursively()
        }
    }

    private class ScanningDispatcher(
        override val topic: String,
        override val id: Uuid = Uuid.random(),
    ) : BaseDispatcher() {
        override val name = "Host scan test dispatcher"
        override val desc = "Scans installed test plugin components"
        override suspend fun dispatch(event: Event) = Unit
        override suspend fun receive(event: Event) = Unit
    }

    private class HostEvent : Event {
        override val id = Uuid.random()
        override val metadata = Event.Metadata()
        override var records: List<EventRecord> = emptyList()
        override val payload = Event.Payload.Text("host event")
    }

    private companion object {
        const val ID = "com.example.loading"
        const val VERSION = "1.0.0"
    }
}
