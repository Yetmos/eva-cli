package com.eva_cli.core.widgets.event

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

data class EventDatabaseSnapshot(
    val event: EventEntity,
    val records: List<EventRecordEntity>,
)

@Dao
interface EventsDao {
    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun event(id: String): EventEntity?

    @Query(
        "SELECT * FROM events WHERE (:state IS NULL OR state = :state) " +
            "ORDER BY created_at_seconds ASC, created_at_nanos ASC, id ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun events(state: String?, limit: Int, offset: Int): List<EventEntity>

    @Query("SELECT * FROM event_records WHERE event_id = :id ORDER BY position ASC")
    suspend fun records(id: String): List<EventRecordEntity>

    @Query("SELECT COUNT(*) FROM events WHERE (:state IS NULL OR state = :state)")
    suspend fun count(state: String?): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Insert
    suspend fun insertRecords(records: List<EventRecordEntity>)

    @Query("DELETE FROM event_records WHERE event_id = :id")
    suspend fun deleteRecords(id: String)

    /** 状态、payload 与记录一起提交，覆盖同 ID 事件时移除旧快照中的记录。 */
    @Transaction
    suspend fun replace(event: EventEntity, records: List<EventRecordEntity>) {
        deleteRecords(event.id)
        insertEvent(event)
        if (records.isNotEmpty()) insertRecords(records)
    }

    @Transaction
    suspend fun find(id: String): EventDatabaseSnapshot? {
        val event = event(id) ?: return null
        return EventDatabaseSnapshot(event, records(id))
    }

    @Transaction
    suspend fun page(state: String?, limit: Int, offset: Int): List<EventDatabaseSnapshot> =
        events(state, limit, offset).map { EventDatabaseSnapshot(it, records(it.id)) }
}
