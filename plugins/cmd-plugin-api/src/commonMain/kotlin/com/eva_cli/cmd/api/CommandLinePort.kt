package com.eva_cli.cmd.api

/** Host bridge exposed by the command-line plugin. */
interface CommandLinePort {
    fun bindOutput(handler: (suspend (text: String) -> Unit)?)

    suspend fun submit(line: String)
}
