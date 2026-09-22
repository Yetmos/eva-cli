package com.eva.cli

import com.eva.runtime.LocalEvaRuntime
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size > 1) {
        System.err.println("Expected a single command. Run eva --help for usage.")
        exitProcess(2)
    }

    when (val command = args.firstOrNull()) {
        null, "--help", "-h" -> println(
            """
            Usage: eva [--help | --version | info]

            Eva CLI project scaffold.
            Agent orchestration, script execution, and sandboxing are not implemented yet.

              --help, -h   Show this help
              --version    Show the runtime version
              info         Show the runtime version and host platform
            """.trimIndent(),
        )
        "--version" -> println("eva-cli ${LocalEvaRuntime().info().version}")
        "info" -> {
            val info = LocalEvaRuntime().info()
            println("Eva CLI project scaffold")
            println("Runtime version: ${info.version}")
            println("Host platform: ${info.host}")
            println("Agent orchestration, script execution, and sandboxing are not implemented yet.")
        }
        else -> {
            System.err.println("Unknown command: $command. Run eva --help for usage.")
            exitProcess(2)
        }
    }
}
