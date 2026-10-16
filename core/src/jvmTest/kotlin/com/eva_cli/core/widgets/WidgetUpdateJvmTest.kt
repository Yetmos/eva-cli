package com.eva_cli.core.widgets

import com.eva_cli.core.widgets.dispatcher.BaseDispatcher
import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.DispatchType
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.event.EventState
import com.eva_cli.core.widgets.event.EventStore
import com.eva_cli.core.widgets.event.Result
import com.eva_cli.core.widgets.event.StoredEvent
import com.eva_cli.core.widgets.provider.BaseProvider
import com.eva_cli.core.widgets.provider.Provider
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlin.uuid.Uuid

class WidgetUpdateJvmTest {
    @Test
    fun dispatchAndProviderUpdatesPersistOneCompleteEventSnapshot() = withRuntime { root ->
        val dispatcher = TestDispatcher(uuid(1))
        val provider = TestProvider(uuid(2))
        val event = TestEvent(
            id = uuid(3),
            metadata = Event.Metadata(provider.id, Instant.parse("2026-10-14T01:02:03.123456789Z")),
            payload = Event.Payload.Binary(byteArrayOf(0, 1, -1), "application/test"),
        )

        val dispatchRecord = dispatcher.decide(event, DispatchType.HANDLE, provider, "selected provider")
        assertSame(dispatchRecord, event.records.single())
        assertSame(dispatchRecord, dispatcher.records.single())
        assertEquals(
            Action.Dispatch(DispatchType.HANDLE, ComponentRef(provider.id, ComponentRef.Kind.PROVIDER), "selected provider"),
            dispatchRecord.action,
        )
        dispatcher.update(event)
        val processingStore = EventStore.open(root.toString())
        try {
            assertSnapshot(event, EventState.PROCESSING, assertNotNull(processingStore.find(event.id)))
        } finally {
            processingStore.close()
        }

        val handleRecord = provider.handle(event, Result.DONE, "handled")
        assertSame(handleRecord, event.records.last())
        assertSame(handleRecord, provider.records.single())
        assertEquals(Action.Handle(Result.DONE, "handled"), handleRecord.action)
        provider.update(event)
        // Provider 返回后，接收方 Dispatcher 会再次保存事件。
        dispatcher.update(event)
        provider.update(event)
        assertEquals(listOf(dispatchRecord, handleRecord), event.records)
        assertEquals(listOf(dispatchRecord), dispatcher.records)
        assertEquals(listOf(handleRecord), provider.records)

        EventStore.closeRuntime()
        val reopened = EventStore.open(root.toString())
        try {
            assertEquals(1L, reopened.count())
            assertEquals(0L, reopened.count(EventState.PROCESSING))
            assertSnapshot(event, EventState.COMPLETED, assertNotNull(reopened.find(event.id)))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun updateMergesOnlyOwnEventRecordsOnceAndPreservesComponentHistory() = withRuntime { root ->
        val componentId = uuid(10)
        val dispatcher = TestDispatcher(componentId)
        val provider = TestProvider(componentId)
        val event = TestEvent(uuid(11))
        val historical = record(uuid(12), uuid(101), ComponentRef(componentId, ComponentRef.Kind.PROVIDER))
        val own = record(event.id, uuid(102), ComponentRef(componentId, ComponentRef.Kind.PROVIDER))
        val dispatch = record(
            event.id,
            uuid(103),
            ComponentRef(componentId, ComponentRef.Kind.DISPATCHER),
            Action.Dispatch(DispatchType.HANDLE, own.actor),
        )
        val otherProvider = record(event.id, uuid(104), ComponentRef(uuid(20), ComponentRef.Kind.PROVIDER))
        val otherEvent = record(uuid(21), uuid(105), own.actor)
        provider.records = listOf(historical)
        event.records = listOf(dispatch, own, otherProvider, otherEvent)

        repeat(2) {
            provider.update(event)
            dispatcher.update(event)
        }

        assertEquals(listOf(historical, own), provider.records)
        assertEquals(listOf(dispatch), dispatcher.records)
        assertEquals(listOf(dispatch, own, otherProvider, otherEvent), event.records)
        val store = EventStore.open(root.toString())
        try {
            assertSnapshot(event, EventState.PROCESSING, assertNotNull(store.find(event.id)))
            assertEquals(1L, store.count())
        } finally {
            store.close()
        }
    }

    @Test
    fun providerResultsDetermineCompletionWhileReturningResultsRemainProcessing() = withRuntime { root ->
        val provider = TestProvider(uuid(30))
        val returnedIds = mutableListOf<Uuid>()
        provider.emit = { returned ->
            val store = EventStore.open(root.toString())
            try {
                assertSnapshot(returned, EventState.PROCESSING, assertNotNull(store.find(returned.id)))
                returnedIds += returned.id
            } finally {
                store.close()
            }
        }
        val states = mapOf(
            Result.DONE to EventState.COMPLETED,
            Result.END to EventState.COMPLETED,
            Result.BACK to EventState.PROCESSING,
            Result.DO_BACK to EventState.PROCESSING,
        )
        val events = states.keys.mapIndexed { index, result ->
            TestEvent(uuid(40 + index)).also { event ->
                provider.finish(event, result)
            }
        }
        assertEquals(listOf(uuid(42), uuid(43)), returnedIds)

        EventStore.closeRuntime()
        val reopened = EventStore.open(root.toString())
        try {
            events.forEach { event ->
                val result = (event.records.single().action as Action.Handle).result
                assertSnapshot(event, states.getValue(result), assertNotNull(reopened.find(event.id)))
            }
            assertEquals(4L, reopened.count())
            assertEquals(2L, reopened.count(EventState.COMPLETED))
            assertEquals(2L, reopened.count(EventState.PROCESSING))
        } finally {
            reopened.close()
        }
    }

    private fun assertSnapshot(expected: Event, state: EventState, actual: StoredEvent) {
        assertEquals(state, actual.state)
        assertEquals(expected.id, actual.event.id)
        assertEquals(expected.metadata, actual.event.metadata)
        assertEquals(expected.payload, actual.event.payload)
        assertEquals(expected.records, actual.event.records)
    }

    private fun record(
        eventId: Uuid,
        id: Uuid,
        actor: ComponentRef,
        action: Action = Action.Handle(Result.BACK),
    ) = EventRecord(
        id = id,
        eventId = eventId,
        occurredAt = Instant.parse("2026-10-14T01:02:03Z"),
        actor = actor,
        action = action,
    )

    private fun uuid(value: Int): Uuid =
        Uuid.parse("00000000-0000-0000-0000-${value.toString().padStart(12, '0')}")

    private fun withRuntime(block: suspend (Path) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("eva-widget-update-")
        val previousDirectory = System.getProperty("user.dir")
        EventStore.closeRuntime()
        System.setProperty("user.dir", root.toString())
        try {
            block(root)
        } finally {
            EventStore.closeRuntime()
            if (previousDirectory == null) {
                System.clearProperty("user.dir")
            } else {
                System.setProperty("user.dir", previousDirectory)
            }
            root.toFile().deleteRecursively()
        }
    }

    private class TestEvent(
        override val id: Uuid,
        override val metadata: Event.Metadata = Event.Metadata(),
        override val payload: Event.Payload = Event.Payload.Text("update test"),
        override var records: List<EventRecord> = emptyList(),
    ) : Event

    private class TestProvider(override val id: Uuid) : BaseProvider() {
        override val name = "update provider"
        override val desc = "update provider"
        override val topic = "/update"
        override var enable = true
        override var status = Provider.Status.STOP
        override var records: List<EventRecord> = emptyList()
        override var emit: (suspend (Event) -> Unit)? = null
        override suspend fun receive(event: Event) = Unit

        fun handle(event: Event, result: Result, detail: String? = null): EventRecord = record(event, result, detail)

        suspend fun finish(event: Event, result: Result) {
            handle(event, result)
            update(event)
            if (result == Result.BACK || result == Result.DO_BACK) {
                emit?.invoke(event)
            }
        }
    }

    private class TestDispatcher(override val id: Uuid) : BaseDispatcher() {
        override val name = "update dispatcher"
        override val desc = "update dispatcher"
        override val topic = "/update"
        override suspend fun receive(event: Event) = Unit
        override suspend fun dispatch(event: Event) = Unit
        override suspend fun scanProviders() = Unit
        override suspend fun scanDispatchers() = Unit

        fun decide(event: Event, type: DispatchType, target: Widget, reason: String? = null): EventRecord =
            record(event, type, target, reason)
    }
}
