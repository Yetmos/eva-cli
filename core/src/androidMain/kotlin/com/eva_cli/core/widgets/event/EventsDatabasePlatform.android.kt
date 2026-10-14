package com.eva_cli.core.widgets.event

import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.Dispatchers

private class EventsDatabaseContext(path: String) : ContextWrapper(null) {
    private val databaseFile = File(path)

    override fun getDatabasePath(name: String): File = databaseFile

    override fun getSystemService(name: String): Any? = null
}

internal actual fun openEventsDatabase(path: String): EventsDatabase =
    Room.databaseBuilder<EventsDatabase>(
        context = EventsDatabaseContext(path),
        name = path,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
