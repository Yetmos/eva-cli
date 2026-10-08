package com.eva_cli.core.widgets.context

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eva_cli.core.runtime.Configure
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
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

/** One owner per loaded plugin; close before another context opens the same plugin data. */
internal class JvmWidgetContext(
    override val pluginId: String,
    runtimeDirectory: Path,
    pluginDirectory: Path,
    resourcesPath: String?,
    permissions: PluginPermissions,
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
    private var store: DataStore<Preferences>? = null
    private val openDatabases = mutableMapOf<String, DatabaseEntry>()
    private val openingDatabases = mutableMapOf<String, DatabaseOpening>()

    init {
        requireSingleName(pluginId)
        resourcesRoot = resourcesPath?.let { safePath(pluginRoot, it) }
    }

    override val network: PluginNetwork = object : PluginNetwork {
        override suspend fun execute(request: NetworkRequest): NetworkResponse = withinLifetime {
            val http = synchronized(lock) {
                requireOpen()
                client ?: HttpClient(OkHttp).also { client = it }
            }
            val response = http.request(request.url) {
                method = HttpMethod.parse(request.method)
                request.headers.forEach { (name, value) -> headers.append(name, value) }
                request.body?.let { setBody(it) }
            }
            NetworkResponse(
                status = response.status.value,
                headers = response.headers.entries().associate { it.key to it.value },
                body = response.bodyAsBytes(),
            )
        }
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
