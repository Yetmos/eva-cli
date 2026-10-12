package com.eva_cli.examples.cmd

import com.eva_cli.cmd.api.CommandLinePort
import com.eva_cli.core.widgets.event.Event

/** Internal bridge between CMD dispatcher and its provider. */
internal interface CommandLineEndpoint : CommandLinePort {
    fun bindInput(handler: (suspend (line: String) -> Unit)?)

    suspend fun write(text: String)

    suspend fun receive(event: Event)
}
