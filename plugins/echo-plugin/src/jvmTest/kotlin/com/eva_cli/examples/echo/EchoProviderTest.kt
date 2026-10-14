package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.Result
import com.eva_cli.core.widgets.provider.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EchoProviderTest {
    @Test
    fun emitsANewTextEventAndRecordsSuccessfulInputHandling() = runBlocking {
        val provider = EchoProvider()
        val input = TestEvent(Event.Payload.Text("hello"))
        val replies = mutableListOf<Event>()
        provider.emit = {
            assertEquals(Provider.Status.WORKING, provider.status)
            assertTrue(input.records.isEmpty())
            replies += it
        }
        provider.start()

        provider.receive(input)

        val reply = replies.single()
        assertEquals(Event.Payload.Text("Echo: hello"), reply.payload)
        assertEquals(provider.id, reply.metadata.providerId)
        assertNotEquals(input.id, reply.id)
        assertEquals(Event.Payload.Text("hello"), input.payload)
        assertNull(input.metadata.providerId)
        assertEquals(Action.Handle(Result.DONE), input.records.single().action)
        assertEquals(provider.id, input.records.single().actor.id)
        assertEquals(input.records, provider.records)
        assertTrue(reply.records.isEmpty())
        assertEquals(Provider.Status.IDLE, provider.status)
    }

    @Test
    fun recordsUnsupportedEventsAsEnded() = runBlocking {
        val provider = EchoProvider()
        val replies = mutableListOf<Event>()
        provider.emit = { replies += it }
        provider.start()

        val input = TestEvent(Event.Payload.Binary(byteArrayOf(1, 2, 3)))
        provider.receive(input)

        assertTrue(replies.isEmpty())
        assertEquals(Result.END, (input.records.single().action as Action.Handle).result)
        assertEquals(input.records, provider.records)
        assertEquals(Provider.Status.IDLE, provider.status)
    }

    @Test
    fun onlyProcessesEventsWhileStartedAndEnabled() = runBlocking {
        val provider = EchoProvider()
        val input = TestEvent(Event.Payload.Text("hello"))
        val replies = mutableListOf<Event>()
        provider.emit = { replies += it }

        provider.receive(input)
        provider.start()
        provider.enable = false
        provider.receive(input)
        provider.enable = true
        provider.stop()
        provider.receive(input)

        assertTrue(replies.isEmpty())
        assertTrue(input.records.isEmpty())
        assertTrue(provider.records.isEmpty())
        assertEquals(Provider.Status.STOP, provider.status)

        provider.start()
        provider.receive(input)
        assertEquals(Event.Payload.Text("Echo: hello"), replies.single().payload)
    }

    @Test
    fun propagatesOutputFailuresAndCanHandleTheNextEvent() = runBlocking {
        val provider = EchoProvider()
        val input = TestEvent(Event.Payload.Text("hello"))
        val failure = IllegalStateException("output failed")
        provider.emit = { throw failure }
        provider.start()

        assertSame(failure, assertFailsWith<IllegalStateException> { provider.receive(input) })
        assertEquals(Provider.Status.IDLE, provider.status)
        assertTrue(input.records.isEmpty())
        assertTrue(provider.records.isEmpty())

        val replies = mutableListOf<Event>()
        provider.emit = { replies += it }
        provider.receive(input)
        assertEquals(Event.Payload.Text("Echo: hello"), replies.single().payload)
    }

    @Test
    fun propagatesCancellationAndResetsTheWorkingState() = runBlocking {
        val provider = EchoProvider()
        val cancellation = CancellationException("host stopped waiting")
        val input = TestEvent(Event.Payload.Text("hello"))
        provider.emit = { throw cancellation }
        provider.start()

        assertSame(cancellation, assertFailsWith<CancellationException> {
            provider.receive(input)
        })
        assertEquals(Provider.Status.IDLE, provider.status)
        assertTrue(input.records.isEmpty())
        assertTrue(provider.records.isEmpty())
    }

    @Test
    fun preservesStopRequestedByTheOutputCallback() = runBlocking {
        val provider = EchoProvider()
        provider.emit = { provider.stop() }
        provider.start()

        provider.receive(TestEvent(Event.Payload.Text("hello")))

        assertEquals(Provider.Status.STOP, provider.status)
    }

    @Test
    fun reportsAnUnboundOutputCallback() = runBlocking {
        val provider = EchoProvider()
        provider.start()

        val failure = assertFailsWith<IllegalStateException> {
            provider.receive(TestEvent(Event.Payload.Text("hello")))
        }

        assertTrue(failure.message.orEmpty().contains("emit"))
        assertEquals(Provider.Status.IDLE, provider.status)
    }
}
