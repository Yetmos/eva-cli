package com.eva_cli.examples.cmd

import com.eva_cli.core.widgets.context.CommandLinePort
import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.TextEvent

/** Parses command lines and forwards command results to CMDProvider. */
class CMDDispatcher : PluginDispatcher() {
    override val name = "CMD Dispatcher"
    override val desc = "Parses command-line commands"
    override val topic = "/CMD"

    private val commandPort: CommandLinePort
        get() = checkNotNull(providers.values.singleOrNull() as? CommandLinePort) {
            "topic=$topic 必须且只能有一个 CommandLinePort Provider"
        }

    override suspend fun start() {
        super.start()
        commandPort.bindInput { line -> receive(TextEvent(line)) }
    }

    override suspend fun stop() {
        if (providers.size == 1) (providers.values.singleOrNull() as? CommandLinePort)?.bindInput(null)
        super.stop()
    }

    /** RootDispatcher 将最终回复通过 /CMD Dispatcher 的 dispatch 传入输出端。 */
    override suspend fun dispatch(event: Event) {
        if (!enable) return
        providers.values.single().receive(event)
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
                    commandPort.write(TextEvent("Usage: echo <text>"))
                } else {
                    commandPort.submitEvent(TextEvent(argument))
                }
            }
            else -> commandPort.write(TextEvent("Unknown command: $command"))
        }
    }
}
