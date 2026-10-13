package com.eva_cli.core.widgets.event

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface RecordsDao {
    @Query(
        "SELECT * FROM widget_records " +
            "WHERE component_id = :componentId AND component_kind = :componentKind " +
            "ORDER BY occurred_at ASC, record_id ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun page(
        componentId: String,
        componentKind: String,
        limit: Int,
        offset: Int,
    ): List<RecordEntity>

    @Query(
        "SELECT COUNT(*) FROM widget_records " +
            "WHERE component_id = :componentId AND component_kind = :componentKind",
    )
    suspend fun count(componentId: String, componentKind: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(records: List<RecordEntity>)

    @Query(
        "DELETE FROM widget_records " +
            "WHERE component_id = :componentId AND component_kind = :componentKind",
    )
    suspend fun delete(componentId: String, componentKind: String)

    @Transaction
    suspend fun replace(
        componentId: String,
        componentKind: String,
        records: List<RecordEntity>,
    ) {
        delete(componentId, componentKind)
        if (records.isNotEmpty()) insert(records)
    }
}
