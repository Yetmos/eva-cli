package com.eva_cli.core.widgets.plug

import com.eva_cli.core.runtime.Configure
import com.eva_cli.core.utils.file.FileUtils
import com.eva_cli.core.widgets.context.JvmWidgetContext
import com.eva_cli.core.widgets.context.PluginPermissions
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.Collections
import java.util.Enumeration
import java.util.jar.Attributes
import java.util.jar.JarFile

private data class PluginRegistration(
    val plugin: LoadedPlugin,
    val loader: PluginClassLoader,
    val context: JvmWidgetContext,
)

/** 每次申请解析当前 UI 桥接，Activity 重建后已有插件不会继续持有旧 Activity。 */
private object HostPluginPermissions : PluginPermissions {
    override suspend fun check(permission: String) = PlugManager.permissions.check(permission)
    override suspend fun request(permission: String) = PlugManager.permissions.request(permission)
}

private val registryLock = Any()
private val loadedPlugins = mutableMapOf<String, PluginRegistration>()
private val changingPlugins = mutableSetOf<String>()

internal actual fun listLoadedPlugins(): List<LoadedPlugin> = synchronized(registryLock) {
    loadedPlugins.values.map { it.plugin }.sortedBy { it.manifest.id }
}

internal actual fun loadLocalPlugin(id: String, version: String): LoadedPlugin {
    require(isSafeInstallSegment(id)) { "插件 id 不是安全的目录名：$id" }
    require(isSafeInstallSegment(version)) { "插件 version 不是安全的目录名：$version" }
    val runtimeDirectory = Path.of(FileUtils.rootPath()).toAbsolutePath().normalize()
    val pluginsDirectory = runtimeDirectory.resolve(Configure.PLUGINS_DIR)
    val pluginDirectory = pluginsDirectory.resolve(id)
    val versionsDirectory = pluginDirectory.resolve(Configure.PLUGIN_VERSIONS_DIR)
    val versionDirectory = versionsDirectory.resolve(version)

    synchronized(registryLock) {
        check(id !in changingPlugins) { "插件正在加载或释放：$id" }
        loadedPlugins[id]?.let { registration ->
            check(registration.plugin.manifest.version == version &&
                registration.plugin.directory == versionDirectory.toString()
            ) { "插件 $id 已从 ${registration.plugin.directory} 加载，请先释放现有版本" }
            return registration.plugin
        }
        changingPlugins.add(id)
    }

    var loader: PluginClassLoader? = null
    var context: JvmWidgetContext? = null
    try {
        for (directory in listOf(runtimeDirectory, pluginsDirectory, pluginDirectory, versionsDirectory, versionDirectory)) {
            check(Files.isDirectory(directory, NOFOLLOW_LINKS)) {
                "插件安装路径不存在或不是普通目录：$directory"
            }
        }
        val manifestPath = versionDirectory.resolve("plugin.json")
        check(Files.isRegularFile(manifestPath, NOFOLLOW_LINKS)) {
            "插件清单不存在或不是普通文件：$manifestPath"
        }
        val manifest = readManifest(versionDirectory)
        check(manifest.id == id && manifest.version == version) {
            "插件清单与安装目录不匹配：$id:$version"
        }
        val jvm = checkNotNull(manifest.platforms.jvm) { "插件未声明 JVM 入口：$id:$version" }
        val artifact = resolveLoadingPath(versionDirectory, jvm.artifact, "JVM artifact")
        check(Files.isRegularFile(artifact, NOFOLLOW_LINKS)) { "JVM artifact 不是普通文件：$artifact" }
        val dependencies = jvm.dependencies?.let { value ->
            val directory = resolveLoadingPath(versionDirectory, value, "JVM dependencies")
            check(Files.isDirectory(directory, NOFOLLOW_LINKS)) { "JVM dependencies 不是普通目录：$directory" }
            dependencyJars(directory)
        }.orEmpty()
        validateManifest(manifest, versionDirectory)

        val jars = (listOf(artifact) + dependencies).distinct()
        jars.forEach(::validateClasspathJar)
        val pluginLoader = PluginClassLoader(jars.map { it.toUri().toURL() }.toTypedArray())
        loader = pluginLoader
        val entryClass = pluginLoader.loadClass(jvm.entryClass)
        check(entryClass.classLoader === pluginLoader &&
            entryClass.protectionDomain.codeSource?.location?.toURI() == artifact.toUri()
        ) { "插件入口必须来自主 artifact：${jvm.entryClass}" }
        check(Modifier.isPublic(entryClass.modifiers) && !Modifier.isAbstract(entryClass.modifiers) &&
            PluginEntry::class.java.isAssignableFrom(entryClass)
        ) { "插件入口必须是公开的具体 PluginEntry 实现：${jvm.entryClass}" }
        val entry = entryClass.asSubclass(PluginEntry::class.java).getConstructor().newInstance()
        val providers = entry.providers.toList()
        val dispatchers = entry.dispatchers.toList()
        val pluginContext = JvmWidgetContext(
            pluginId = id,
            runtimeDirectory = runtimeDirectory,
            pluginDirectory = versionDirectory,
            resourcesPath = manifest.resources,
            permissions = HostPluginPermissions,
            networkPolicy = PlugManager.networkPolicy,
        )
        context = pluginContext
        for (widget in providers + dispatchers) widget.attach(pluginContext)
        val plugin = LoadedPlugin(
            manifest = manifest,
            directory = versionDirectory.toString(),
            entry = entry,
            providers = providers,
            dispatchers = dispatchers,
            context = pluginContext,
        )
        synchronized(registryLock) {
            loadedPlugins[id] = PluginRegistration(plugin, pluginLoader, pluginContext)
        }
        return plugin
    } catch (error: Throwable) {
        try {
            context?.close()
        } catch (closeError: Throwable) {
            error.addSuppressed(closeError)
        }
        try {
            loader?.close()
        } catch (closeError: Throwable) {
            error.addSuppressed(closeError)
        }
        if (error is VirtualMachineError || error is ThreadDeath) throw error
        throw IllegalStateException("加载插件失败：$id:$version", error)
    } finally {
        synchronized(registryLock) { changingPlugins.remove(id) }
    }
}

internal actual fun unloadLocalPlugin(id: String): Boolean {
    require(isSafeInstallSegment(id)) { "插件 id 不是安全的目录名：$id" }
    val registration = synchronized(registryLock) {
        check(id !in changingPlugins) { "插件正在加载或释放：$id" }
        val existing = loadedPlugins[id] ?: return false
        existing.context.checkCanClose()
        changingPlugins.add(id)
        existing
    }
    try {
        var failure: Throwable? = null
        try {
            registration.context.close()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            registration.loader.close()
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        failure?.let { throw it }
        return true
    } catch (error: Exception) {
        throw IllegalStateException("释放插件加载资源失败：$id", error)
    } finally {
        synchronized(registryLock) {
            loadedPlugins.remove(id)
            changingPlugins.remove(id)
        }
    }
}

/** 在清单校验读取文件之前，拒绝安装目录内路径任一层的符号链接。 */
private fun resolveLoadingPath(root: Path, value: String, label: String): Path {
    val resolved = resolveManifestPath(root, value, label)
    var current = root
    for (segment in root.relativize(resolved)) {
        current = current.resolve(segment)
        check(!Files.isSymbolicLink(current)) { "$label 不允许符号链接：$current" }
    }
    return resolved
}

private fun dependencyJars(directory: Path): List<Path> = Files.walk(directory).use { paths ->
    paths.sorted().filter { path ->
        check(!Files.isSymbolicLink(path)) { "JVM dependencies 不允许符号链接：$path" }
        if (Files.isDirectory(path, NOFOLLOW_LINKS) ||
            !path.fileName.toString().endsWith(".jar", ignoreCase = true)
        ) {
            false
        } else {
            check(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "JVM dependency 不是普通文件：$path" }
            true
        }
    }.toList()
}

/** URLClassLoader 会隐式跟随 Class-Path 和 JAR 索引；只允许显式声明的本地 JAR。 */
private fun validateClasspathJar(path: Path) {
    JarFile(path.toFile()).use { jar ->
        check(jar.manifest?.mainAttributes?.getValue(Attributes.Name.CLASS_PATH).isNullOrBlank()) {
            "插件 JAR 不允许 Manifest Class-Path，请将依赖放入 JVM dependencies：$path"
        }
        check(jar.getJarEntry("META-INF/INDEX.LIST") == null) { "插件 JAR 不允许隐式 JAR 索引：$path" }
    }
}

/** 插件代码、依赖及资源优先从插件读取；宿主 API 与 Kotlin 类型始终与宿主共享。 */
private class PluginClassLoader(urls: Array<URL>) : URLClassLoader(urls, PluginEntry::class.java.classLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
        val loaded = findLoadedClass(name) ?: if (sharedPackages.any { name.startsWith(it) }) {
            try {
                super.loadClass(name, false)
            } catch (_: ClassNotFoundException) {
                findClass(name)
            }
        } else {
            try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                parent.loadClass(name)
            }
        }
        if (resolve) resolveClass(loaded)
        loaded
    }

    override fun getResource(name: String): URL? = findResource(name) ?: parent.getResource(name)

    override fun getResources(name: String): Enumeration<URL> {
        val resources = linkedSetOf<URL>()
        resources.addAll(Collections.list(findResources(name)))
        resources.addAll(Collections.list(parent.getResources(name)))
        return Collections.enumeration(resources)
    }

    companion object {
        private val sharedPackages = listOf(
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "org.w3c.dom.", "org.xml.sax.", "kotlin.", "kotlinx.", "com.eva_cli.core.",
            // Optional plugin host APIs are shared when a host provides them.
            "com.eva_cli.cmd.api.",
        )
    }
}
