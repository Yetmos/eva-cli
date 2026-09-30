package com.eva_cli.core.provider

import com.eva_cli.core.dispatcher.Dispatcher
import kotlin.uuid.Uuid

interface Provider {
    val id: Uuid
    val name: String
    val desc: String
    val topic: String
    var enable: Boolean
    var status: Status
    enum class Status {
        STOP,
        STARTING,
        IDLE,
        WORKING,
    }

    // INFO: 当前组件的所有任务执行记录，需要持久化
    var context: Context
    data class Context(val items: List<Dispatcher.Context.ContextItem> = listOf()) {
        data class ContextItem(
            val id: Uuid,
            val handleTime: Long,
            val handleId: Uuid,
            val handleType: HandleType,
            val event: ProviderEvent
        ) {
            // INFO:
            // [DONE] 处理完毕，无效反馈结果，终结事件
            // [END]  无效事件，终结事件
            // [BACK] 错误事件，反馈给Dispatcher
            enum class HandleType{ DONE, END, BACK, FEED_UP}
        }
    }

    suspend fun receive(event: ProviderEvent)

    var emit: (suspend (event: ProviderEvent) -> Unit)?


    fun start() = Unit

    fun stop() = Unit

    suspend fun backup()

    suspend fun restore()

}