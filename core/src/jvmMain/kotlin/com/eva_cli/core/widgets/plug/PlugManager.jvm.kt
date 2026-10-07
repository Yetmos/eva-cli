package com.eva_cli.core.widgets.plug

import com.eva_cli.core.runtime.Configure
import com.eva_cli.core.utils.file.FileUtils
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipFile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 仅删除指定版本，不依赖安装清单，也不跟随安装目录或其内容中的符号链接。 */
internal actual fun uninstallLocalPlugin(id: String, version: String): Boolean {
    require(isSafeInstallSegment(id)) { "插件 id 不是安全的目录名：$id" }
    require(isSafeInstallSegment(version)) { "插件 version 不是安全的目录名：$version" }

    try {
        val pluginsDirectory = Path.of(FileUtils.rootPath())
            .resolve(Configure.PLUGINS_DIR)
            .toAbsolutePath()
            .normalize()
        val pluginDirectory = pluginsDirectory.resolve(id)
        val versionDirectory = pluginDirectory.resolve(version)
        for (directory in listOf(pluginsDirectory, pluginDirectory, versionDirectory)) {
            if (Files.notExists(directory, NOFOLLOW_LINKS)) return false
            check(Files.isDirectory(directory, NOFOLLOW_LINKS)) {
                "插件安装路径不是普通目录：$directory"
            }
        }
        deleteRecursively(versionDirectory)
        return true
    } catch (error: InvalidPathException) {
        throw IllegalArgumentException("插件卸载路径无效：$id:$version", error)
    } catch (error: Exception) {
        throw IllegalStateException("卸载插件失败：$id:$version", error)
    }
}

/** 从安装目录读取当前有效的 JVM 插件，不创建目录或修改安装记录。 */
internal actual fun listInstalledPlugins(): List<InstalledPluginInfo> {
    val pluginsDirectory = Path.of(FileUtils.rootPath())
        .resolve(Configure.PLUGINS_DIR)
        .toAbsolutePath()
        .normalize()
    if (Files.notExists(pluginsDirectory, NOFOLLOW_LINKS)) return emptyList()
    if (!Files.isDirectory(pluginsDirectory, NOFOLLOW_LINKS) || !Files.isReadable(pluginsDirectory)) {
        throw IllegalStateException("插件安装根目录不可读或不是普通目录：$pluginsDirectory")
    }

    val installed = mutableListOf<InstalledPluginInfo>()
    try {
        Files.newDirectoryStream(pluginsDirectory).use { pluginDirectories ->
            for (pluginDirectory in pluginDirectories) {
                if (pluginDirectory.fileName.toString().startsWith(".eva-install-") ||
                    !Files.isDirectory(pluginDirectory, NOFOLLOW_LINKS)
                ) continue

                Files.newDirectoryStream(pluginDirectory).use { versionDirectories ->
                    for (versionDirectory in versionDirectories) {
                        if (!Files.isDirectory(versionDirectory, NOFOLLOW_LINKS) ||
                            !Files.isRegularFile(versionDirectory.resolve("plugin.json"), NOFOLLOW_LINKS)
                        ) continue

                        try {
                            val manifest = readManifest(versionDirectory)
                            if (manifest.id != pluginDirectory.fileName.toString() ||
                                manifest.version != versionDirectory.fileName.toString()
                            ) continue
                            validateManifest(manifest, versionDirectory)
                            installed.add(InstalledPluginInfo(manifest, versionDirectory.toString()))
                        } catch (_: PluginInstallException) {
                            // 不完整或损坏的安装不影响其他有效插件的查询。
                        }
                    }
                }
            }
        }
    } catch (error: Exception) {
        throw IllegalStateException("无法读取已安装插件列表：$pluginsDirectory", error)
    }
    return installed.sortedWith(compareBy({ it.manifest.id }, { it.manifest.version }))
}

/** JVM 本地 .eva 安装实现。 */
internal actual fun installLocalPlugin(source: PluginSource.File): InstalledPlugin {
    val packagePath = resolveSource(source.uri)
    requireRegularFile(packagePath, "插件包")
    val digest = sha256(packagePath)
    validateExpectedDigest(source.sha256, digest)

    val pluginsDirectory = Path.of(FileUtils.rootPath())
        .resolve(Configure.PLUGINS_DIR)
        .toAbsolutePath()
        .normalize()
    Files.createDirectories(pluginsDirectory)

    val stagingDirectory = Files.createTempDirectory(pluginsDirectory, ".eva-install-")
    var committed = false
    try {
        extractZip(packagePath, stagingDirectory)
        val manifest = readManifest(stagingDirectory)
        validateManifest(manifest, stagingDirectory)

        val versionDirectory = pluginsDirectory.resolve(manifest.id).resolve(manifest.version)
        val existing = existingInstallation(versionDirectory, manifest)
        if (existing == true) {
            return InstalledPlugin(manifest, versionDirectory.toString(), alreadyInstalled = true, sha256 = digest)
        }
        if (Files.exists(versionDirectory)) {
            throw PluginInstallException(
                "插件 ${manifest.id}:${manifest.version} 已存在，但其清单不匹配；请先卸载或选择其他版本",
            )
        }

        Files.createDirectories(versionDirectory.parent)
        try {
            moveIntoPlace(stagingDirectory, versionDirectory)
        } catch (error: FileAlreadyExistsException) {
            // 处理并发安装：另一进程可能在检查后抢先提交了相同版本。
            if (existingInstallation(versionDirectory, manifest) != true) throw error
            return InstalledPlugin(manifest, versionDirectory.toString(), alreadyInstalled = true, sha256 = digest)
        }
        committed = true
        return InstalledPlugin(manifest, versionDirectory.toString(), alreadyInstalled = false, sha256 = digest)
    } catch (error: PluginInstallException) {
        throw error
    } catch (error: Exception) {
        throw PluginInstallException("安装 .eva 插件失败：${packagePath}", error)
    } finally {
        if (!committed) deleteRecursively(stagingDirectory)
    }
}

private val manifestJson = Json {
    // 允许发布工具添加宿主尚未使用的描述字段；必需字段仍由序列化模型校验。
    ignoreUnknownKeys = true
}

private fun resolveSource(uriOrPath: String): Path {
    if (uriOrPath.isBlank()) throw PluginInstallException("插件文件路径不能为空")
    return try {
        val path = if (uriOrPath.startsWith("file:", ignoreCase = true)) {
            Path.of(URI(uriOrPath))
        } else {
            Path.of(uriOrPath)
        }
        path.toAbsolutePath().normalize()
    } catch (error: Exception) {
        throw PluginInstallException("无效的插件文件路径：$uriOrPath", error)
    }
}

private fun requireRegularFile(path: Path, description: String) {
    if (!Files.isRegularFile(path)) {
        throw PluginInstallException("$description 不存在或不是普通文件：$path")
    }
}

private fun validateExpectedDigest(expected: String?, actual: String) {
    if (expected == null) return
    val normalized = expected.trim().lowercase()
    if (!Regex("[0-9a-f]{64}").matches(normalized)) {
        throw PluginInstallException("sha256 必须是 64 位十六进制字符串")
    }
    if (normalized != actual) {
        throw PluginInstallException("插件包 sha256 校验失败：期望 $normalized，实际 $actual")
    }
}

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun extractZip(archive: Path, destination: Path) {
    val written = HashSet<Path>()
    try {
        ZipFile(archive.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val relative = safeArchivePath(entry.name)
                val output = destination.resolve(relative).normalize()
                if (!output.startsWith(destination)) {
                    throw PluginInstallException("插件包包含越界路径：${entry.name}")
                }
                if (!written.add(output)) {
                    throw PluginInstallException("插件包包含重复路径：${entry.name}")
                }
                if (entry.isDirectory) {
                    Files.createDirectories(output)
                    continue
                }
                Files.createDirectories(output.parent)
                zip.getInputStream(entry).use { input ->
                    Files.newOutputStream(output).use { outputStream -> input.copyTo(outputStream) }
                }
            }
        }
    } catch (error: PluginInstallException) {
        throw error
    } catch (error: Exception) {
        throw PluginInstallException("无法解压 .eva 插件包：$archive", error)
    }
}

private fun safeArchivePath(entryName: String): Path {
    if (entryName.isBlank() || entryName.indexOf('\u0000') >= 0) {
        throw PluginInstallException("插件包包含无效路径：$entryName")
    }
    val portable = entryName.replace('\\', '/')
    if (portable.startsWith('/') || Regex("^[A-Za-z]:.*").matches(portable)) {
        throw PluginInstallException("插件包包含绝对路径：$entryName")
    }
    val relative = try {
        Path.of(portable).normalize()
    } catch (error: InvalidPathException) {
        throw PluginInstallException("插件包包含无效路径：$entryName", error)
    }
    if (relative.isAbsolute || relative == Path.of("..") || relative.startsWith("..")) {
        throw PluginInstallException("插件包包含越界路径：$entryName")
    }
    return relative
}

private fun readManifest(stagingDirectory: Path): PluginManifest {
    val manifestPath = stagingDirectory.resolve("plugin.json")
    if (!Files.isRegularFile(manifestPath)) {
        throw PluginInstallException("插件包根目录缺少 plugin.json")
    }
    return try {
        manifestJson.decodeFromString<PluginManifest>(Files.readString(manifestPath))
    } catch (error: SerializationException) {
        throw PluginInstallException("plugin.json 格式无效", error)
    } catch (error: Exception) {
        throw PluginInstallException("无法读取 plugin.json", error)
    }
}

private fun validateManifest(manifest: PluginManifest, stagingDirectory: Path) {
    if (manifest.formatVersion != CURRENT_PLUGIN_FORMAT_VERSION) {
        throw PluginInstallException(
            "不支持的 .eva formatVersion=${manifest.formatVersion}，当前版本为 $CURRENT_PLUGIN_FORMAT_VERSION",
        )
    }
    if (manifest.apiVersion != CURRENT_PLUGIN_API_VERSION) {
        throw PluginInstallException(
            "不兼容的插件 apiVersion=${manifest.apiVersion}，当前 API 版本为 $CURRENT_PLUGIN_API_VERSION",
        )
    }
    validateInstallSegment(manifest.id, "插件 id")
    validateInstallSegment(manifest.version, "插件 version")
    if (manifest.name.isBlank()) throw PluginInstallException("插件 name 不能为空")

    val jvm = manifest.platforms.jvm ?: throw PluginInstallException("插件必须声明 platforms.jvm")
    jvm.minJavaVersion?.let { minimum ->
        if (minimum < 1) {
            throw PluginInstallException("JVM minJavaVersion 必须是正整数：$minimum")
        }
        val runtimeVersion = Runtime.version().feature()
        if (runtimeVersion < minimum) {
            throw PluginInstallException(
                "插件要求 Java $minimum 或更高版本，当前运行时为 Java $runtimeVersion",
            )
        }
    }
    if (jvm.artifact.isBlank()) throw PluginInstallException("JVM artifact 不能为空")
    val artifact = resolveManifestPath(stagingDirectory, jvm.artifact, "JVM artifact")
    if (!Files.isRegularFile(artifact)) {
        throw PluginInstallException("JVM artifact 不存在或不是普通文件：${jvm.artifact}")
    }
    if (jvm.entryClass.isBlank()) throw PluginInstallException("JVM entryClass 不能为空")
    val entryClass = validateEntryClass(jvm.entryClass)
    val classEntry = entryClass.replace('.', '/') + ".class"
    try {
        JarFile(artifact.toFile()).use { jar ->
            if (jar.getJarEntry(classEntry) == null) {
                throw PluginInstallException("JVM entryClass 不存在于 artifact：${jvm.entryClass}")
            }
        }
    } catch (error: PluginInstallException) {
        throw error
    } catch (error: Exception) {
        throw PluginInstallException("无法读取 JVM artifact：${jvm.artifact}", error)
    }

    jvm.dependencies?.let {
        if (!Files.isDirectory(resolveManifestPath(stagingDirectory, it, "JVM dependencies"))) {
            throw PluginInstallException("JVM dependencies 目录不存在：$it")
        }
    }
    manifest.resources?.let {
        if (!Files.exists(resolveManifestPath(stagingDirectory, it, "resources"))) {
            throw PluginInstallException("resources 路径不存在：$it")
        }
    }
}

private fun validateInstallSegment(value: String, label: String) {
    if (!isSafeInstallSegment(value)) {
        throw PluginInstallException("$label 不是安全的目录名：$value")
    }
}

private fun isSafeInstallSegment(value: String): Boolean =
    value.isNotBlank() && value == value.trim() && value != "." && value != ".." &&
        value.none { it == '/' || it == '\\' || it == ':' || it.isISOControl() }

private fun validateEntryClass(value: String): String {
    val segments = value.split('.')
    if (segments.any { it.isEmpty() || !it.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*")) }) {
        throw PluginInstallException("JVM entryClass 不是有效的类名：$value")
    }
    return value
}

private fun resolveManifestPath(root: Path, value: String, label: String): Path {
    val relative = try {
        safeArchivePath(value)
    } catch (error: PluginInstallException) {
        throw PluginInstallException("$label 路径无效：$value", error)
    }
    val resolved = root.resolve(relative).normalize()
    if (!resolved.startsWith(root)) throw PluginInstallException("$label 路径越界：$value")
    return resolved
}

private fun existingInstallation(path: Path, expected: PluginManifest): Boolean? {
    if (!Files.exists(path)) return null
    if (!Files.isDirectory(path)) return false
    val manifestPath = path.resolve("plugin.json")
    if (!Files.isRegularFile(manifestPath)) return false
    return try {
        manifestJson.decodeFromString<PluginManifest>(Files.readString(manifestPath)) == expected
    } catch (_: Exception) {
        false
    }
}

private fun moveIntoPlace(staging: Path, destination: Path) {
    try {
        Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        // 同一 Plugins 文件系统通常支持原子目录移动；不支持时仍禁止覆盖已有目录。
        Files.move(staging, destination)
    }
}

private fun deleteRecursively(path: Path) {
    if (!Files.exists(path)) return
    Files.walk(path).use { stream ->
        stream.sorted(Comparator.reverseOrder<Path>()).forEach { Files.deleteIfExists(it) }
    }
}
