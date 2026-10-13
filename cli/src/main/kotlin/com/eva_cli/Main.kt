package com.eva_cli

import com.eva_cli.cmd.api.CommandLinePort
import com.eva_cli.core.Eva
import com.eva_cli.core.widgets.plug.PlugManager
import com.eva_cli.core.widgets.plug.PluginSource
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) = runBlocking {
    val exitCode = when (args.firstOrNull()) {
        null, "--help", "-h" -> {
            if (args.size > 1) {
                printError("Unexpected arguments. Run eva --help for usage.")
                2
            } else {
                printHelp()
                0
            }
        }
        "cmd" -> if (args.size == 1) {
            runCommandLine()
            0
        } else {
            printError("The cmd command does not accept arguments. Run eva --help for usage.")
            2
        }
        "plugin" -> runPluginCommand(args.drop(1))
        else -> {
            printError("Unknown command: ${args.first()}. Run eva --help for usage.")
            2
        }
    }
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}

private fun printHelp() {
    println(
        """
        Usage: eva [cmd|plugin|--help]

        Eva CLI.

          cmd                         Read commands from stdin and write responses to stdout
          plugin <command>             Manage installed plugins (run "eva plugin --help")
          --help, -h                   Show this help
        """.trimIndent(),
    )
}

private fun printPluginHelp() {
    println(
        """
        Usage: eva plugin <command>

        Commands:
          install <path> [sha256]  Install a local .eva package
          list                     List installed plugins
          remove <id> <version>    Remove an installed plugin version
          load <id> <version>      Load an installed plugin
          unload <id>              Unload a loaded plugin
          --help, -h               Show this help
        """.trimIndent(),
    )
}

private fun runPluginCommand(arguments: List<String>): Int {
    val command = arguments.firstOrNull()
    if (command == null || command == "--help" || command == "-h" || command == "help") {
        if (arguments.size > 1) {
            printError("Unexpected arguments. Run eva plugin --help for usage.")
            return 2
        }
        printPluginHelp()
        return 0
    }

    fun requireArguments(count: Int, usage: String): Boolean {
        if (arguments.size == count) return true
        printError("Usage: eva plugin $usage")
        return false
    }

    return try {
        when (command) {
            "install" -> {
                if (arguments.size !in 2..3) {
                    printError("Usage: eva plugin install <path> [sha256]")
                    return 2
                }
                val installed = PlugManager.installPlugin(
                    PluginSource.File(
                        uri = arguments[1],
                        sha256 = arguments.getOrNull(2),
                    ),
                )
                val state = if (installed.alreadyInstalled) "Already installed" else "Installed"
                println("$state ${installed.manifest.id}:${installed.manifest.version}")
                0
            }
            "list" -> {
                if (!requireArguments(1, "list")) return 2
                val installed = PlugManager.installedList
                if (installed.isEmpty()) {
                    println("No plugins installed.")
                } else {
                    installed.forEach { plugin ->
                        println("${plugin.manifest.id}:${plugin.manifest.version}\t${plugin.manifest.name}")
                    }
                }
                0
            }
            "remove" -> {
                if (!requireArguments(3, "remove <id> <version>")) return 2
                val id = arguments[1]
                check(PlugManager.loadedList.none { it.manifest.id == id }) {
                    "Plugin $id is loaded; unload it before removing a version"
                }
                check(PlugManager.uninstallPlugin(id, arguments[2])) {
                    "Plugin $id:${arguments[2]} is not installed"
                }
                println("Removed $id:${arguments[2]}")
                0
            }
            "load" -> {
                if (!requireArguments(3, "load <id> <version>")) return 2
                val loaded = PlugManager.loadPlugin(arguments[1], arguments[2])
                println("Loaded ${loaded.manifest.id}:${loaded.manifest.version}")
                0
            }
            "unload" -> {
                if (!requireArguments(2, "unload <id>")) return 2
                check(PlugManager.unloadPlugin(arguments[1])) {
                    "Plugin ${arguments[1]} is not loaded"
                }
                println("Unloaded ${arguments[1]}")
                0
            }
            else -> {
                printError("Unknown plugin command: $command. Run eva plugin --help for usage.")
                2
            }
        }
    } catch (error: Exception) {
        printError(error.message ?: error::class.simpleName ?: "Plugin command failed")
        1
    }
}

private fun printError(message: String) {
    System.err.println("Error: $message")
}

private suspend fun runCommandLine() {
    try {
        Eva.start()
        val ports = PlugManager.loadedList.flatMap { it.providers }.filterIsInstance<CommandLinePort>()
        check(ports.size == 1) {
            "已加载插件中必须且只能有一个 CommandLinePort，实际找到 ${ports.size} 个"
        }
        val port = ports.single()
        try {
            port.bindOutput { text -> println(text) }
            while (true) {
                val line = readlnOrNull() ?: break
                port.submit(line)
            }
        } finally {
            port.bindOutput(null)
        }
    } finally {
        Eva.stop()
    }
}
