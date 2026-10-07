package com.eva_cli.core.widgets.plug

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlugManagerJvmTest {
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
