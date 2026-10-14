package com.eva_cli.core.widgets.event

/** 创建宿主级事件库，独立于插件 records.db。 */
internal expect fun openEventsDatabase(path: String): EventsDatabase
