package com.eva_cli.examples.root

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking
import kotlin.uuid.Uuid

class RootDispatcherTest {
    @Test
    fun broadcastsInboundEventsToEveryChildExceptTheReportingDispatcher() = runBlocking {
        val root = RootDispatcher()
        val echo = RecordingDispatcher("/Echo")
        val other = RecordingDispatcher("/Other")
        val command = RecordingDispatcher("/CMD")
        root.dispatchers[echo.id] = echo
        root.dispatchers[other.id] = other
        root.dispatchers[command.id] = command

        val event = TestEvent()
        root.dispatch(event)

        assertSame(event, echo.received)
        assertSame(event, other.received)
        assertEquals(null, command.received)
    }

    @Test
    fun excludesTheChildThatOwnsTheReportingProvider() = runBlocking {
        val root = RootDispatcher()
        val reporter = RecordingDispatcher("/Reporter")
        val peer = RecordingDispatcher("/Peer")
        val provider = RecordingProvider()
        reporter.providers[provider.id] = provider
        root.dispatchers[reporter.id] = reporter
        root.dispatchers[peer.id] = peer

        val event = TestEvent(Event.Metadata(providerId = provider.id))
        root.dispatch(event)

        assertEquals(null, reporter.received)
        assertSame(event, peer.received)
    }

    @Test
    fun forwardsReportedEventsToTheHostOutput() = runBlocking {
        val root = RootDispatcher()
        val output = mutableListOf<Event>()
        root.emit = { output += it }

        val event = TestEvent()
        root.receive(event)

        assertEquals(1, output.size)
        assertSame(event, output.single())
    }

    @Test
    fun broadcastsReportedEventsToSiblingDispatchers() = runBlocking {
        val root = RootDispatcher()
        val reporter = RecordingDispatcher("/Reporter")
        val peer = RecordingDispatcher("/Peer")
        val provider = RecordingProvider()
        reporter.providers[provider.id] = provider
        root.dispatchers[reporter.id] = reporter
        root.dispatchers[peer.id] = peer

        val event = TestEvent(Event.Metadata(providerId = provider.id))
        root.receive(event)

        assertEquals(null, reporter.received)
        assertSame(event, peer.received)
    }

    private class RecordingDispatcher(override val topic: String) : PluginDispatcher() {
        override val name = "Recording Dispatcher"
        override val desc = "Test dispatcher"
        var received: Event? = null

        override suspend fun dispatch(event: Event) {
            received = event
        }

        override suspend fun receive(event: Event) {
            received = event
        }
    }

    private class RecordingProvider : PluginProvider() {
        override val id: Uuid = Uuid.random()
        override val name = "Recording Provider"
        override val desc = "Test provider"
        override val topic = "/Reporter"
        override var enable = true
        override var status = Provider.Status.IDLE
        override var records: List<EventRecord> = emptyList()
        override var emit: (suspend (event: Event) -> Unit)? = null

        override suspend fun receive(event: Event) = Unit
    }

    private class TestEvent(
        override val metadata: Event.Metadata = Event.Metadata(),
    ) : Event {
        override val id: Uuid = Uuid.random()
        override var records: List<EventRecord> = emptyList()
        override val payload: Event.Payload = Event.Payload.Text("test")
    }
}
