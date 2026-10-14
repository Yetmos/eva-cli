package com.eva_cli.examples.cmd

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.DispatchType
import com.eva_cli.core.widgets.event.Result
import com.eva_cli.core.widgets.event.TextEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CMDDispatcherTest {
    @Test
    fun recordsDispatchAndSuccessfulOutputHandlingInOrder() = runBlocking {
        val dispatcher = CMDDispatcher()
        val provider = CMDProvider()
        val event = TextEvent("Echo: hello")
        dispatcher.providers[provider.id] = provider
        provider.bindOutput {
            assertEquals(
                Action.Dispatch(DispatchType.HANDLE, ComponentRef(provider.id, ComponentRef.Kind.PROVIDER)),
                event.records.single().action,
            )
        }
        provider.start()

        dispatcher.dispatch(event)

        assertEquals(2, event.records.size)
        assertEquals(dispatcher.id, event.records[0].actor.id)
        assertEquals(Action.Handle(Result.DONE), event.records[1].action)
        assertEquals(listOf(event.records[0]), dispatcher.records)
        assertEquals(listOf(event.records[1]), provider.records)
    }

    @Test
    fun parsesEchoAndSubmitsOnlyTheArgument() = runBlocking {
        val dispatcher = CMDDispatcher()
        val provider = CMDProvider()
        dispatcher.providers[provider.id] = provider
        provider.start()
        val submitted = mutableListOf<Event>()
        dispatcher.emit = { submitted += it }

        dispatcher.receive(TextEvent("echo hello world"))

        assertEquals(listOf(Event.Payload.Text("hello world")), submitted.map { it.payload })
        provider.stop()
    }

    @Test
    fun reportsUnknownCommandsToTheOutputPort() = runBlocking {
        val dispatcher = CMDDispatcher()
        val provider = CMDProvider()
        dispatcher.providers[provider.id] = provider
        provider.start()
        val output = mutableListOf<String>()
        provider.bindOutput { output += it }

        dispatcher.receive(TextEvent("whoami"))

        assertEquals(listOf("Unknown command: whoami"), output)
        provider.stop()
    }
}
