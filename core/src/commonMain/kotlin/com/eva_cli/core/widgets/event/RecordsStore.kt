package com.eva_cli.core.widgets.event

import com.eva_cli.core.widgets.context.PluginFileArea
import com.eva_cli.core.widgets.context.WidgetContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Stable JSON persistence for records owned by one provider or dispatcher. */
internal object RecordsStore {
    private const val FORMAT_VERSION = 1
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    suspend fun write(context: WidgetContext, kind: ComponentRef.Kind, componentId: Uuid, records: List<EventRecord>) {
        val snapshot = RecordsSnapshot(FORMAT_VERSION, records.map(EventRecordDto::fromDomain))
        context.files.write(path(kind, componentId), json.encodeToString(snapshot).encodeToByteArray(), PluginFileArea.DATA)
    }

    suspend fun read(context: WidgetContext, kind: ComponentRef.Kind, componentId: Uuid): List<EventRecord> {
        val directory = directory(kind)
        val file = fileName(componentId)
        if (file !in context.files.list(directory, PluginFileArea.DATA)) return emptyList()
        val snapshot = try {
            json.decodeFromString<RecordsSnapshot>(context.files.read(path(kind, componentId), PluginFileArea.DATA).decodeToString())
        } catch (error: SerializationException) {
            throw IllegalStateException("Invalid $kind records backup for component $componentId", error)
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("Invalid $kind records backup for component $componentId", error)
        }
        check(snapshot.formatVersion == FORMAT_VERSION) { "Unsupported records backup version ${snapshot.formatVersion} for component $componentId" }
        return snapshot.records.map { it.toDomain() }
    }

    private fun path(kind: ComponentRef.Kind, id: Uuid) = "${directory(kind)}/${fileName(id)}"
    private fun directory(kind: ComponentRef.Kind) = when (kind) {
        ComponentRef.Kind.PROVIDER -> "records/providers"
        ComponentRef.Kind.DISPATCHER -> "records/dispatchers"
    }
    private fun fileName(id: Uuid) = "$id.json"
}

@Serializable
private data class RecordsSnapshot(val formatVersion: Int, val records: List<EventRecordDto>)

@Serializable
private data class EventRecordDto(
    val id: String,
    val eventId: String,
    val occurredAt: String,
    val actorId: String,
    val actorKind: String,
    val action: ActionDto,
) {
    fun toDomain(): EventRecord = EventRecord(
        id = parseUuid(id, "record id"),
        eventId = parseUuid(eventId, "event id"),
        occurredAt = try { Instant.parse(occurredAt) } catch (error: IllegalArgumentException) {
            throw IllegalStateException("Invalid record occurredAt: $occurredAt", error)
        },
        actor = ComponentRef(parseUuid(actorId, "actor id"), parseEnum(actorKind, "actor kind")),
        action = action.toDomain(),
    )

    companion object {
        fun fromDomain(record: EventRecord) = EventRecordDto(
            record.id.toString(), record.eventId.toString(), record.occurredAt.toString(),
            record.actor.id.toString(), record.actor.kind.name, ActionDto.fromDomain(record.action),
        )
    }
}

@Serializable
private data class ActionDto(
    val type: String,
    val dispatchType: String? = null,
    val targetId: String? = null,
    val targetKind: String? = null,
    val reason: String? = null,
    val result: String? = null,
    val detail: String? = null,
) {
    fun toDomain(): Action = when (type) {
        "dispatch" -> Action.Dispatch(
            parseRequiredEnum(dispatchType, "dispatch type"),
            ComponentRef(parseRequiredUuid(targetId, "dispatch target id"), parseRequiredEnum(targetKind, "dispatch target kind")),
            reason,
        )
        "handle" -> Action.Handle(parseRequiredEnum(result, "handle result"), detail)
        else -> error("Unknown event record action type: $type")
    }

    companion object {
        fun fromDomain(action: Action) = when (action) {
            is Action.Dispatch -> ActionDto("dispatch", action.type.name, action.target.id.toString(), action.target.kind.name, action.reason)
            is Action.Handle -> ActionDto("handle", result = action.result.name, detail = action.detail)
        }
    }
}

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
