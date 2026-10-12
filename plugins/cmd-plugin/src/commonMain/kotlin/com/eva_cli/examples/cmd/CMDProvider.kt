package com.eva_cli.examples.cmd

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider
import kotlin.uuid.Uuid

/** Command-line input/output endpoint mounted at /CMD. */
class CMDProvider : PluginProvider(), CommandLineEndpoint {
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

    override fun bindInput(handler: (suspend (line: String) -> Unit)?) {
        inputHandler = handler
    }

    override fun bindOutput(handler: (suspend (text: String) -> Unit)?) {
        outputHandler = handler
    }

    override suspend fun start() {
        status = Provider.Status.IDLE
    }

    override suspend fun stop() {
        inputHandler = null
        outputHandler = null
        status = Provider.Status.STOP
    }

    override suspend fun submit(line: String) {
        if (!enable || status == Provider.Status.STOP) return
        checkNotNull(inputHandler) { "Bind CMDProvider input before submitting commands" }(line)
    }

    override suspend fun write(text: String) {
        if (!enable || status == Provider.Status.STOP) return
        checkNotNull(outputHandler) { "Bind CMDProvider output before writing responses" }(text)
    }

    /** Dispatcher output uses the same endpoint contract as the host. */
    override suspend fun receive(event: Event) {
        val text = (event.payload as? Event.Payload.Text)?.value ?: return
        write(text)
    }
}
