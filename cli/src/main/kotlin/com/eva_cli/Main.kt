package com.eva_cli

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size > 1) {
        System.err.println("Expected a single command. Run eva --help for usage.")
        exitProcess(2)
    }

    when (val command = args.firstOrNull()) {
        null, "--help", "-h" -> println(
            """
            Usage: eva [--help]

            Eva CLI project scaffold.
            Agent orchestration, script execution, and sandboxing are not implemented yet.

              --help, -h   Show this help
            """.trimIndent(),
        )
        else -> {
            System.err.println("Unknown command: $command. Run eva --help for usage.")
            exitProcess(2)
        }
    }
}
