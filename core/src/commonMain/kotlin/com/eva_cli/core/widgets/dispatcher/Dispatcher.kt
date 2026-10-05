package com.eva_cli.core.widgets.dispatcher

import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.provider.Provider
import com.eva_cli.core.widgets.event.Event
import kotlin.uuid.Uuid

// INFO: topic上事件的调度
interface Dispatcher {

    val id: Uuid
    val name: String
    val desc: String
    var enable: Boolean
    val topic: String
    /**
     * 当前 Dispatcher 的决策记录，按追加顺序排列；调用方保证 actor 指向当前组件。
     * [List] 只读接口不保证深度不可变，应避免修改底层集合，并发更新需同步。
     */
    var records: List<EventRecord>
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
    suspend fun dispatch(event: Event)

    // 反馈给上级topic的Dispatcher处理
    var feedUp: (suspend (event: Event) -> Unit)?

    suspend fun receive(event: Event)

    // 扫描当前topic上的所有provider
    suspend fun scanProviders()

    // 扫描所有的下级topic和对应的Dispatcher
    suspend fun scanDispatchers()

}
