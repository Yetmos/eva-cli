package com.eva_cli.core.widgets.event

/** 在各平台创建使用 Bundled SQLite driver 的 Room 数据库。 */
internal expect fun openRecordsDatabase(path: String): RecordsDatabase
