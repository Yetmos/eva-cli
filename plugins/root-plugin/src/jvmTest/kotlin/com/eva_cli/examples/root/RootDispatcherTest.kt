package com.eva_cli.examples.root

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.DispatchType
import com.eva_cli.core.widgets.event.Result
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
        assertEquals(
            listOf(
                Action.Dispatch(DispatchType.DISPATCH, ComponentRef(echo.id, ComponentRef.Kind.DISPATCHER)),
                Action.Dispatch(DispatchType.DISPATCH, ComponentRef(other.id, ComponentRef.Kind.DISPATCHER)),
            ),
            event.records.map { it.action },
        )
        assertEquals(event.records, root.records)
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

    @Test
    fun identifiesTheReturningProviderFromItsLatestHandlingRecord() = runBlocking {
        for (result in listOf(Result.BACK, Result.DO_BACK)) {
            val root = RootDispatcher()
            val reporter = RecordingDispatcher("/Reporter")
            val peer = RecordingDispatcher("/Peer")
            val provider = RecordingProvider()
            reporter.providers[provider.id] = provider
            root.dispatchers[reporter.id] = reporter
            root.dispatchers[peer.id] = peer
            val event = TestEvent()
            event.records = listOf(
                EventRecord(
                    eventId = event.id,
                    actor = ComponentRef(provider.id, ComponentRef.Kind.PROVIDER),
                    action = Action.Handle(result),
                ),
                EventRecord(
                    eventId = event.id,
                    actor = ComponentRef(reporter.id, ComponentRef.Kind.DISPATCHER),
                    action = Action.Dispatch(
                        DispatchType.FEED_UP,
                        ComponentRef(root.id, ComponentRef.Kind.DISPATCHER),
                    ),
                ),
            )

            root.receive(event)

            assertEquals(null, reporter.received)
            assertSame(event, peer.received)
            assertEquals(
                Action.Dispatch(DispatchType.DISPATCH, ComponentRef(peer.id, ComponentRef.Kind.DISPATCHER)),
                event.records.last().action,
            )
        }
    }

    @Test
    fun handlesASiblingOnlyOnceWhenTheOriginalEventReturnsDuringDispatch() = runBlocking {
        val root = RootDispatcher()
        val returningProvider = RecordingProvider(Result.DO_BACK)
        val completingProvider = RecordingProvider(Result.DONE)
        val reporter = RecordingDispatcher("/Reporter", returningProvider::receive)
        val peer = RecordingDispatcher("/Peer", completingProvider::receive)
        reporter.providers[returningProvider.id] = returningProvider
        peer.providers[completingProvider.id] = completingProvider
        root.dispatchers[reporter.id] = reporter
        root.dispatchers[peer.id] = peer
        returningProvider.emit = root::receive
        val output = mutableListOf<Event>()
        root.emit = { output += it }
        val event = TestEvent()

        root.dispatch(event)

        assertEquals(1, returningProvider.receivedCount)
        assertEquals(1, completingProvider.receivedCount)
        assertEquals(2, event.records.count { it.action is Action.Dispatch })
        assertEquals(Action.Handle(Result.DONE), event.records.last().action)
        assertEquals(emptyList(), output)

        root.dispatch(event)

        assertEquals(2, returningProvider.receivedCount)
        assertEquals(2, completingProvider.receivedCount)
    }

    @Test
    fun finishesTheActiveChainWhenBothProvidersReturnTheOriginalEvent() = runBlocking {
        val root = RootDispatcher()
        val firstProvider = RecordingProvider(Result.DO_BACK)
        val secondProvider = RecordingProvider(Result.BACK)
        val first = RecordingDispatcher("/First", firstProvider::receive)
        val second = RecordingDispatcher("/Second", secondProvider::receive)
        first.providers[firstProvider.id] = firstProvider
        second.providers[secondProvider.id] = secondProvider
        root.dispatchers[first.id] = first
        root.dispatchers[second.id] = second
        firstProvider.emit = root::receive
        secondProvider.emit = root::receive
        val output = mutableListOf<Event>()
        root.emit = { output += it }
        val event = TestEvent()

        root.dispatch(event)

        assertEquals(1, firstProvider.receivedCount)
        assertEquals(1, secondProvider.receivedCount)
        assertEquals(2, event.records.count { it.action is Action.Dispatch })
        assertEquals(1, output.size)
        assertSame(event, output.single())
    }

    private class RecordingDispatcher(
        override val topic: String,
        private val handler: (suspend (Event) -> Unit)? = null,
    ) : PluginDispatcher() {
        override val name = "Recording Dispatcher"
        override val desc = "Test dispatcher"
        var received: Event? = null

        override suspend fun dispatch(event: Event) {
            received = event
            handler?.invoke(event)
        }

        override suspend fun receive(event: Event) {
            received = event
        }
    }

    private class RecordingProvider(private val result: Result? = null) : PluginProvider() {
        override val id: Uuid = Uuid.random()
        override val name = "Recording Provider"
        override val desc = "Test provider"
        override val topic = "/Reporter"
        override var enable = true
        override var status = Provider.Status.IDLE
        override var records: List<EventRecord> = emptyList()
        override var emit: (suspend (event: Event) -> Unit)? = null
        var receivedCount = 0

        override suspend fun receive(event: Event) {
            receivedCount++
            val handlingResult = result ?: return
            record(event, handlingResult)
            update(event)
            if (handlingResult == Result.BACK || handlingResult == Result.DO_BACK) {
                checkNotNull(emit)(event)
            }
        }
    }

    private class TestEvent(
        override val metadata: Event.Metadata = Event.Metadata(),
    ) : Event {
        override val id: Uuid = Uuid.random()
        override var records: List<EventRecord> = emptyList()
        override val payload: Event.Payload = Event.Payload.Text("test")
    }
}
