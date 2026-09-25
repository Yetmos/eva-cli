package com.eva_cli.core.dispatcher

import com.eva_cli.core.provider.ProviderEvent
import com.eva_cli.core.provider.io.IOProvider
import kotlin.uuid.Uuid

// INFO: topic上事件的调度
interface Dispatcher {
    val id: Uuid
    val name: String
    val desc: String
    var enable: Boolean
    val topic: String
    val providers: LinkedHashMap<Uuid, IOProvider>

    // 给当前topic的Provider分配任务
    fun dispatch(event: ProviderEvent)
    // 反馈给上级topic
    var feedUp: (suspend (event: ProviderEvent) -> Unit)?
    // 扫描当前topic上的所有provider
    fun discover()
}
