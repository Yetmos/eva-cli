package com.eva_cli.examples.cmd

import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.event.Result
import com.eva_cli.core.widgets.event.TextEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CMDProviderTest {
    @Test
    fun recordsDoneOnlyAfterTheOutputSucceeds() = runBlocking {
        val provider = CMDProvider()
        val event = TextEvent("Echo: hello")
        val output = mutableListOf<String>()
        provider.bindOutput {
            assertTrue(event.records.isEmpty())
            output += it
        }
        provider.start()

        provider.receive(event)

        assertEquals(listOf("Echo: hello"), output)
        assertEquals(Action.Handle(Result.DONE), event.records.single().action)
        assertEquals(provider.id, event.records.single().actor.id)
        assertEquals(event.records, provider.records)
    }

    @Test
    fun doesNotRecordDoneWhenOutputFails() = runBlocking {
        val provider = CMDProvider()
        val event = TextEvent("Echo: hello")
        val failure = IllegalStateException("output failed")
        provider.bindOutput { throw failure }
        provider.start()

        assertSame(failure, assertFailsWith<IllegalStateException> { provider.receive(event) })

        assertTrue(event.records.isEmpty())
        assertTrue(provider.records.isEmpty())
    }

    @Test
    fun recordsUnsupportedPayloadsAsEnded() = runBlocking {
        val provider = CMDProvider()
        val event = object : Event {
            override val id = Uuid.random()
            override val metadata = Event.Metadata()
            override var records: List<EventRecord> = emptyList()
            override val payload = Event.Payload.Binary(byteArrayOf(1, 2, 3))
        }
        provider.start()

        provider.receive(event)

        assertEquals(Result.END, (event.records.single().action as Action.Handle).result)
        assertEquals(event.records, provider.records)
    }

    @Test
    fun skipsReceivingWhenStoppedOrDisabled() = runBlocking {
        val provider = CMDProvider()
        val event = TextEvent("Echo: hello")
        provider.receive(event)
        provider.start()
        provider.enable = false
        provider.receive(event)
        provider.enable = true
        provider.stop()
        provider.receive(event)

        assertTrue(event.records.isEmpty())
        assertTrue(provider.records.isEmpty())
    }

    @Test
    fun submitsInputAndWritesTextOutputThroughHostBindings() = runBlocking {
        val provider = CMDProvider()
        val inputs = mutableListOf<String>()
        val outputs = mutableListOf<String>()
        provider.bindInput { inputs += it }
        provider.bindOutput { outputs += it }
        provider.start()

        provider.submit("echo hello")
        provider.write("Echo: hello")

        assertEquals(listOf("echo hello"), inputs)
        assertEquals(listOf("Echo: hello"), outputs)
        provider.stop()
    }

    @Test
    fun ignoresInputAndOutputAfterStop() = runBlocking {
        val provider = CMDProvider()
        val outputs = mutableListOf<String>()
        provider.bindOutput { outputs += it }
        provider.start()
        provider.stop()

        provider.submit("echo hello")
        provider.write("Echo: hello")

        assertTrue(outputs.isEmpty())
    }
}
