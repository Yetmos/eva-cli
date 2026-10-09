package com.eva_cli.core.widgets.context

import com.eva_cli.core.widgets.event.Event

/**
 * Host bridge exposed by a command-line Provider.
 * The host owns the actual stdin/stdout streams; the plugin owns command
 * submission and response conversion.
 */
interface CommandLinePort {
    fun bindInput(handler: (suspend (line: String) -> Unit)?)

    fun bindOutput(handler: (suspend (text: String) -> Unit)?)

    fun bindEventSubmit(handler: (suspend (event: Event) -> Unit)?)

    suspend fun submit(line: String)

    suspend fun submitEvent(event: Event)

    suspend fun write(event: Event)
}
