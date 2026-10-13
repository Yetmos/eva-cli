package com.eva_cli.core.widgets.event

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Room 中保存的单条 Provider/Dispatcher records。时间和 UUID 使用规范化字符串存储。 */
@Entity(
    tableName = "widget_records",
    primaryKeys = ["component_id", "component_kind", "record_id"],
    indices = [
        Index(value = ["component_id", "component_kind", "occurred_at", "record_id"]),
        Index(value = ["event_id"]),
    ],
)
data class RecordEntity(
    @ColumnInfo(name = "component_id")
    val componentId: String,
    @ColumnInfo(name = "component_kind")
    val componentKind: String,
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "event_id")
    val eventId: String,
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
