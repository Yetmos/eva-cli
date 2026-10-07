package com.eva_cli.core.widgets.dispatcher

import com.eva_cli.core.widgets.Widget
import com.eva_cli.core.widgets.provider.Provider
import com.eva_cli.core.widgets.event.Event
import kotlin.uuid.Uuid

// INFO: topic上事件的调度
interface Dispatcher : Widget {

    val providers: LinkedHashMap<Uuid, Provider>
    val dispatchers: LinkedHashMap<Uuid, Dispatcher>

    // 决策树备份
    suspend fun backupTree()
    // 决策树恢复
    suspend fun restoreTree()

    // 给当前topic的Provider分配任务 或者 分发给下级topic的Dispatcher处理
    suspend fun dispatch(event: Event)

    /** 从已加载插件刷新当前 topic 的 Provider 映射，不负责组件启停或回调绑定。 */
    suspend fun scanProviders()

    /** 从已加载插件刷新直接子 topic 的 Dispatcher 映射，不递归扫描。 */
    suspend fun scanDispatchers()

}
