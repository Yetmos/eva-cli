package com.eva_cli.examples.cmd

import com.eva_cli.cmd.api.CommandLinePort
import com.eva_cli.core.Eva
import com.eva_cli.core.widgets.event.EventState
import com.eva_cli.core.widgets.event.EventStore
import com.eva_cli.core.widgets.plug.PluginSource
import com.eva_cli.core.widgets.plug.PlugManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CMDRuntimeLoadingTest {
    @Test
    fun installedCmdAndEchoPluginsExchangeACommandThroughTheRuntime() = runBlocking {
        EventStore.closeRuntime()
        val root = Files.createTempDirectory("eva-cmd-runtime")
        val previousUserDir = System.getProperty("user.dir")
        val echoArchive = zipDirectory(
            Path.of(checkNotNull(System.getProperty("eva.echoPlugin.stagingDirectory"))),
            root.resolve("echo.eva"),
        )
        val rootArchive = zipDirectory(
            Path.of(checkNotNull(System.getProperty("eva.rootPlugin.stagingDirectory"))),
            root.resolve("root.eva"),
        )
        val cmdArchive = zipDirectory(
            Path.of(checkNotNull(System.getProperty("eva.cmdPlugin.stagingDirectory"))),
            root.resolve("cmd.eva"),
        )
        val loadedIds = mutableListOf<String>()
        try {
            System.setProperty("user.dir", root.toString())
            val echo = PlugManager.installPlugin(PluginSource.File(echoArchive.toString()))
            val rootPlugin = PlugManager.installPlugin(PluginSource.File(rootArchive.toString()))
            val cmd = PlugManager.installPlugin(PluginSource.File(cmdArchive.toString()))
            loadedIds += echo.manifest.id
            loadedIds += rootPlugin.manifest.id
            loadedIds += cmd.manifest.id
            PlugManager.loadPlugin(echo.manifest.id, echo.manifest.version)
            PlugManager.loadPlugin(rootPlugin.manifest.id, rootPlugin.manifest.version)
            val loadedCmd = PlugManager.loadPlugin(cmd.manifest.id, cmd.manifest.version)
            val port = loadedCmd.providers.filterIsInstance<CommandLinePort>().single()
            val output = mutableListOf<String>()
            port.bindOutput { output += it }

            Eva.start()
            port.submit("echo hello")

            assertEquals(listOf("Echo: hello"), output)
            val events = EventStore.open(root.toString())
            try {
                assertEquals(2L, events.count())
                assertEquals(2L, events.count(EventState.COMPLETED))
            } finally {
                events.close()
            }
        } finally {
            Eva.stop()
            loadedIds.asReversed().forEach { id ->
                if (PlugManager.loadedList.any { it.manifest.id == id }) PlugManager.unloadPlugin(id)
            }
            if (previousUserDir == null) System.clearProperty("user.dir")
            else System.setProperty("user.dir", previousUserDir)
            root.toFile().deleteRecursively()
        }
    }

    private fun zipDirectory(source: Path, target: Path): Path {
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            Files.walk(source).use { paths ->
                paths.filter { Files.isRegularFile(it) }.forEach { path ->
                    zip.putNextEntry(ZipEntry(source.relativize(path).toString().replace('\\', '/')))
                    Files.copy(path, zip)
                    zip.closeEntry()
                }
            }
        }
        return target
    }
}
