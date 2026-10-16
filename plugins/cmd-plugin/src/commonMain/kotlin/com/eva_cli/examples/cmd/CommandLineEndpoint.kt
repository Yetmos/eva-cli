package com.eva_cli.examples.cmd

import com.eva_cli.cmd.api.CommandLinePort
import com.eva_cli.core.widgets.event.Event

/** CMD Dispatcher 与其 Provider 之间的内部桥接。 */
internal interface CommandLineEndpoint : CommandLinePort {
    fun bindInput(handler: (suspend (line: String) -> Unit)?)

    suspend fun write(text: String)

    suspend fun receive(event: Event)
}
