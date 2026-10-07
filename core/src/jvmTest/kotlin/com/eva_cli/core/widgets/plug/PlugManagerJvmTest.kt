package com.eva_cli.core.widgets.plug

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlugManagerJvmTest {
    @Test
    fun restoresAllPluginVersionsAndTheirContentsFromTheSnapshot() = withRuntimeRoot { root ->
        val first = writeInstalledFixture(root, "com.example.test")
        val secondArchive = createEva(root.resolve("second.eva"), version = "2.0.0")
        PlugManager.installPlugin(PluginSource.File(secondArchive.toString()))
        writeInstalledFixture(root, "com.example.other")
        Files.createDirectories(first.resolve("resources/empty"))
        Files.write(first.resolve("resources/data.bin"), byteArrayOf(0, 1, -1, 127))
        val source = Files.createDirectories(first.resolve("src/commonMain/kotlin"))
        Files.writeString(source.resolve("Example.kt"), "// saved source")
        val expected = directoryContents(root.resolve("Plugins"))
        val data = Files.createDirectories(root.resolve("Data"))
        Files.writeString(data.resolve("keep.txt"), "runtime data")

        PlugManager.backup()
        assertTrue(Files.isRegularFile(root.resolve("Backup/plugins.zip")))
        assertEquals(expected, directoryContents(root.resolve("Plugins")))

        PlugManager.uninstallPlugin("com.example.test", "1.0.0")
        Files.writeString(root.resolve("Plugins/com.example.test/2.0.0/prebuilt/jvm/plugin.jar"), "broken")
        writeInstalledFixture(root, "com.example.new")

        PlugManager.restore()
        assertEquals(expected, directoryContents(root.resolve("Plugins")))
        assertEquals(3, PlugManager.installedList.size)
        assertEquals("runtime data", Files.readString(data.resolve("keep.txt")))

        PlugManager.restore()
        assertEquals(expected, directoryContents(root.resolve("Plugins")))
    }

    @Test
    fun restoresAnEmptySnapshotAndCanRestoreIntoAMissingPluginsDirectory() = withRuntimeRoot { root ->
        PlugManager.backup()
        writeInstalledFixture(root, "com.example.new")

        PlugManager.restore()
        assertTrue(PlugManager.installedList.isEmpty())
        assertTrue(directoryContents(root.resolve("Plugins")).isEmpty())

        writeInstalledFixture(root, "com.example.saved")
        PlugManager.backup()
        assertTrue(root.resolve("Plugins").toFile().deleteRecursively())

        PlugManager.restore()
        assertEquals("com.example.saved", PlugManager.installedList.single().manifest.id)
    }

    @Test
    fun aSuccessfulBackupReplacesThePreviousSnapshot() = withRuntimeRoot { root ->
        writeInstalledFixture(root, "com.example.old")
        PlugManager.backup()
        PlugManager.uninstallPlugin("com.example.old", "1.0.0")
        writeInstalledFixture(root, "com.example.latest")
        PlugManager.backup()
        PlugManager.uninstallPlugin("com.example.latest", "1.0.0")

        PlugManager.restore()

        assertEquals("com.example.latest", PlugManager.installedList.single().manifest.id)
    }

    @Test
    fun backsUpOnlyValidInstalledVersionsAndExcludesInstallationStaging() = withRuntimeRoot { root ->
        writeInstalledFixture(root, "com.example.saved")
        val broken = writeInstalledFixture(root, "com.example.broken")
        Files.writeString(broken.resolve("plugin.json"), "broken json")
        writeInstalledFixture(root, ".eva-install-incomplete")

        PlugManager.backup()
        PlugManager.restore()

        assertEquals("com.example.saved", PlugManager.installedList.single().manifest.id)
        assertEquals(setOf("com.example.saved"), root.resolve("Plugins").toFile().list()?.toSet())
    }

    @Test
    fun aMissingSnapshotDoesNotModifyPluginsOrCreateABackupDirectory() = withRuntimeRoot { root ->
        writeInstalledFixture(root, "com.example.current")
        val expected = directoryContents(root.resolve("Plugins"))

        assertFailsWith<IllegalStateException> { PlugManager.restore() }

        assertEquals(expected, directoryContents(root.resolve("Plugins")))
        assertFalse(Files.exists(root.resolve("Backup")))
    }

    @Test
    fun rejectsCorruptedAndUnrelatedZipFilesWithoutReplacingPlugins() {
        for (isZip in listOf(false, true)) {
            withRuntimeRoot { root ->
                writeInstalledFixture(root, "com.example.current")
                val expected = directoryContents(root.resolve("Plugins"))
                Files.createDirectory(root.resolve("Backup"))
                val snapshot = root.resolve("Backup/plugins.zip")
                if (isZip) {
                    ZipOutputStream(Files.newOutputStream(snapshot)).use { zip ->
                        zip.putNextEntry(ZipEntry("Plugins/"))
                        zip.closeEntry()
                    }
                } else {
                    Files.writeString(snapshot, "not a snapshot")
                }

                assertFailsWith<IllegalStateException> { PlugManager.restore() }

                assertEquals(expected, directoryContents(root.resolve("Plugins")))
            }
        }
    }

    @Test
    fun rejectsChangedOrMissingSnapshotFilesBeforeReplacingPlugins() {
        for (removeFile in listOf(false, true)) {
            withRuntimeRoot { root ->
                val installed = writeInstalledFixture(root, "com.example.saved")
                Files.writeString(installed.resolve("README.md"), "original resource")
                PlugManager.backup()
                rewriteSnapshot(root) { name, bytes ->
                    if (name.endsWith("/README.md")) {
                        if (removeFile) null else "tampered resource".encodeToByteArray()
                    } else {
                        bytes
                    }
                }
                writeInstalledFixture(root, "com.example.current")
                val expected = directoryContents(root.resolve("Plugins"))

                assertFailsWith<IllegalStateException> { PlugManager.restore() }

                assertEquals(expected, directoryContents(root.resolve("Plugins")))
            }
        }
    }

    @Test
    fun rejectsUnexpectedAndTraversingSnapshotEntriesWithoutWritingOutsidePlugins() = withRuntimeRoot { root ->
        writeInstalledFixture(root, "com.example.saved")
        PlugManager.backup()
        val snapshot = root.resolve("Backup/plugins.zip")
        val original = Files.readAllBytes(snapshot)
        for (entry in listOf("../escaped.txt", "Plugins/../../escaped.txt", "unexpected.txt")) {
            Files.write(snapshot, original)
            rewriteSnapshot(root, extraEntry = entry) { _, bytes -> bytes }
            val expected = directoryContents(root.resolve("Plugins"))

            assertFailsWith<IllegalStateException> { PlugManager.restore() }

            assertEquals(expected, directoryContents(root.resolve("Plugins")))
            assertFalse(Files.exists(root.resolve("escaped.txt")))
        }
    }

    @Test
    fun failedBackupKeepsThePreviousSnapshotAndDoesNotFollowInternalLinks() = withRuntimeRoot { root ->
        if (System.getProperty("os.name").startsWith("Windows")) return@withRuntimeRoot
        val installed = writeInstalledFixture(root, "com.example.saved")
        PlugManager.backup()
        val snapshot = root.resolve("Backup/plugins.zip")
        val original = Files.readAllBytes(snapshot)
        val outside = Files.createDirectory(root.resolve("outside"))
        Files.writeString(outside.resolve("keep.txt"), "private content")
        Files.createSymbolicLink(installed.resolve("linked-resource"), outside)

        assertFailsWith<IllegalStateException> { PlugManager.backup() }

        assertContentEquals(original, Files.readAllBytes(snapshot))
        assertEquals("private content", Files.readString(outside.resolve("keep.txt")))
        Files.delete(installed.resolve("linked-resource"))
        PlugManager.restore()
        assertEquals("com.example.saved", PlugManager.installedList.single().manifest.id)
    }

    @Test
    fun refusesFileAndSymlinkCollisionsAtSnapshotBoundaries() {
        for (relative in listOf("Backup", "Backup/plugins.zip", "Plugins")) {
            for (linked in listOf(false, true)) {
                if (linked && System.getProperty("os.name").startsWith("Windows")) continue
                withRuntimeRoot { root ->
                    PlugManager.backup()
                    val path = root.resolve(relative)
                    if (Files.exists(path)) assertTrue(path.toFile().deleteRecursively())
                    Files.createDirectories(path.parent)
                    val outside = root.resolve("outside.txt")
                    Files.writeString(outside, "keep outside")
                    if (linked) {
                        Files.createSymbolicLink(path, outside)
                    } else if (relative.endsWith(".zip")) {
                        Files.createDirectory(path)
                        Files.writeString(path.resolve("keep.txt"), "keep directory")
                    } else {
                        Files.writeString(path, "keep file")
                    }

                    assertFailsWith<IllegalStateException> { PlugManager.backup() }
                    assertFailsWith<IllegalStateException> { PlugManager.restore() }

                    assertEquals("keep outside", Files.readString(outside))
                    if (linked) {
                        assertTrue(Files.isSymbolicLink(path))
                    } else if (relative.endsWith(".zip")) {
                        assertEquals("keep directory", Files.readString(path.resolve("keep.txt")))
                    } else {
                        assertEquals("keep file", Files.readString(path))
                    }
                }
            }
        }
    }

    @Test
    fun uninstallsOnlyTheRequestedVersionAndRefreshesTheInstalledList() = withRuntimeRoot { root ->
        val first = writeInstalledFixture(root, "com.example.test")
        val archive = createEva(root.resolve("second.eva"), version = "2.0.0")
        val second = PlugManager.installPlugin(PluginSource.File(archive.toString()))
        val other = writeInstalledFixture(root, "com.example.other")
        val snapshot = PlugManager.installedList

        assertTrue(PlugManager.uninstallPlugin("com.example.test", "1.0.0"))

        assertFalse(Files.exists(first))
        assertTrue(Files.isRegularFile(Path.of(second.directory).resolve("plugin.json")))
        assertTrue(Files.isRegularFile(other.resolve("plugin.json")))
        assertEquals(
            listOf("com.example.other:1.0.0", "com.example.test:2.0.0"),
            PlugManager.installedList.map { "${it.manifest.id}:${it.manifest.version}" },
        )
        assertEquals(3, snapshot.size)
        assertFalse(PlugManager.uninstallPlugin("com.example.test", "1.0.0"))
    }

    @Test
    fun uninstallingMissingPluginsDoesNotCreateDirectories() = withRuntimeRoot { root ->
        val plugins = root.resolve("Plugins")
        assertFalse(PlugManager.uninstallPlugin("com.example.missing", "1.0.0"))
        assertFalse(Files.exists(plugins))

        Files.createDirectory(plugins)
        assertFalse(PlugManager.uninstallPlugin("com.example.missing", "1.0.0"))
        assertFalse(Files.exists(plugins.resolve("com.example.missing")))

        val parent = Files.createDirectory(plugins.resolve("com.example.missing"))
        assertFalse(PlugManager.uninstallPlugin("com.example.missing", "1.0.0"))
        assertTrue(Files.list(parent).use { it.findAny().isEmpty })
    }

    @Test
    fun uninstallsBrokenIncompatibleAndIncompleteDirectoriesWithoutReadingTheirManifests() = withRuntimeRoot { root ->
        val broken = writeInstalledFixture(root, "com.example.broken")
        Files.writeString(broken.resolve("plugin.json"), "invalid json")
        val incompatible = writeInstalledFixture(root, "com.example.incompatible")
        Files.writeString(
            incompatible.resolve("plugin.json"),
            Files.readString(incompatible.resolve("plugin.json")).replace("\"apiVersion\": 1", "\"apiVersion\": 2"),
        )
        val incomplete = Files.createDirectories(root.resolve("Plugins/com.example.incomplete/1.0.0/nested"))
        Files.writeString(incomplete.resolve("partial.txt"), "unfinished install")
        assertTrue(PlugManager.installedList.isEmpty())

        for (id in listOf("com.example.broken", "com.example.incompatible", "com.example.incomplete")) {
            assertTrue(PlugManager.uninstallPlugin(id, "1.0.0"))
            assertFalse(Files.exists(root.resolve("Plugins/$id/1.0.0")))
            assertTrue(Files.isDirectory(root.resolve("Plugins/$id")), "保留空的插件 ID 目录")
            assertFalse(PlugManager.uninstallPlugin(id, "1.0.0"))
        }
    }

    @Test
    fun rejectsUnsafeUninstallArgumentsWithoutDeletingFiles() = withRuntimeRoot { root ->
        val installed = writeInstalledFixture(root, "com.example.test")
        val sentinel = root.resolve("keep.txt")
        Files.writeString(sentinel, "keep me")
        val invalid = listOf(
            "", " ", ".", "..", "../com.example.test", "/absolute", "a/b", "a\\b", "C:\\Plugins",
            "C:relative", " leading", "trailing ", "line\nbreak", "nul\u0000byte", "del\u007f", "control\u0085",
        )

        for (value in invalid) {
            val idError = assertFailsWith<IllegalArgumentException> {
                PlugManager.uninstallPlugin(value, "1.0.0")
            }
            val versionError = assertFailsWith<IllegalArgumentException> {
                PlugManager.uninstallPlugin("com.example.test", value)
            }
            assertFalse(idError is PluginInstallException)
            assertFalse(versionError is PluginInstallException)
            assertEquals("keep me", Files.readString(sentinel))
            assertTrue(Files.isRegularFile(installed.resolve("plugin.json")))
        }
    }

    @Test
    fun refusesNonDirectoryUninstallPathsAtEveryInstallationLevel() {
        for (relative in listOf("Plugins", "Plugins/com.example.test", "Plugins/com.example.test/1.0.0")) {
            withRuntimeRoot { root ->
                val path = root.resolve(relative)
                Files.createDirectories(path.parent)
                Files.writeString(path, "keep me")

                assertFailsWith<IllegalStateException> {
                    PlugManager.uninstallPlugin("com.example.test", "1.0.0")
                }
                assertEquals("keep me", Files.readString(path))
            }
        }
    }

    @Test
    fun refusesLinkedAndDanglingUninstallPathsAtEveryInstallationLevel() {
        if (System.getProperty("os.name").startsWith("Windows")) return
        for (relative in listOf("Plugins", "Plugins/com.example.test", "Plugins/com.example.test/1.0.0")) {
            for (dangling in listOf(false, true)) {
                withRuntimeRoot { root ->
                    val outside = root.resolve("outside")
                    val sentinel = outside.resolve("keep.txt")
                    if (!dangling) {
                        Files.createDirectory(outside)
                        Files.writeString(sentinel, "keep me")
                    }
                    val path = root.resolve(relative)
                    Files.createDirectories(path.parent)
                    Files.createSymbolicLink(path, outside)

                    assertFailsWith<IllegalStateException> {
                        PlugManager.uninstallPlugin("com.example.test", "1.0.0")
                    }
                    assertTrue(Files.isSymbolicLink(path))
                    if (dangling) {
                        assertFalse(Files.exists(outside, NOFOLLOW_LINKS))
                    } else {
                        assertEquals("keep me", Files.readString(sentinel))
                    }
                }
            }
        }
    }

    @Test
    fun deletesInternalLinksWithoutFollowingThemOrDeletingExternalContents() = withRuntimeRoot { root ->
        if (System.getProperty("os.name").startsWith("Windows")) return@withRuntimeRoot
        val installed = writeInstalledFixture(root, "com.example.test")
        val outside = Files.createDirectory(root.resolve("outside"))
        val sentinel = outside.resolve("keep.txt")
        Files.writeString(sentinel, "keep me")
        Files.createSymbolicLink(installed.resolve("linked-directory"), outside)
        Files.createSymbolicLink(installed.resolve("linked-file"), sentinel)
        Files.createSymbolicLink(installed.resolve("dangling-link"), outside.resolve("missing"))

        assertTrue(PlugManager.uninstallPlugin("com.example.test", "1.0.0"))

        assertFalse(Files.exists(installed, NOFOLLOW_LINKS))
        assertEquals("keep me", Files.readString(sentinel))
        assertEquals(listOf("keep.txt"), Files.list(outside).use { it.map { path -> path.fileName.toString() }.toList() })
    }

    @Test
    fun listingAnEmptyRuntimeDoesNotCreateDirectories() = withRuntimeRoot { root ->
        assertTrue(PlugManager.installedList.isEmpty())
        assertFalse(Files.exists(root.resolve("Plugins")))
    }

    @Test
    fun listsAllInstalledVersionsInStableOrderWithoutDuplicates() = withRuntimeRoot { root ->
        val archives = listOf(
            createEva(root.resolve("z.eva"), id = "com.example.z"),
            createEva(root.resolve("a2.eva"), id = "com.example.a", version = "2.0.0"),
            createEva(root.resolve("a1.eva"), id = "com.example.a"),
        )
        archives.forEach { PlugManager.installPlugin(PluginSource.File(it.toString())) }
        PlugManager.installPlugin(PluginSource.File(archives.first().toString()))

        val installed = PlugManager.installedList
        assertEquals(
            listOf("com.example.a:1.0.0", "com.example.a:2.0.0", "com.example.z:1.0.0"),
            installed.map { "${it.manifest.id}:${it.manifest.version}" },
        )
        installed.forEach {
            assertEquals(root.resolve("Plugins/${it.manifest.id}/${it.manifest.version}").toString(), it.directory)
        }
    }

    @Test
    fun discoversExistingInstallationsAndReturnsAFreshSnapshot() = withRuntimeRoot { root ->
        // 模拟上次运行留下的目录，不调用 installPlugin，也不保留原 .eva 文件。
        val directory = writeInstalledFixture(root, "com.example.saved")
        val snapshot = PlugManager.installedList
        assertEquals("com.example.saved", snapshot.single().manifest.id)
        assertEquals(directory.toString(), snapshot.single().directory)

        directory.toFile().deleteRecursively()

        assertTrue(PlugManager.installedList.isEmpty())
        assertEquals(1, snapshot.size)
    }

    @Test
    fun skipsInvalidAndTemporaryInstallationsWithoutHidingValidOnes() = withRuntimeRoot { root ->
        writeInstalledFixture(root, "com.example.valid")
        writeInstalledFixture(root, ".eva-install-staging")
        val brokenJson = writeInstalledFixture(root, "com.example.broken")
        Files.writeString(brokenJson.resolve("plugin.json"), "invalid json")
        val missingJar = writeInstalledFixture(root, "com.example.missing")
        Files.delete(missingJar.resolve("prebuilt/jvm/plugin.jar"))
        val wrongId = writeInstalledFixture(root, "com.example.wrong")
        Files.writeString(
            wrongId.resolve("plugin.json"),
            Files.readString(wrongId.resolve("plugin.json")).replace("com.example.wrong", "com.example.other"),
        )
        val wrongVersion = writeInstalledFixture(root, "com.example.version")
        Files.move(wrongVersion, wrongVersion.resolveSibling("9.0.0"))
        val incompatible = writeInstalledFixture(root, "com.example.incompatible")
        Files.writeString(
            incompatible.resolve("plugin.json"),
            Files.readString(incompatible.resolve("plugin.json")).replace("\"apiVersion\": 1", "\"apiVersion\": 2"),
        )
        Files.createDirectories(root.resolve("Plugins/com.example.incomplete/1.0.0"))
        Files.writeString(root.resolve("Plugins/notes.txt"), "not an installation")

        assertEquals(listOf("com.example.valid"), PlugManager.installedList.map { it.manifest.id })
        assertTrue(Files.exists(brokenJson.resolve("plugin.json")), "查询不得删除损坏的安装")
    }

    @Test
    fun doesNotFollowLinkedInstallationDirectoriesOrManifests() = withRuntimeRoot { root ->
        // Windows 创建符号链接可能需要管理员权限；此测试用于 macOS/Linux。
        if (System.getProperty("os.name").startsWith("Windows")) return@withRuntimeRoot
        val outside = root.resolve("outside")
        val linkedId = writeInstalledFixture(outside, "com.example.linkedId")
        val linkedVersion = writeInstalledFixture(outside, "com.example.linkedVersion")
        val linkedManifest = writeInstalledFixture(outside, "com.example.linkedManifest")
        val plugins = Files.createDirectories(root.resolve("Plugins"))
        Files.createSymbolicLink(plugins.resolve("com.example.linkedId"), linkedId.parent)
        val versionParent = Files.createDirectories(plugins.resolve("com.example.linkedVersion"))
        Files.createSymbolicLink(versionParent.resolve("1.0.0"), linkedVersion)
        val manifestDirectory = writeInstalledFixture(root, "com.example.linkedManifest")
        Files.delete(manifestDirectory.resolve("plugin.json"))
        Files.createSymbolicLink(manifestDirectory.resolve("plugin.json"), linkedManifest.resolve("plugin.json"))

        assertTrue(PlugManager.installedList.isEmpty())
    }

    @Test
    fun reportsAnInvalidInstallationRootWithoutChangingIt() = withRuntimeRoot { root ->
        Files.writeString(root.resolve("Plugins"), "keep me")

        assertFailsWith<IllegalStateException> { PlugManager.installedList }
        assertEquals("keep me", Files.readString(root.resolve("Plugins")))
    }

    @Test
    fun installsAndThenIdempotentlyReusesAVerifiedPackage() = withRuntimeRoot { root ->
        val archive = createEva(root.resolve("echo.eva"))

        val first = PlugManager.installPlugin(PluginSource.File(archive.toString()))
        assertFalse(first.alreadyInstalled)
        assertTrue(first.sha256.matches(Regex("[0-9a-f]{64}")))
        assertEquals(root.resolve("Plugins/com.example.test/1.0.0").toString(), first.directory)
        assertTrue(Path.of(first.directory).resolve("plugin.json").toFile().isFile)

        val second = PlugManager.installPlugin(PluginSource.File(archive.toString()))
        assertTrue(second.alreadyInstalled)
        assertEquals(first.directory, second.directory)
    }

    @Test
    fun rejectsZipPathTraversalAndRollsBackStagingDirectory() = withRuntimeRoot { root ->
        val archive = createEva(
            root.resolve("malicious.eva"),
            extraEntries = mapOf("../outside.txt" to "do not write".encodeToByteArray()),
        )

        val error = assertFailsWith<PluginInstallException> {
            PlugManager.installPlugin(PluginSource.File(archive.toString()))
        }
        assertTrue(error.message.orEmpty().contains("越界路径"))
        assertFalse(Files.exists(root.resolve("outside.txt")))
        assertTrue(Files.exists(root.resolve("Plugins")))
        assertTrue(Files.list(root.resolve("Plugins")).use { stream -> stream.noneMatch { it.fileName.toString().startsWith(".eva-install-") } })
    }

    @Test
    fun rejectsUnsupportedManifestVersionBeforeInstalling() = withRuntimeRoot { root ->
        val archive = createEva(root.resolve("bad-manifest.eva"), formatVersion = 2)

        val error = assertFailsWith<PluginInstallException> {
            PlugManager.installPlugin(PluginSource.File(archive.toString()))
        }
        assertTrue(error.message.orEmpty().contains("formatVersion"))
        assertFalse(Files.exists(root.resolve("Plugins/com.example.test/1.0.0")))
        assertTrue(PlugManager.installedList.isEmpty())
    }

    @Test
    fun rejectsIncorrectExpectedSha256() = withRuntimeRoot { root ->
        val archive = createEva(root.resolve("wrong-sha.eva"))

        val error = assertFailsWith<PluginInstallException> {
            PlugManager.installPlugin(
                PluginSource.File(archive.toString(), sha256 = "0".repeat(64)),
            )
        }
        assertTrue(error.message.orEmpty().contains("sha256"))
        assertFalse(Files.exists(root.resolve("Plugins")))
    }

    @Test
    fun rejectsPackagesThatRequireANewerJavaRuntime() = withRuntimeRoot { root ->
        val archive = createEva(
            root.resolve("new-java.eva"),
            minJavaVersion = Runtime.version().feature() + 1,
        )

        val error = assertFailsWith<PluginInstallException> {
            PlugManager.installPlugin(PluginSource.File(archive.toString()))
        }
        assertTrue(error.message.orEmpty().contains("minJavaVersion") || error.message.orEmpty().contains("Java"))
        assertFalse(Files.exists(root.resolve("Plugins/com.example.test/1.0.0")))
    }

    @Test
    fun rejectsAnExistingVersionWithADifferentManifest() = withRuntimeRoot { root ->
        val first = createEva(root.resolve("first.eva"))
        PlugManager.installPlugin(PluginSource.File(first.toString()))

        val installedManifest = root.resolve("Plugins/com.example.test/1.0.0/plugin.json")
        val changed = Files.readString(installedManifest).replace("Test Plugin", "Changed Plugin")
        Files.writeString(installedManifest, changed)
        val error = assertFailsWith<PluginInstallException> {
            PlugManager.installPlugin(PluginSource.File(first.toString()))
        }
        assertTrue(error.message.orEmpty().contains("清单不匹配"))
    }

    private fun directoryContents(directory: Path): Map<String, List<Byte>?> = Files.walk(directory).use { paths ->
        paths.filter { it != directory }.toList().associate { path ->
            directory.relativize(path).toString() to
                if (Files.isDirectory(path)) null else Files.readAllBytes(path).toList()
        }
    }

    private fun rewriteSnapshot(
        root: Path,
        extraEntry: String? = null,
        transform: (String, ByteArray) -> ByteArray?,
    ) {
        val snapshot = root.resolve("Backup/plugins.zip")
        val replacement = root.resolve("replacement.zip")
        ZipFile(snapshot.toFile()).use { source ->
            ZipOutputStream(Files.newOutputStream(replacement)).use { output ->
                for (entry in source.entries()) {
                    val bytes = source.getInputStream(entry).use { it.readBytes() }
                    val changed = transform(entry.name, bytes) ?: continue
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(changed)
                    output.closeEntry()
                }
                if (extraEntry != null) {
                    output.putNextEntry(ZipEntry(extraEntry))
                    output.write("unexpected content".encodeToByteArray())
                    output.closeEntry()
                }
            }
        }
        Files.move(replacement, snapshot, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun createEva(
        path: Path,
        formatVersion: Int = 1,
        minJavaVersion: Int? = null,
        extraEntries: Map<String, ByteArray> = emptyMap(),
        id: String = "com.example.test",
        version: String = "1.0.0",
    ): Path {
        val artifact = jarBytes("com/example/test/TestPlugin.class")
        val minJavaField = minJavaVersion?.let { ",\n                  \"minJavaVersion\": $it" } ?: ""
        val manifest = """
            {
              "formatVersion": $formatVersion,
              "id": "$id",
              "name": "Test Plugin",
              "version": "$version",
              "apiVersion": 1,
              "platforms": {
                "jvm": {
                  "artifact": "prebuilt/jvm/plugin.jar",
                  "entryClass": "com.example.test.TestPlugin"$minJavaField
                }
              }
            }
        """.trimIndent().encodeToByteArray()
        Files.newOutputStream(path).use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("plugin.json"))
                zip.write(manifest)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("prebuilt/jvm/plugin.jar"))
                zip.write(artifact)
                zip.closeEntry()
                extraEntries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
        return path
    }

    private fun writeInstalledFixture(root: Path, id: String): Path {
        Files.createDirectories(root)
        val archive = createEva(root.resolve("fixture.eva"), id = id)
        val directory = root.resolve("Plugins/$id/1.0.0")
        Files.createDirectories(directory.resolve("prebuilt/jvm"))
        ZipFile(archive.toFile()).use { zip ->
            for (name in listOf("plugin.json", "prebuilt/jvm/plugin.jar")) {
                zip.getInputStream(zip.getEntry(name)).use { input ->
                    Files.copy(input, directory.resolve(name))
                }
            }
        }
        Files.delete(archive)
        return directory
    }

    private fun jarBytes(classEntry: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        JarOutputStream(bytes).use { jar ->
            jar.putNextEntry(JarEntry(classEntry))
            jar.write(byteArrayOf(0))
            jar.closeEntry()
        }
        bytes.toByteArray()
    }

    private fun withRuntimeRoot(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("eva-plugin-install")
        val previous = System.getProperty("user.dir")
        try {
            System.setProperty("user.dir", root.toString())
            block(root)
        } finally {
            if (previous == null) System.clearProperty("user.dir") else System.setProperty("user.dir", previous)
            root.toFile().deleteRecursively()
        }
    }
}
