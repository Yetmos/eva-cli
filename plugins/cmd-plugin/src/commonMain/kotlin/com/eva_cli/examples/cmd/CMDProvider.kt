package com.eva_cli.examples.cmd

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.event.Result
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider
import kotlin.uuid.Uuid

/** 挂载在 /CMD 的命令行输入输出端点。 */
class CMDProvider : PluginProvider(), CommandLineEndpoint {
    override val id: Uuid = Uuid.parse("55555555-5555-4555-8555-555555555555")
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

    /** Dispatcher 输出使用与宿主相同的端点契约。 */
    override suspend fun receive(event: Event) {
        if (!enable || status == Provider.Status.STOP) return
        val text = (event.payload as? Event.Payload.Text)?.value
        if (text == null) {
            record(event, Result.END, "CMD only supports text events")
            update(event)
            return
        }
        write(text)
        record(event, Result.DONE)
        update(event)
    }
}
