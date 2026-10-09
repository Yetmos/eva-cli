package com.eva_cli

import com.eva_cli.core.Eva
import com.eva_cli.core.widgets.context.CommandLinePort
import com.eva_cli.core.widgets.plug.PlugManager
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) = runBlocking {
    if (args.size > 1) {
        System.err.println("Expected a single command. Run eva --help for usage.")
        exitProcess(2)
    }

    when (val command = args.firstOrNull()) {
        null, "--help", "-h" -> println(
            """
            Usage: eva [cmd|--help]

            Eva CLI.

              cmd           Read commands from stdin and write responses to stdout
              --help, -h    Show this help
            """.trimIndent(),
        )
        "cmd" -> runCommandLine()
        else -> {
            System.err.println("Unknown command: $command. Run eva --help for usage.")
            exitProcess(2)
        }
    }
}

private suspend fun runCommandLine() {
    Eva.start()
    val ports = PlugManager.loadedList.flatMap { it.providers }.filterIsInstance<CommandLinePort>()
    check(ports.size == 1) {
        "已加载插件中必须且只能有一个 CommandLinePort，实际找到 ${ports.size} 个"
    }
    val port = ports.single()
    port.bindOutput { text -> println(text) }
    try {
        while (true) {
            val line = readlnOrNull() ?: break
            port.submit(line)
        }
    } finally {
        port.bindOutput(null)
        Eva.stop()
    }
}
