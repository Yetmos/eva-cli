package com.eva_cli.core.widgets

import com.eva_cli.core.widgets.context.JvmWidgetContext
import com.eva_cli.core.widgets.context.UnavailablePluginPermissions
import com.eva_cli.core.widgets.dispatcher.BaseDispatcher
import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.DispatchType
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.event.RecordsStore
import com.eva_cli.core.widgets.event.Result
import com.eva_cli.core.widgets.provider.BaseProvider
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RecordsStoreJvmTest {
    @Test
    fun writesAndReadsRoomRecordsWithProviderDispatcherIsolation() = withRuntime { root, plugin ->
        val componentId = uuid(1)
        val providerRecord = record(componentId, ComponentRef.Kind.PROVIDER, 1)
        val dispatcherRecord = EventRecord(
            id = uuid(102),
            eventId = uuid(202),
            occurredAt = Instant.parse("2026-01-02T03:02:00Z"),
            actor = ComponentRef(componentId, ComponentRef.Kind.DISPATCHER),
            action = Action.Dispatch(
                type = DispatchType.FEED_UP,
                target = ComponentRef(uuid(3), ComponentRef.Kind.PROVIDER),
                reason = "room-test",
            ),
        )
        val context = context(root, plugin)
        try {
            runBlocking {
                RecordsStore.write(context, ComponentRef.Kind.PROVIDER, componentId, listOf(providerRecord))
                RecordsStore.write(context, ComponentRef.Kind.DISPATCHER, componentId, listOf(dispatcherRecord))
                assertEquals(listOf(providerRecord), RecordsStore.read(context, ComponentRef.Kind.PROVIDER, componentId))
                assertEquals(listOf(dispatcherRecord), RecordsStore.read(context, ComponentRef.Kind.DISPATCHER, componentId))
                assertEquals(1L, RecordsStore.count(context, ComponentRef.Kind.PROVIDER, componentId))
                assertEquals(1L, RecordsStore.count(context, ComponentRef.Kind.DISPATCHER, componentId))
            }
            assertTrue(Files.isRegularFile(root.resolve("Plugins/records-test/Data/databases/records.db")))
        } finally {
            context.close()
        }
    }

    @Test
    fun pagesRecordsInOccurredAtOrderAndReportsCount() = withRuntime { root, plugin ->
        val componentId = uuid(10)
        val records = (0 until 519).map { index -> record(componentId, ComponentRef.Kind.PROVIDER, index) }
        val context = context(root, plugin)
        try {
            runBlocking {
                RecordsStore.write(context, ComponentRef.Kind.PROVIDER, componentId, records.reversed())
                assertEquals(records.size.toLong(), RecordsStore.count(context, ComponentRef.Kind.PROVIDER, componentId))
                assertEquals(records, RecordsStore.read(context, ComponentRef.Kind.PROVIDER, componentId))
                assertEquals(records.take(3), RecordsStore.page(context, ComponentRef.Kind.PROVIDER, componentId, limit = 3))
                assertEquals(records.drop(3).take(3), RecordsStore.page(context, ComponentRef.Kind.PROVIDER, componentId, limit = 3, offset = 3))
                assertEquals(records.drop(512).take(4), RecordsStore.page(context, ComponentRef.Kind.PROVIDER, componentId, limit = 4, offset = 512))
                assertEquals(records.drop(518), RecordsStore.page(context, ComponentRef.Kind.PROVIDER, componentId, limit = 3, offset = 518))
                assertTrue(RecordsStore.page(context, ComponentRef.Kind.PROVIDER, componentId, limit = 3, offset = records.size).isEmpty())
            }
        } finally {
            context.close()
        }
    }

    @Test
    fun recordsSurviveContextRecreationAndClosedContextReleasesDatabase() = withRuntime { root, plugin ->
        val componentId = uuid(20)
        val records = listOf(record(componentId, ComponentRef.Kind.DISPATCHER, 1))
        val first = context(root, plugin)
        runBlocking { RecordsStore.write(first, ComponentRef.Kind.DISPATCHER, componentId, records) }
        first.close()
        assertFailsWith<IllegalStateException> {
            runBlocking { RecordsStore.count(first, ComponentRef.Kind.DISPATCHER, componentId) }
        }
        val second = context(root, plugin)
        try {
            runBlocking {
                assertEquals(records, RecordsStore.read(second, ComponentRef.Kind.DISPATCHER, componentId))
                assertEquals(1L, RecordsStore.count(second, ComponentRef.Kind.DISPATCHER, componentId))
            }
        } finally {
            second.close()
        }
    }

    @Test
    fun dispatcherLifecycleRestoresRecordsFromRoomOnStartAndBacksUpOnStop() = withRuntime { root, plugin ->
        val dispatcherId = uuid(30)
        val providerId = uuid(31)
        val first = TestDispatcher(dispatcherId)
        val firstProvider = TestProvider(providerId)
        val context = context(root, plugin)
        try {
            first.attach(context)
            firstProvider.attach(context)
            first.providers[providerId] = firstProvider
            first.records = listOf(record(dispatcherId, ComponentRef.Kind.DISPATCHER, 1))
            firstProvider.records = listOf(record(providerId, ComponentRef.Kind.PROVIDER, 2))
            runBlocking { first.stop() }
            val second = TestDispatcher(dispatcherId)
            val secondProvider = TestProvider(providerId)
            second.attach(context)
            secondProvider.attach(context)
            second.providers[providerId] = secondProvider
            runBlocking { second.start() }
            assertEquals(first.records, second.records)
            assertEquals(firstProvider.records, secondProvider.records)
        } finally {
            context.close()
        }
    }

    private fun record(componentId: Uuid, kind: ComponentRef.Kind, index: Int): EventRecord = EventRecord(
        id = uuid(index + 100),
        eventId = uuid(index + 200),
        occurredAt = Instant.parse(
            "2026-01-02T03:${(index / 60).toString().padStart(2, '0')}:${(index % 60).toString().padStart(2, '0')}Z",
        ),
        actor = ComponentRef(componentId, kind),
        action = Action.Handle(Result.DONE, "record-$index"),
    )

    private fun uuid(value: Int): Uuid = Uuid.parse("00000000-0000-0000-0000-${value.toString().padStart(12, '0')}")

    private fun context(root: Path, plugin: Path) =
        JvmWidgetContext("records-test", root, plugin, null, UnavailablePluginPermissions)

    private fun withRuntime(block: (Path, Path) -> Unit) {
        val root = Files.createTempDirectory("eva-records-")
        try {
            block(root, Files.createDirectory(root.resolve("plugin")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private class TestProvider(override val id: Uuid) : BaseProvider() {
        override val name = "records provider"
        override val desc = "records provider"
        override val topic = "/records"
        override var enable = true
        override var status = com.eva_cli.core.widgets.provider.Provider.Status.STOP
        override var records: List<EventRecord> = emptyList()
        override var emit: (suspend (Event) -> Unit)? = null
        override suspend fun receive(event: Event) = Unit
    }

    private class TestDispatcher(override val id: Uuid) : BaseDispatcher() {
        override val name = "records dispatcher"
        override val desc = "records dispatcher"
        override val topic = "/records"
        override suspend fun receive(event: Event) = Unit
        override suspend fun dispatch(event: Event) = Unit
        override suspend fun scanProviders() = Unit
        override suspend fun scanDispatchers() = Unit
    }
}
