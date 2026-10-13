package com.eva_cli.core.widgets.event

import com.eva_cli.core.widgets.context.PluginDatabase
import com.eva_cli.core.widgets.context.PluginDatabaseFactory
import com.eva_cli.core.widgets.context.WidgetContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** 插件 records 的 Room 数据库句柄；数据库由宿主 Context 统一关闭。 */
internal class RecordsDatabaseHandle(
    val database: RecordsDatabase,
) : PluginDatabase {
    override fun close() = database.close()
}

internal object RecordsDatabaseFactory : PluginDatabaseFactory<RecordsDatabaseHandle> {
    override fun open(path: String): RecordsDatabaseHandle =
        RecordsDatabaseHandle(openRecordsDatabase(path))
}

/** 使用插件专属数据库保存组件 records，避免大列表 JSON 快照的全量读写。 */
internal object RecordsStore {
    private const val DATABASE_NAME = "records.db"
    private const val PAGE_SIZE = 512

    suspend fun write(
        context: WidgetContext,
        kind: ComponentRef.Kind,
        componentId: Uuid,
        records: List<EventRecord>,
    ) {
        val database = database(context)
        database.recordsDao().replace(
            componentId = componentId.toString(),
            componentKind = kind.name,
            records = records.map { it.toEntity(componentId, kind) },
        )
    }

    suspend fun read(
        context: WidgetContext,
        kind: ComponentRef.Kind,
        componentId: Uuid,
    ): List<EventRecord> {
        val database = database(context)
        val dao = database.recordsDao()
        val component = componentId.toString()
        val componentKind = kind.name
        val total = dao.count(component, componentKind)
        if (total == 0L) return emptyList()
        require(total <= Int.MAX_VALUE) { "Too many records for component $componentId: $total" }
        val result = ArrayList<EventRecord>(total.toInt())
        var offset = 0
        while (offset < total) {
            val page = dao.page(component, componentKind, PAGE_SIZE, offset)
            if (page.isEmpty()) break
            result += page.map(RecordEntity::toDomain)
            offset += page.size
        }
        return result
    }

    suspend fun count(
        context: WidgetContext,
        kind: ComponentRef.Kind,
        componentId: Uuid,
    ): Long = database(context).recordsDao().count(componentId.toString(), kind.name)

    suspend fun page(
        context: WidgetContext,
        kind: ComponentRef.Kind,
        componentId: Uuid,
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): List<EventRecord> {
        require(limit > 0) { "limit must be positive" }
        require(offset >= 0) { "offset must be non-negative" }
        return database(context).recordsDao()
            .page(componentId.toString(), kind.name, limit, offset)
            .map(RecordEntity::toDomain)
    }

    private fun database(context: WidgetContext): RecordsDatabase =
        context.databases.open(DATABASE_NAME, RecordsDatabaseFactory).database
}

private fun EventRecord.toEntity(componentId: Uuid, kind: ComponentRef.Kind): RecordEntity {
    val action = action.toEntity()
    return RecordEntity(
        componentId = componentId.toString(),
        componentKind = kind.name,
        recordId = id.toString(),
        eventId = eventId.toString(),
        occurredAt = occurredAt.toString(),
        actorId = actor.id.toString(),
        actorKind = actor.kind.name,
        actionType = action.type,
        dispatchType = action.dispatchType,
        targetId = action.targetId,
        targetKind = action.targetKind,
        reason = action.reason,
        result = action.result,
        detail = action.detail,
    )
}

private data class ActionColumns(
    val type: String,
    val dispatchType: String? = null,
    val targetId: String? = null,
    val targetKind: String? = null,
    val reason: String? = null,
    val result: String? = null,
    val detail: String? = null,
)

private fun Action.toEntity(): ActionColumns = when (this) {
    is Action.Dispatch -> ActionColumns(
        type = "dispatch",
        dispatchType = type.name,
        targetId = target.id.toString(),
        targetKind = target.kind.name,
        reason = reason,
    )
    is Action.Handle -> ActionColumns(type = "handle", result = result.name, detail = detail)
}

private fun RecordEntity.toDomain(): EventRecord = EventRecord(
    id = parseUuid(recordId, "record id"),
    eventId = parseUuid(eventId, "event id"),
    occurredAt = try {
        Instant.parse(occurredAt)
    } catch (error: IllegalArgumentException) {
        throw IllegalStateException("Invalid record occurredAt: $occurredAt", error)
    },
    actor = ComponentRef(parseUuid(actorId, "actor id"), parseEnum(actorKind, "actor kind")),
    action = when (actionType) {
        "dispatch" -> Action.Dispatch(
            parseRequiredEnum(dispatchType, "dispatch type"),
            ComponentRef(
                parseRequiredUuid(targetId, "dispatch target id"),
                parseRequiredEnum(targetKind, "dispatch target kind"),
            ),
            reason,
        )
        "handle" -> Action.Handle(parseRequiredEnum(result, "handle result"), detail)
        else -> error("Unknown event record action type: $actionType")
    },
)

private inline fun <reified T : Enum<T>> parseEnum(value: String, label: String): T = try {
    enumValueOf<T>(value)
} catch (error: IllegalArgumentException) {
    throw IllegalStateException("Invalid $label: $value", error)
}

private fun parseUuid(value: String, label: String): Uuid = try {
    Uuid.parse(value)
} catch (error: IllegalArgumentException) {
    throw IllegalStateException("Invalid $label: $value", error)
}

private inline fun <reified T : Enum<T>> parseRequiredEnum(value: String?, label: String): T =
    parseEnum(requireNotNull(value) { "Missing $label" }, label)

private fun parseRequiredUuid(value: String?, label: String): Uuid =
    parseUuid(requireNotNull(value) { "Missing $label" }, label)
