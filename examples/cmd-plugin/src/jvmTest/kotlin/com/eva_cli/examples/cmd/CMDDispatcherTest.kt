package com.eva_cli.examples.cmd

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.TextEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CMDDispatcherTest {
    @Test
    fun parsesEchoAndSubmitsOnlyTheArgument() = runBlocking {
        val dispatcher = CMDDispatcher()
        val provider = CMDProvider()
        dispatcher.providers[provider.id] = provider
        provider.start()
        val submitted = mutableListOf<Event>()
        provider.bindEventSubmit { submitted += it }

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
