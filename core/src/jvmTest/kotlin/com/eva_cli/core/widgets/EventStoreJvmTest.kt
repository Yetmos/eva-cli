package com.eva_cli.core.widgets

import com.eva_cli.core.widgets.context.JvmWidgetContext
import com.eva_cli.core.widgets.context.UnavailablePluginPermissions
import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.DispatchType
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.event.EventState
import com.eva_cli.core.widgets.event.EventStore
import com.eva_cli.core.widgets.event.RecordsStore
import com.eva_cli.core.widgets.event.Result
import com.eva_cli.core.widgets.event.StoredEvent
import com.eva_cli.core.widgets.event.openEventsDatabase
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class EventStoreJvmTest {
    @Test
    fun processingAndCompletedSnapshotsSurviveDatabaseReopening() = withRuntime { root ->
        val processing = TestEvent(
            id = uuid(1),
            metadata = Event.Metadata(uuid(101), Instant.parse("2026-10-14T01:02:03.123456789Z")),
            payload = Event.Payload.Text("你好，Eva 🌍\n第二行"),
            records = listOf(
                EventRecord(
                    id = uuid(11),
                    eventId = uuid(1),
                    occurredAt = Instant.parse("2026-10-14T01:02:05Z"),
                    actor = ComponentRef(uuid(201), ComponentRef.Kind.DISPATCHER),
                    action = Action.Dispatch(
                        DispatchType.FEED_UP,
                        ComponentRef(uuid(202), ComponentRef.Kind.PROVIDER),
                        "分发原因",
                    ),
                ),
                EventRecord(
                    id = uuid(12),
                    eventId = uuid(1),
                    occurredAt = Instant.parse("2026-10-14T01:02:04Z"),
                    actor = ComponentRef(uuid(202), ComponentRef.Kind.PROVIDER),
                    action = Action.Handle(Result.DO_BACK, "处理细节"),
                ),
            ),
        )
        val completed = TestEvent(
            id = uuid(2),
            metadata = Event.Metadata(createdAt = Instant.parse("2026-10-14T01:02:03Z")),
            payload = Event.Payload.Binary(byteArrayOf(0, 1, -1, -128, 127), "application/test"),
        )
        val first = EventStore.open(root.toString())
        try {
            assertNull(first.find(processing.id))
            first.save(processing, EventState.PROCESSING)
            first.save(completed, EventState.COMPLETED)
        } finally {
            first.close()
        }
        assertTrue(Files.isRegularFile(root.resolve("Events/events.db")))
        val reopened = EventStore.open(root.toString())
        try {
            assertSnapshot(processing, EventState.PROCESSING, assertNotNull(reopened.find(processing.id)))
            assertSnapshot(completed, EventState.COMPLETED, assertNotNull(reopened.find(completed.id)))
            assertEquals(2L, reopened.count())
            assertEquals(1L, reopened.count(EventState.PROCESSING))
            assertEquals(1L, reopened.count(EventState.COMPLETED))
            assertEquals(listOf(completed.id, processing.id), reopened.page().map { it.event.id })
            assertEquals(listOf(processing.id), reopened.page(EventState.PROCESSING).map { it.event.id })
            assertEquals(listOf(completed.id), reopened.page(EventState.COMPLETED).map { it.event.id })
            assertEquals(listOf(processing.id), reopened.page(limit = 1, offset = 1).map { it.event.id })
            assertTrue(reopened.page(offset = 2).isEmpty())
        } finally {
            reopened.close()
        }
    }

    @Test
    fun emptyTextAndBinaryRetainTheirPayloadTypesAndMimeTypes() = withRuntime { root ->
        val events = listOf(
            TestEvent(uuid(1), payload = Event.Payload.Text("")),
            TestEvent(uuid(2), payload = Event.Payload.Binary(byteArrayOf())),
            TestEvent(uuid(3), payload = Event.Payload.Binary(byteArrayOf(), "application/octet-stream")),
        )
        val first = EventStore.open(root.toString())
        try {
            events.forEach { first.save(it, EventState.PROCESSING) }
        } finally {
            first.close()
        }
        val reopened = EventStore.open(root.toString())
        try {
            events.forEach { event ->
                assertSnapshot(event, EventState.PROCESSING, assertNotNull(reopened.find(event.id)))
            }
        } finally {
            reopened.close()
        }
    }

    @Test
    fun updatingSameEventAtomicallyReplacesStatePayloadAndRecords() = withRuntime { root ->
        val original = TestEvent(uuid(1), payload = Event.Payload.Text("处理中"))
        original.records = listOf(record(original.id, 1), record(original.id, 2))
        val completed = TestEvent(
            id = original.id,
            metadata = Event.Metadata(uuid(7), Instant.parse("2026-10-14T01:02:03Z")),
            payload = Event.Payload.Binary(byteArrayOf(8, 9), "application/done"),
            records = listOf(record(original.id, 3)),
        )
        val first = EventStore.open(root.toString())
        try {
            first.save(original, EventState.PROCESSING)
            first.save(completed, EventState.COMPLETED)
        } finally {
            first.close()
        }
        val reopened = EventStore.open(root.toString())
        try {
            assertEquals(1L, reopened.count())
            assertEquals(0L, reopened.count(EventState.PROCESSING))
            assertEquals(1L, reopened.count(EventState.COMPLETED))
            assertSnapshot(completed, EventState.COMPLETED, assertNotNull(reopened.find(original.id)))
            completed.records = emptyList()
            reopened.save(completed, EventState.COMPLETED)
            assertTrue(assertNotNull(reopened.find(original.id)).event.records.isEmpty())
        } finally {
            reopened.close()
        }
    }

    @Test
    fun failedRecordReplacementRollsBackTheEventStateAndEntireRecordSnapshot() = withRuntime { root ->
        val original = TestEvent(uuid(1), payload = Event.Payload.Text("处理中"))
        original.records = listOf(record(original.id, 1), record(original.id, 2))
        val store = EventStore.open(root.toString())
        try {
            store.save(original, EventState.PROCESSING)
        } finally {
            store.close()
        }
        val database = openEventsDatabase(root.resolve("Events/events.db").toString())
        try {
            val dao = database.eventsDao()
            val existing = assertNotNull(dao.event(original.id.toString()))
            val records = dao.records(original.id.toString())
            assertFailsWith<Exception> {
                dao.replace(
                    existing.copy(state = EventState.COMPLETED.name, payloadText = "处理完毕"),
                    listOf(records.first(), records.first()),
                )
            }
        } finally {
            database.close()
        }
        val reopened = EventStore.open(root.toString())
        try {
            assertSnapshot(original, EventState.PROCESSING, assertNotNull(reopened.find(original.id)))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun eventSnapshotsDoNotChangePluginComponentRecordDatabase() = withRuntime { root ->
        val plugin = Files.createDirectory(root.resolve("plugin"))
        val context = JvmWidgetContext("event-store-test", root, plugin, null, UnavailablePluginPermissions)
        val event = TestEvent(uuid(1), payload = Event.Payload.Text("独立数据库"))
        val component = uuid(501)
        val pluginRecord = record(event.id, 1)
        val store = EventStore.open(root.toString())
        try {
            RecordsStore.write(context, ComponentRef.Kind.PROVIDER, component, listOf(pluginRecord))
            store.save(event, EventState.PROCESSING)
            event.records = listOf(record(event.id, 2))
            store.save(event, EventState.COMPLETED)
            assertEquals(
                listOf(pluginRecord),
                RecordsStore.read(context, ComponentRef.Kind.PROVIDER, component),
            )
            assertTrue(Files.isRegularFile(root.resolve("Events/events.db")))
            assertTrue(Files.isRegularFile(root.resolve("Plugins/event-store-test/Data/databases/records.db")))
        } finally {
            store.close()
            context.close()
        }
        val reopenedContext = JvmWidgetContext(
            "event-store-test", root, plugin, null, UnavailablePluginPermissions,
        )
        try {
            assertEquals(
                listOf(pluginRecord),
                RecordsStore.read(reopenedContext, ComponentRef.Kind.PROVIDER, component),
            )
        } finally {
            reopenedContext.close()
        }
    }

    @Test
    fun closedStoreRejectsEveryOperationAndCloseIsIdempotent() = withRuntime { root ->
        val store = EventStore.open(root.toString())
        val event = TestEvent(uuid(1), payload = Event.Payload.Text("关闭"))
        assertFailsWith<IllegalArgumentException> { store.page(limit = 0) }
        assertFailsWith<IllegalArgumentException> { store.page(offset = -1) }
        store.save(event, EventState.PROCESSING)
        store.close()
        store.close()
        assertFailsWith<IllegalStateException> { store.save(event, EventState.COMPLETED) }
        assertFailsWith<IllegalStateException> { store.find(event.id) }
        assertFailsWith<IllegalStateException> { store.page() }
        assertFailsWith<IllegalStateException> { store.count() }
    }

    private fun assertSnapshot(expected: Event, state: EventState, actual: StoredEvent) {
        assertEquals(state, actual.state)
        assertEquals(expected.id, actual.event.id)
        assertEquals(expected.metadata, actual.event.metadata)
        assertEquals(expected.payload, actual.event.payload)
        assertEquals(expected.records, actual.event.records)
        assertFalse(actual.event is TestEvent)
    }

    private fun record(eventId: Uuid, index: Int): EventRecord = EventRecord(
        id = uuid(index + 100),
        eventId = eventId,
        occurredAt = Instant.parse("2026-10-14T01:02:03Z"),
        actor = ComponentRef(uuid(501), ComponentRef.Kind.PROVIDER),
        action = Action.Handle(Result.DONE, "record-$index"),
    )

    private fun uuid(value: Int): Uuid = Uuid.parse("00000000-0000-0000-0000-${value.toString().padStart(12, '0')}")

    private fun withRuntime(block: suspend (Path) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("eva-events-")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private class TestEvent(
        override val id: Uuid,
        override val metadata: Event.Metadata = Event.Metadata(),
        override val payload: Event.Payload,
        override var records: List<EventRecord> = emptyList(),
    ) : Event
}
