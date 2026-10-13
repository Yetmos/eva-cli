package com.eva_cli.core.widgets.event

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.Dispatchers

/**
 * Room 的 Android builder 要求 Context；BundledSQLiteDriver 实际直接使用绝对文件路径，
 * 因此只提供用于 journal mode 和路径解析的最小 Context，避免把宿主 Context 泄漏到插件层。
 */
private class RecordsDatabaseContext(path: String) : ContextWrapper(null) {
    private val databaseFile = File(path)

    override fun getDatabasePath(name: String): File = databaseFile

    override fun getSystemService(name: String): Any? = null
}

internal actual fun openRecordsDatabase(path: String): RecordsDatabase =
    Room.databaseBuilder<RecordsDatabase>(
        context = RecordsDatabaseContext(path),
        name = path,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
