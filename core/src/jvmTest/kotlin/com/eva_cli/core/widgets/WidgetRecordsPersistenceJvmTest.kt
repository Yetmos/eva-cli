package com.eva_cli.core.widgets

import com.eva_cli.core.widgets.context.JvmWidgetContext
import com.eva_cli.core.widgets.context.UnavailablePluginPermissions
import com.eva_cli.core.widgets.dispatcher.BaseDispatcher
import com.eva_cli.core.widgets.event.Action
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
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

class WidgetRecordsPersistenceJvmTest {
    @Test
    fun providerAndDispatcherBackupsRoundTripAndUseSeparateFiles() = withRuntime { root, plugin ->
        val providerId = Uuid.random()
        val dispatcherId = Uuid.random()
        val provider = TestProvider(providerId)
        val dispatcher = TestDispatcher(dispatcherId)
        val providerRecord = EventRecord(
            eventId = Uuid.random(),
            occurredAt = Instant.parse("2026-01-02T03:04:05Z"),
            actor = ComponentRef(providerId, ComponentRef.Kind.PROVIDER),
            action = Action.Handle(Result.DONE, "provider"),
        )
        val dispatcherRecord = EventRecord(
            eventId = Uuid.random(),
            occurredAt = Instant.parse("2026-01-02T03:04:06Z"),
            actor = ComponentRef(dispatcherId, ComponentRef.Kind.DISPATCHER),
            action = Action.Dispatch(
                type = com.eva_cli.core.widgets.event.DispatchType.FEED_UP,
                target = ComponentRef(providerId, ComponentRef.Kind.PROVIDER),
                reason = "dispatcher",
            ),
        )
        val context = JvmWidgetContext("records-test", root, plugin, null, UnavailablePluginPermissions)
        try {
            provider.attach(context)
            dispatcher.attach(context)
            runBlocking {
                provider.records = listOf(providerRecord)
                dispatcher.records = listOf(dispatcherRecord)
                provider.backup()
                dispatcher.backup()
                provider.records = emptyList()
                dispatcher.records = emptyList()
                provider.restore()
                dispatcher.restore()
            }
            assertEquals(listOf(providerRecord), provider.records)
            assertEquals(listOf(dispatcherRecord), dispatcher.records)
            runBlocking {
                assertEquals(listOf("$providerId.json"), context.files.list("records/providers"))
                assertEquals(listOf("$dispatcherId.json"), context.files.list("records/dispatchers"))
            }
        } finally {
            context.close()
        }
    }

    @Test
    fun missingBackupRestoresEmptyAndMalformedBackupFailsClearly() = withRuntime { root, plugin ->
        val id = Uuid.random()
        val provider = TestProvider(id)
        val context = JvmWidgetContext("records-test", root, plugin, null, UnavailablePluginPermissions)
        try {
            provider.attach(context)
            provider.records = listOf(sampleRecord(id))
            runBlocking {
                provider.restore()
                assertTrue(provider.records.isEmpty())
                context.files.write("records/providers/$id.json", "{broken".encodeToByteArray())
                val failure = assertFailsWith<IllegalStateException> { provider.restore() }
                assertTrue(failure.message.orEmpty().contains("PROVIDER"))
                assertTrue(failure.message.orEmpty().contains(id.toString()))
            }
        } finally {
            context.close()
        }
    }

    @Test
    fun dispatcherLifecycleRestoresOnStartAndBacksUpOnStop() = withRuntime { root, plugin ->
        val id = Uuid.random()
        val first = TestDispatcher(id)
        val providerId = Uuid.random()
        val firstProvider = TestProvider(providerId)
        firstProvider.records = listOf(sampleRecord(providerId))
        first.providers[providerId] = firstProvider
        val context = JvmWidgetContext("records-test", root, plugin, null, UnavailablePluginPermissions)
        try {
            first.attach(context)
            firstProvider.attach(context)
            first.records = listOf(sampleRecord(id, ComponentRef.Kind.DISPATCHER))
            runBlocking { first.stop() }

            val second = TestDispatcher(id)
            val secondProvider = TestProvider(providerId)
            second.providers[providerId] = secondProvider
            second.attach(context)
            secondProvider.attach(context)
            runBlocking { second.start() }
            assertEquals(first.records, second.records)
            assertEquals(firstProvider.records, secondProvider.records)
        } finally {
            context.close()
        }
    }

    private fun sampleRecord(id: Uuid, kind: ComponentRef.Kind = ComponentRef.Kind.PROVIDER) = EventRecord(
        eventId = Uuid.random(),
        actor = ComponentRef(id, kind),
        action = Action.Handle(Result.END),
    )

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
