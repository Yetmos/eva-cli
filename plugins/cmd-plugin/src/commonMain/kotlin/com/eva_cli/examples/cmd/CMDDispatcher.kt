package com.eva_cli.examples.cmd

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.TextEvent

/** Parses command lines and forwards command results to CMDProvider. */
class CMDDispatcher : PluginDispatcher() {
    override val name = "CMD Dispatcher"
    override val desc = "Parses command-line commands"
    override val topic = "/CMD"

    private val commandPort: CommandLineEndpoint
        get() = checkNotNull(providers.values.singleOrNull() as? CommandLineEndpoint) {
            "topic=$topic 必须且只能有一个 CommandLinePort Provider"
        }

    override suspend fun start() {
        super.start()
        val endpoint = commandPort
        endpoint.bindInput { line -> receive(TextEvent(line)) }
    }

    override suspend fun stop() {
        (providers.values.singleOrNull() as? CommandLineEndpoint)?.bindInput(null)
        super.stop()
    }

    /** RootDispatcher 将最终回复通过 /CMD Dispatcher 的 dispatch 传入输出端。 */
    override suspend fun dispatch(event: Event) {
        if (!enable) return
        commandPort.receive(event)
    }

    /** CMDProvider 的输入回调进入这里，再提交到宿主根 Dispatcher。 */
    override suspend fun receive(event: Event) {
        if (!enable) return
        val line = (event.payload as? Event.Payload.Text)?.value?.trim().orEmpty()
        if (line.isEmpty()) return

        val command = line.substringBefore(' ').lowercase()
        val argument = line.substringAfter(' ', missingDelimiterValue = "").trim()
        when (command) {
            "echo" -> {
                if (argument.isEmpty()) {
                    commandPort.write("Usage: echo <text>")
                } else {
                    checkNotNull(emit) { "Bind CMDDispatcher.emit before forwarding commands" }(
                        TextEvent(argument),
                    )
                }
            }
            else -> commandPort.write("Unknown command: $command")
        }
    }
}
