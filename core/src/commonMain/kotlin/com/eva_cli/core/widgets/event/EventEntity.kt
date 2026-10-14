package com.eva_cli.core.widgets.event

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 宿主保存的完整事件快照；payload 类型独立于内容，空文本和空二进制不会混淆。 */
@Entity(
    tableName = "events",
    indices = [Index(value = ["state", "created_at_seconds", "created_at_nanos", "id"])],
)
data class EventEntity(
    @PrimaryKey
    val id: String,
    val state: String,
    @ColumnInfo(name = "provider_id")
    val providerId: String?,
    @ColumnInfo(name = "created_at_seconds")
    val createdAtSeconds: Long,
    @ColumnInfo(name = "created_at_nanos")
    val createdAtNanos: Int,
    @ColumnInfo(name = "payload_type")
    val payloadType: String,
    @ColumnInfo(name = "payload_text")
    val payloadText: String?,
    @ColumnInfo(name = "payload_binary", typeAffinity = ColumnInfo.BLOB)
    val payloadBinary: ByteArray?,
    @ColumnInfo(name = "mime_type")
    val mimeType: String?,
)

/** position 保存 Event.records 的追加顺序，recordEventId 保存记录自身的 eventId。 */
@Entity(
    tableName = "event_records",
    primaryKeys = ["event_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EventRecordEntity(
    @ColumnInfo(name = "event_id")
    val eventId: String,
    val position: Int,
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "record_event_id")
    val recordEventId: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: String,
    @ColumnInfo(name = "actor_id")
    val actorId: String,
    @ColumnInfo(name = "actor_kind")
    val actorKind: String,
    @ColumnInfo(name = "action_type")
    val actionType: String,
    @ColumnInfo(name = "dispatch_type")
    val dispatchType: String?,
    @ColumnInfo(name = "target_id")
    val targetId: String?,
    @ColumnInfo(name = "target_kind")
    val targetKind: String?,
    val reason: String?,
    val result: String?,
    val detail: String?,
)
