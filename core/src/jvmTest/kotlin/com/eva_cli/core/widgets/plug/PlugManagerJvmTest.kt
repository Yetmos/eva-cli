package com.eva_cli.core.widgets.plug

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlugManagerJvmTest {
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
    ): Path {
        val artifact = jarBytes("com/example/test/TestPlugin.class")
        val minJavaField = minJavaVersion?.let { ",\n                  \"minJavaVersion\": $it" } ?: ""
        val manifest = """
            {
              "formatVersion": $formatVersion,
              "id": "com.example.test",
              "name": "Test Plugin",
              "version": "1.0.0",
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
