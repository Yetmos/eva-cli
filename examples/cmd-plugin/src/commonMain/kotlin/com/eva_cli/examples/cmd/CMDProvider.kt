package com.eva_cli.examples.cmd

import com.eva_cli.core.widgets.context.CommandLinePort
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider
import kotlin.uuid.Uuid

/** Command-line input/output endpoint mounted at /CMD. */
class CMDProvider : PluginProvider(), CommandLinePort {
    override val id: Uuid = Uuid.random()
    override val name = "CMD Provider"
    override val desc = "Command-line input and output endpoint"
    override val topic = "/CMD"
    override var enable = true
    override var status = Provider.Status.STOP
    override var records: List<EventRecord> = emptyList()
    override var emit: (suspend (event: Event) -> Unit)? = null

    private var inputHandler: (suspend (line: String) -> Unit)? = null
    private var outputHandler: (suspend (text: String) -> Unit)? = null
    private var eventSubmitHandler: (suspend (event: Event) -> Unit)? = null

    override fun bindInput(handler: (suspend (line: String) -> Unit)?) {
        inputHandler = handler
    }

    override fun bindOutput(handler: (suspend (text: String) -> Unit)?) {
        outputHandler = handler
    }

    override fun bindEventSubmit(handler: (suspend (event: Event) -> Unit)?) {
        eventSubmitHandler = handler
    }

    override suspend fun start() {
        status = Provider.Status.IDLE
    }

    override suspend fun stop() {
        inputHandler = null
        outputHandler = null
        eventSubmitHandler = null
        status = Provider.Status.STOP
    }

    override suspend fun submit(line: String) {
        if (!enable || status == Provider.Status.STOP) return
        checkNotNull(inputHandler) { "Bind CMDProvider input before submitting commands" }(line)
    }

    override suspend fun submitEvent(event: Event) {
        if (!enable || status == Provider.Status.STOP) return
        checkNotNull(eventSubmitHandler) { "Bind CMDProvider event submission before routing commands" }(event)
    }

    override suspend fun write(event: Event) {
        if (!enable || status == Provider.Status.STOP) return
        val text = (event.payload as? Event.Payload.Text)?.value ?: return
        checkNotNull(outputHandler) { "Bind CMDProvider output before writing responses" }(text)
    }

    /** Dispatcher output uses the same endpoint contract as the host. */
    override suspend fun receive(event: Event) {
        write(event)
    }
}
