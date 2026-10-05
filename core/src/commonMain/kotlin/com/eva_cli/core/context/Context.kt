package com.eva_cli.core.context

/**
 * 事件或组件独立持有的记录集合，[items] 按追加顺序排列。
 * 调用方负责保证记录归属：事件履历按 [EventRecord.eventId] 归属当前事件，
 * 组件日志按 [EventRecord.actor] 归属当前组件；本类型不会自动筛选或同步记录。
 *
 * 可通过 `context = context.copy(items = context.items + record)` 追加记录。
 * [List] 只提供只读访问，不保证深度不可变，调用方应避免修改其可变底层集合。
 * `copy` 追加不保证线程安全，同一对象的更新需要串行执行或由调用方同步。
 */
data class Context(
    val items: List<EventRecord> = emptyList(),
)
