package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.event.Event
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
    fun emitsANewTextEventWithoutChangingTheInput() = runBlocking {
        val provider = EchoProvider()
        val input = TestEvent(Event.Payload.Text("hello"))
        val replies = mutableListOf<Event>()
        provider.emit = {
            assertEquals(Provider.Status.WORKING, provider.status)
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
        assertTrue(input.records.isEmpty())
        assertEquals(Provider.Status.IDLE, provider.status)
    }

    @Test
    fun ignoresBinaryEvents() = runBlocking {
        val provider = EchoProvider()
        val replies = mutableListOf<Event>()
        provider.emit = { replies += it }
        provider.start()

        provider.receive(TestEvent(Event.Payload.Binary(byteArrayOf(1, 2, 3))))

        assertTrue(replies.isEmpty())
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

        val replies = mutableListOf<Event>()
        provider.emit = { replies += it }
        provider.receive(input)
        assertEquals(Event.Payload.Text("Echo: hello"), replies.single().payload)
    }

    @Test
    fun propagatesCancellationAndResetsTheWorkingState() = runBlocking {
        val provider = EchoProvider()
        val cancellation = CancellationException("host stopped waiting")
        provider.emit = { throw cancellation }
        provider.start()

        assertSame(cancellation, assertFailsWith<CancellationException> {
            provider.receive(TestEvent(Event.Payload.Text("hello")))
        })
        assertEquals(Provider.Status.IDLE, provider.status)
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
