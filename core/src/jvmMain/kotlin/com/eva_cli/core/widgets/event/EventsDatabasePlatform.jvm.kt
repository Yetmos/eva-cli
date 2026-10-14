package com.eva_cli.core.widgets.event

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

internal actual fun openEventsDatabase(path: String): EventsDatabase =
    Room.databaseBuilder<EventsDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
