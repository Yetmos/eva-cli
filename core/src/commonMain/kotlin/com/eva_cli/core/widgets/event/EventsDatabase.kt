package com.eva_cli.core.widgets.event

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(entities = [EventEntity::class, EventRecordEntity::class], version = 1, exportSchema = true)
@ConstructedBy(EventsDatabaseConstructor::class)
abstract class EventsDatabase : RoomDatabase() {
    abstract fun eventsDao(): EventsDao
}

@Suppress("KotlinNoActualForExpect")
expect object EventsDatabaseConstructor : RoomDatabaseConstructor<EventsDatabase> {
    override fun initialize(): EventsDatabase
}
