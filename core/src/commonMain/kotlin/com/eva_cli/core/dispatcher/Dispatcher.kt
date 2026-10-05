package com.eva_cli.core.dispatcher

import com.eva_cli.core.provider.EventRecord
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

    // INFO: 当前组件的所有决策记录，需要持久化和恢复
    var context: Context

    /** 当前 Dispatcher 对不同事件的决策记录，与事件履历共用同一条 [EventRecord]。 */
    data class Context(val items: List<EventRecord> = emptyList())

    val providers: LinkedHashMap<Uuid, Provider>
    val dispatchers: LinkedHashMap<Uuid, Dispatcher>

    suspend fun start()

    suspend fun stop()

    suspend fun backup()

    suspend fun restore()
    // 决策树备份
    suspend fun backupTree()
    // 决策树恢复
    suspend fun restoreTree()

    // 给当前topic的Provider分配任务 或者 分发给下级topic的Dispatcher处理
    suspend fun dispatch(event: ProviderEvent)

    // 反馈给上级topic的Dispatcher处理
    var feedUp: (suspend (event: ProviderEvent) -> Unit)?

    suspend fun receive(event: ProviderEvent)

    // 扫描当前topic上的所有provider
    suspend fun scanProviders()

    // 扫描所有的下级topic和对应的Dispatcher
    suspend fun scanDispatchers()

}
