package com.eva_cli.core.dispatcher

import com.eva_cli.core.provider.Provider
import com.eva_cli.core.provider.ProviderEvent
import kotlin.uuid.Uuid

// INFO: topic上事件的调度
interface Dispatcher {

    val id: Uuid
    val name: String
    val desc: String
    var enable: Boolean
    val topic: String
    val providers: LinkedHashMap<Uuid, Provider>
    val dispatchers: LinkedHashMap<Uuid, Dispatcher>

    // 给当前topic的Provider分配任务
    suspend fun dispatch(event: ProviderEvent)
    // 分发给下级topic的Dispatcher处理
    suspend fun transTo(event: ProviderEvent)
    // 反馈给上级topic的Dispatcher处理
    var feedUp: (suspend (event: ProviderEvent) -> Unit)?
    suspend fun receive(event: ProviderEvent)
    // 扫描当前topic上的所有provider
    suspend fun discoverProviders()
    // 扫描所有的下级topic和对应的Dispatcher
    suspend fun discoverDispatchers()

}
