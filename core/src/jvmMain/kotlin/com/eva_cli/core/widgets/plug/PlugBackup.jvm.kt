package com.eva_cli.core.widgets.plug

import com.eva_cli.core.runtime.Configure
import com.eva_cli.core.utils.file.FileUtils
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SNAPSHOT_FILE = "plugins.zip"
private const val SNAPSHOT_INDEX = "backup.json"
private const val SNAPSHOT_PLUGINS = "Plugins/"

@Serializable
private data class PluginSnapshot(
    val formatVersion: Int,
    val plugins: List<SnapshotPlugin>,
    val entries: List<SnapshotEntry>,
)

@Serializable
private data class SnapshotPlugin(val id: String, val version: String)

@Serializable
private data class SnapshotEntry(val path: String, val sha256: String?)

internal actual fun backupLocalPlugins() {
    try {
        val root = Path.of(FileUtils.rootPath()).toAbsolutePath().normalize()
        val pluginsDirectory = root.resolve(Configure.PLUGINS_DIR)
        val backupDirectory = root.resolve(Configure.BACKUP)
        requireDirectoryOrAbsent(pluginsDirectory)
        requireDirectoryOrAbsent(backupDirectory)
        Files.createDirectories(backupDirectory)
        val snapshot = backupDirectory.resolve(SNAPSHOT_FILE)
        requireSnapshotOrAbsent(snapshot)
        val installed = listInstalledPlugins()
        val temporary = Files.createTempFile(backupDirectory, ".eva-backup-", ".zip")
        try {
            val entries = mutableListOf<SnapshotEntry>()
            val directories = mutableSetOf<String>()
            ZipOutputStream(Files.newOutputStream(temporary)).use { zip ->
                fun addDirectory(name: String) {
                    if (!directories.add(name)) return
                    requireCanonicalSnapshotPath(name)
                    zip.putNextEntry(ZipEntry(name))
                    zip.closeEntry()
                    entries.add(SnapshotEntry(name, null))
                }
                addDirectory(SNAPSHOT_PLUGINS)
                for (plugin in installed) {
                    addDirectory("$SNAPSHOT_PLUGINS${plugin.manifest.id}/")
                    addDirectory(
                        "$SNAPSHOT_PLUGINS${plugin.manifest.id}/${Configure.PLUGIN_VERSIONS_DIR}/",
                    )
                    val directory = Path.of(plugin.directory)
                    Files.walk(directory).use { paths ->
                        paths.sorted().forEach { path ->
                            val relative = pluginsDirectory.relativize(path).joinToString("/")
                            val name = "$SNAPSHOT_PLUGINS$relative"
                            when {
                                Files.isDirectory(path, NOFOLLOW_LINKS) -> addDirectory("$name/")
                                Files.isRegularFile(path, NOFOLLOW_LINKS) -> {
                                    requireCanonicalSnapshotPath(name)
                                    zip.putNextEntry(ZipEntry(name))
                                    Files.newInputStream(path).use { it.copyTo(zip) }
                                    zip.closeEntry()
                                    entries.add(SnapshotEntry(name, sha256(path)))
                                }
                                else -> error("插件包含符号链接或特殊文件：$path")
                            }
                        }
                    }
                }
                val index = PluginSnapshot(
                    formatVersion = 1,
                    plugins = installed.map { SnapshotPlugin(it.manifest.id, it.manifest.version) },
                    entries = entries,
                )
                zip.putNextEntry(ZipEntry(SNAPSHOT_INDEX))
                zip.write(Json.encodeToString(index).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            // 发布前完整复验，避免备份期间变化的文件产生不可恢复的快照。
            val verification = Files.createTempDirectory(backupDirectory, ".eva-backup-check-")
            try {
                stageSnapshot(temporary, verification)
            } finally {
                deleteRecursively(verification)
            }
            requireDirectoryOrAbsent(backupDirectory)
            requireSnapshotOrAbsent(snapshot)
            // 不支持原子替换时保留旧快照并报错，不能先删除上一次可用备份。
            Files.move(temporary, snapshot, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    } catch (error: Exception) {
        throw IllegalStateException("备份插件失败", error)
    }
}

internal actual fun restoreLocalPlugins() {
    try {
        val root = Path.of(FileUtils.rootPath()).toAbsolutePath().normalize()
        val pluginsDirectory = root.resolve(Configure.PLUGINS_DIR)
        val backupDirectory = root.resolve(Configure.BACKUP)
        requireDirectoryOrAbsent(pluginsDirectory)
        check(Files.isDirectory(backupDirectory, NOFOLLOW_LINKS)) { "备份目录不存在或不是普通目录：$backupDirectory" }
        val snapshot = backupDirectory.resolve(SNAPSHOT_FILE)
        check(Files.isRegularFile(snapshot, NOFOLLOW_LINKS)) { "插件快照不存在或不是普通文件：$snapshot" }
        val staging = Files.createTempDirectory(root, ".eva-restore-")
        val previous = staging.resolve("previous")
        val movedExisting = mutableListOf<Pair<Path, Path>>()
        val movedRestored = mutableListOf<Path>()
        val removedEmptyPluginDirectories = mutableListOf<Path>()
        var rollbackFailed = false
        try {
            stageSnapshot(snapshot, staging)
            requireDirectoryOrAbsent(pluginsDirectory)
            if (Files.notExists(pluginsDirectory, NOFOLLOW_LINKS)) {
                Files.createDirectories(pluginsDirectory)
            }
            try {
                // Keep each plugin's Data/ and Cache/ directories in place. Only the
                // versioned installation subtree participates in a plugin snapshot.
                Files.newDirectoryStream(pluginsDirectory).use { stream ->
                    stream.toList().sorted().forEach { pluginDirectory ->
                        check(Files.isDirectory(pluginDirectory, NOFOLLOW_LINKS)) {
                            "插件目录包含非普通目录：$pluginDirectory"
                        }
                        val versions = pluginDirectory.resolve(Configure.PLUGIN_VERSIONS_DIR)
                        if (Files.notExists(versions, NOFOLLOW_LINKS)) return@forEach
                        check(Files.isDirectory(versions, NOFOLLOW_LINKS)) {
                            "插件版本目录不是普通目录：$versions"
                        }
                        val previousVersions = previous.resolve(Configure.PLUGINS_DIR)
                            .resolve(pluginDirectory.fileName.toString())
                            .resolve(Configure.PLUGIN_VERSIONS_DIR)
                        Files.createDirectories(previousVersions.parent)
                        moveIntoPlace(versions, previousVersions)
                        movedExisting.add(versions to previousVersions)
                    }
                }

                val stagedPlugins = staging.resolve(Configure.PLUGINS_DIR)
                Files.newDirectoryStream(stagedPlugins).use { stream ->
                    stream.toList().forEach { stagedPlugin ->
                        check(Files.isDirectory(stagedPlugin, NOFOLLOW_LINKS)) {
                            "插件快照包含非普通目录：$stagedPlugin"
                        }
                        val id = stagedPlugin.fileName.toString()
                        val stagedVersions = stagedPlugin.resolve(Configure.PLUGIN_VERSIONS_DIR)
                        check(Files.isDirectory(stagedVersions, NOFOLLOW_LINKS)) {
                            "插件快照缺少版本目录：$id"
                        }
                        val targetPlugin = pluginsDirectory.resolve(id)
                        if (Files.notExists(targetPlugin, NOFOLLOW_LINKS)) {
                            Files.createDirectory(targetPlugin)
                        } else {
                            check(Files.isDirectory(targetPlugin, NOFOLLOW_LINKS)) {
                                "插件目录不是普通目录：$targetPlugin"
                            }
                        }
                        val targetVersions = targetPlugin.resolve(Configure.PLUGIN_VERSIONS_DIR)
                        moveIntoPlace(stagedVersions, targetVersions)
                        movedRestored.add(targetVersions)
                    }
                }

                // Do not leave stale ID directories behind after their versions were
                // removed. A directory containing Data/ or Cache is retained.
                Files.newDirectoryStream(pluginsDirectory).use { stream ->
                    stream.toList().forEach { pluginDirectory ->
                        if (Files.newDirectoryStream(pluginDirectory).use { !it.iterator().hasNext() }) {
                            Files.delete(pluginDirectory)
                            removedEmptyPluginDirectories.add(pluginDirectory)
                        }
                    }
                }
            } catch (error: Exception) {
                movedRestored.asReversed().forEach { restored ->
                    runCatching { deleteRecursively(restored) }
                        .onFailure { rollbackError ->
                            rollbackFailed = true
                            error.addSuppressed(rollbackError)
                        }
                }
                removedEmptyPluginDirectories.forEach { directory ->
                    runCatching { Files.createDirectories(directory) }
                        .onFailure { rollbackError ->
                            rollbackFailed = true
                            error.addSuppressed(rollbackError)
                        }
                }
                movedExisting.asReversed().forEach { (original, saved) ->
                    runCatching {
                        Files.createDirectories(original.parent)
                        moveIntoPlace(saved, original)
                    }.onFailure { rollbackError ->
                        rollbackFailed = true
                        error.addSuppressed(IllegalStateException("回滚失败，原插件保留在：$saved", rollbackError))
                    }
                }
                throw error
            }
        } finally {
            // 提交成功或回滚完成后，清理快照 staging 目录；回滚失败时保留现场。
            if (!rollbackFailed) runCatching { deleteRecursively(staging) }
        }
    } catch (error: Exception) {
        throw IllegalStateException("恢复插件失败", error)
    }
}

private fun stageSnapshot(archive: Path, destination: Path) {
    val index = ZipFile(archive.toFile()).use { zip ->
        val names = mutableSetOf<String>()
        val resolvedNames = mutableSetOf<Path>()
        val zipEntries = zip.entries()
        while (zipEntries.hasMoreElements()) {
            val entry = zipEntries.nextElement()
            requireCanonicalSnapshotPath(entry.name)
            check(names.add(entry.name) && resolvedNames.add(safeArchivePath(entry.name))) {
                "插件快照包含重复路径：${entry.name}"
            }
        }
        val metadata = zip.getEntry(SNAPSHOT_INDEX)
        check(metadata != null && !metadata.isDirectory) { "插件快照缺少 $SNAPSHOT_INDEX" }
        val snapshot = zip.getInputStream(metadata).bufferedReader(Charsets.UTF_8).use {
            Json.decodeFromString<PluginSnapshot>(it.readText())
        }
        validateSnapshotIndex(snapshot)
        check(names == snapshot.entries.map { it.path }.toSet() + SNAPSHOT_INDEX) {
            "插件快照文件清单不匹配"
        }
        extractZip(zip, destination)
        snapshot
    }
    for (entry in index.entries) {
        val path = destination.resolve(safeArchivePath(entry.path))
        if (entry.sha256 == null) {
            check(Files.isDirectory(path, NOFOLLOW_LINKS)) { "插件快照缺少目录：${entry.path}" }
        } else {
            check(Files.isRegularFile(path, NOFOLLOW_LINKS) && sha256(path) == entry.sha256) {
                "插件快照文件校验失败：${entry.path}"
            }
        }
    }
    for (plugin in index.plugins) {
        val directory = destination.resolve(Configure.PLUGINS_DIR)
            .resolve(plugin.id)
            .resolve(Configure.PLUGIN_VERSIONS_DIR)
            .resolve(plugin.version)
        val manifest = readManifest(directory)
        check(manifest.id == plugin.id && manifest.version == plugin.version) { "插件快照清单与目录不匹配" }
        validateManifest(manifest, directory)
    }
}

private fun validateSnapshotIndex(index: PluginSnapshot) {
    check(index.formatVersion == 1) { "不支持的插件快照版本：${index.formatVersion}" }
    check(index.plugins.distinct().size == index.plugins.size) { "插件快照包含重复插件版本" }
    val pluginDirectories = mutableSetOf(SNAPSHOT_PLUGINS)
    val versionDirectories = index.plugins.map { plugin ->
        check(isSafeInstallSegment(plugin.id) && isSafeInstallSegment(plugin.version)) { "插件快照包含无效插件目录" }
        pluginDirectories.add("$SNAPSHOT_PLUGINS${plugin.id}/")
        pluginDirectories.add(
            "$SNAPSHOT_PLUGINS${plugin.id}/${Configure.PLUGIN_VERSIONS_DIR}/",
        )
        "$SNAPSHOT_PLUGINS${plugin.id}/${Configure.PLUGIN_VERSIONS_DIR}/${plugin.version}/"
    }
    val paths = mutableSetOf<String>()
    for (entry in index.entries) {
        requireCanonicalSnapshotPath(entry.path)
        check(paths.add(entry.path)) { "插件快照清单包含重复路径：${entry.path}" }
        check(entry.path in pluginDirectories || versionDirectories.any { entry.path.startsWith(it) }) {
            "插件快照包含未声明的插件路径：${entry.path}"
        }
        check(if (entry.path.endsWith('/')) entry.sha256 == null else entry.sha256?.matches(Regex("[0-9a-f]{64}")) == true) {
            "插件快照文件校验值无效：${entry.path}"
        }
    }
    check(paths.containsAll(pluginDirectories + versionDirectories)) { "插件快照缺少插件目录" }
}

private fun requireCanonicalSnapshotPath(name: String) {
    val relative = safeArchivePath(name)
    val canonical = relative.joinToString("/") + if (name.endsWith('/')) "/" else ""
    check(relative.toString().isNotEmpty() && canonical == name) { "插件快照包含非标准路径：$name" }
}

private fun requireDirectoryOrAbsent(path: Path) {
    check(Files.notExists(path, NOFOLLOW_LINKS) || Files.isDirectory(path, NOFOLLOW_LINKS)) {
        "插件路径不是普通目录：$path"
    }
}

private fun requireSnapshotOrAbsent(path: Path) {
    check(Files.notExists(path, NOFOLLOW_LINKS) || Files.isRegularFile(path, NOFOLLOW_LINKS)) {
        "插件快照路径不是普通文件：$path"
    }
}
