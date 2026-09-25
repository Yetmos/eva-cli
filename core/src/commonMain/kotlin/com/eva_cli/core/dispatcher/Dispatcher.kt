package com.eva_cli.core.dispatcher

import com.eva_cli.core.provider.ProviderEvent
import com.eva_cli.core.provider.content.ContentEvent
import kotlin.uuid.Uuid

// INFO: topic上事件的调度
interface Dispatcher {
    val id: Uuid
    val name: String
    val desc: String
    val topic: String
    var enable: Boolean

    // 给当前topic的Provider分配任务
    fun dispatch(event: ProviderEvent)

    // 给上级topic的Dispatcher反馈事件的接口回调
    var callSuper: (suspend (event: ProviderEvent) -> Unit)?

}
