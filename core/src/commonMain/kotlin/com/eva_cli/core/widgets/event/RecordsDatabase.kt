package com.eva_cli.core.widgets.event

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(entities = [RecordEntity::class], version = 1, exportSchema = true)
@ConstructedBy(RecordsDatabaseConstructor::class)
abstract class RecordsDatabase : RoomDatabase() {
    abstract fun recordsDao(): RecordsDao
}

@Suppress("KotlinNoActualForExpect")
expect object RecordsDatabaseConstructor : RoomDatabaseConstructor<RecordsDatabase> {
    override fun initialize(): RecordsDatabase
}
