package com.eva_cli.examples.root

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking
import kotlin.uuid.Uuid

class RootDispatcherTest {
    @Test
    fun usesEchoAsTheDefaultDestination() = runBlocking {
        val root = RootDispatcher()
        val echo = RecordingDispatcher("/Echo")
        root.dispatchers[echo.id] = echo

        val event = TestEvent()
        root.dispatch(event)

        assertSame(event, echo.received)
    }

    @Test
    fun routesAnExplicitDestinationWithoutBroadcasting() = runBlocking {
        val root = RootDispatcher()
        val echo = RecordingDispatcher("/Echo")
        val command = RecordingDispatcher("/CMD")
        root.dispatchers[echo.id] = echo
        root.dispatchers[command.id] = command

        val event = TestEvent(Event.Metadata(targetTopic = "/CMD"))
        root.dispatch(event)

        assertSame(event, command.received)
        assertEquals(null, echo.received)
    }

    @Test
    fun rejectsAnUnknownDestination() = runBlocking {
        val root = RootDispatcher()
        val echo = RecordingDispatcher("/Echo")
        root.dispatchers[echo.id] = echo

        val error = assertFailsWith<IllegalStateException> {
            root.dispatch(TestEvent(Event.Metadata(targetTopic = "/Missing")))
        }

        assertEquals(true, error.message.orEmpty().contains("/Missing"))
    }

    @Test
    fun rejectsAnAmbiguousDestination() = runBlocking {
        val root = RootDispatcher()
        val first = RecordingDispatcher("/Echo")
        val second = RecordingDispatcher("/Echo")
        root.dispatchers[first.id] = first
        root.dispatchers[second.id] = second

        val error = assertFailsWith<IllegalStateException> {
            root.dispatch(TestEvent())
        }

        assertEquals(true, error.message.orEmpty().contains("found 2"))
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

    private class TestEvent(
        override val metadata: Event.Metadata = Event.Metadata(),
    ) : Event {
        override val id: Uuid = Uuid.random()
        override var records: List<EventRecord> = emptyList()
        override val payload: Event.Payload = Event.Payload.Text("test")
    }
}
