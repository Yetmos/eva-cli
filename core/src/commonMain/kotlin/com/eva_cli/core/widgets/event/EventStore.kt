package com.eva_cli.core.widgets.event

import com.eva_cli.core.utils.file.FileUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class EventState {
    PROCESSING,
    COMPLETED,
}

data class StoredEvent(val event: Event, val state: EventState)

/**
 * 宿主事件库，保存 Event 的标准字段快照；恢复时不依赖创建该事件的插件类。
 * 所有存取和关闭操作串行执行，page 按创建时间与事件 ID 排序。
 */
class EventStore private constructor(private val database: EventsDatabase) {
    private val mutex = Mutex()
    private var closed = false

    companion object {
        private val runtimeMutex = Mutex()
        private var runtimeStore: EventStore? = null

        fun open(runtimeRoot: String): EventStore {
            require(runtimeRoot.isNotBlank()) { "runtimeRoot must not be blank" }
            val directory = "${runtimeRoot.trimEnd('/', '\\')}/Events"
            FileUtils.createDirectories(directory)
            return EventStore(openEventsDatabase("$directory/events.db"))
        }

        /** 所有组件共享宿主事件库；运行目录在此句柄的生命周期内保持不变。 */
        suspend fun update(event: Event) = runtimeMutex.withLock {
            val store = runtimeStore ?: open(FileUtils.rootPath()).also { runtimeStore = it }
            store.update(event)
        }

        /** 宿主停止所有组件后关闭；下次 update 会重新打开。 */
        suspend fun closeRuntime() = runtimeMutex.withLock {
            val store = runtimeStore ?: return@withLock
            try {
                store.close()
            } finally {
                runtimeStore = null
            }
        }
    }

    /** 仅 DONE/END 终结事件；分发及 BACK/DO_BACK 表示仍需继续处理。 */
    suspend fun update(event: Event) {
        val action = event.records.lastOrNull { it.eventId == event.id }?.action
        val state = if (action is Action.Handle && (action.result == Result.DONE || action.result == Result.END)) {
            EventState.COMPLETED
        } else {
            EventState.PROCESSING
        }
        save(event, state)
    }

    suspend fun save(event: Event, state: EventState) = mutex.withLock {
        checkOpen()
        database.eventsDao().replace(
            event.toEntity(state),
            event.records.toList().mapIndexed { position, record -> record.toEntity(event.id, position) },
        )
    }

    suspend fun find(id: Uuid): StoredEvent? = mutex.withLock {
        checkOpen()
        database.eventsDao().find(id.toString())?.toDomain()
    }

    suspend fun page(
        state: EventState? = null,
        limit: Int = 512,
        offset: Int = 0,
    ): List<StoredEvent> = mutex.withLock {
        checkOpen()
        require(limit > 0) { "limit must be positive" }
        require(offset >= 0) { "offset must be non-negative" }
        database.eventsDao().page(state?.name, limit, offset).map(EventDatabaseSnapshot::toDomain)
    }

    suspend fun count(state: EventState? = null): Long = mutex.withLock {
        checkOpen()
        database.eventsDao().count(state?.name)
    }

    suspend fun close() = mutex.withLock {
        if (!closed) {
            closed = true
            database.close()
        }
    }

    private fun checkOpen() {
        check(!closed) { "EventStore is closed" }
    }
}

private fun Event.toEntity(state: EventState): EventEntity {
    val payload = payload
    return EventEntity(
        id = id.toString(),
        state = state.name,
        providerId = metadata.providerId?.toString(),
        createdAtSeconds = metadata.createdAt.epochSeconds,
        createdAtNanos = metadata.createdAt.nanosecondsOfSecond,
        payloadType = when (payload) {
            is Event.Payload.Text -> "text"
            is Event.Payload.Binary -> "binary"
        },
        payloadText = (payload as? Event.Payload.Text)?.value,
        payloadBinary = (payload as? Event.Payload.Binary)?.toByteArray(),
        mimeType = (payload as? Event.Payload.Binary)?.mimeType,
    )
}

private fun EventRecord.toEntity(eventId: Uuid, position: Int): EventRecordEntity {
    val dispatch = action as? Action.Dispatch
    val handle = action as? Action.Handle
    return EventRecordEntity(
        eventId = eventId.toString(),
        position = position,
        recordId = id.toString(),
        recordEventId = this.eventId.toString(),
        occurredAt = occurredAt.toString(),
        actorId = actor.id.toString(),
        actorKind = actor.kind.name,
        actionType = when (action) {
            is Action.Dispatch -> "dispatch"
            is Action.Handle -> "handle"
        },
        dispatchType = dispatch?.type?.name,
        targetId = dispatch?.target?.id?.toString(),
        targetKind = dispatch?.target?.kind?.name,
        reason = dispatch?.reason,
        result = handle?.result?.name,
        detail = handle?.detail,
    )
}

private class SnapshotEvent(
    override val id: Uuid,
    override val metadata: Event.Metadata,
    override val payload: Event.Payload,
    override var records: List<EventRecord>,
) : Event

private fun EventDatabaseSnapshot.toDomain(): StoredEvent = StoredEvent(
    event = SnapshotEvent(
        id = Uuid.parse(event.id),
        metadata = Event.Metadata(
            providerId = event.providerId?.let(Uuid::parse),
            createdAt = Instant.fromEpochSeconds(event.createdAtSeconds, event.createdAtNanos),
        ),
        payload = when (event.payloadType) {
            "text" -> Event.Payload.Text(requireNotNull(event.payloadText) { "Missing event text payload" })
            "binary" -> Event.Payload.Binary(
                requireNotNull(event.payloadBinary) { "Missing event binary payload" },
                event.mimeType,
            )
            else -> error("Unknown event payload type: ${event.payloadType}")
        },
        records = records.map(EventRecordEntity::toDomain),
    ),
    state = enumValueOf(event.state),
)

private fun EventRecordEntity.toDomain(): EventRecord = EventRecord(
    id = Uuid.parse(recordId),
    eventId = Uuid.parse(recordEventId),
    occurredAt = Instant.parse(occurredAt),
    actor = ComponentRef(Uuid.parse(actorId), enumValueOf(actorKind)),
    action = when (actionType) {
        "dispatch" -> Action.Dispatch(
            type = enumValueOf(requireNotNull(dispatchType) { "Missing dispatch type" }),
            target = ComponentRef(
                Uuid.parse(requireNotNull(targetId) { "Missing dispatch target ID" }),
                enumValueOf(requireNotNull(targetKind) { "Missing dispatch target kind" }),
            ),
            reason = reason,
        )
        "handle" -> Action.Handle(
            result = enumValueOf(requireNotNull(result) { "Missing handle result" }),
            detail = detail,
        )
        else -> error("Unknown event record action type: $actionType")
    },
)
