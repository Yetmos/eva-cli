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

    suspend fun start()

    suspend fun stop()

    suspend fun backup()

    suspend fun restore()

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

abstract class BaseDispatcher: Dispatcher{
    override val id: Uuid = Uuid.random()
    override var enable: Boolean = true
    override val providers: LinkedHashMap<Uuid, Provider> = linkedMapOf()
    override val dispatchers: LinkedHashMap<Uuid, Dispatcher> = linkedMapOf()

    override suspend fun start() {
        restore()
        scanDispatchers()
        scanProviders()
        for (item in providers.values) {
            item.emit = this::receive
            item.start()
        }
        for (item in dispatchers.values) {
            item.feedUp = this::receive
            item.start()
        }
    }

    override suspend fun stop() {
        backup()
    }

    override var feedUp: (suspend (event: ProviderEvent) -> Unit)? = null

    override suspend fun scanProviders() {
        TODO("Not yet implemented")
    }

    override suspend fun scanDispatchers() {
        TODO("Not yet implemented")
    }
}