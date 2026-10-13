package com.eva_cli.core.widgets.dispatcher

import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.event.ComponentRef
import com.eva_cli.core.widgets.event.RecordsStore
import com.eva_cli.core.widgets.BaseWidget
import com.eva_cli.core.widgets.provider.Provider
import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.plug.PlugManager
import kotlin.uuid.Uuid

abstract class BaseDispatcher: BaseWidget(), Dispatcher {
    override val id: Uuid = Uuid.random()
    override var enable: Boolean = true
    override var records: List<EventRecord> = emptyList()
    override val providers: LinkedHashMap<Uuid, Provider> = linkedMapOf()
    override val dispatchers: LinkedHashMap<Uuid, Dispatcher> = linkedMapOf()

    override suspend fun start() {
        restore()
        scanDispatchers()
        scanProviders()
        for (item in providers.values) {
            item.restore()
            item.emit = this::receive
            item.start()
        }
        for (item in dispatchers.values) {
            item.emit = this::receive
            item.start()
        }
    }

    override suspend fun stop() {
        backup()
        for (item in providers.values) {
            item.backup()
            item.stop()
        }
        for (item in dispatchers.values) {
            item.stop()
        }
    }

    override var emit: (suspend (event: Event) -> Unit)? = null

    // INFO: 决策树备份
    override suspend fun backupTree() {
        backup()
        for (item in providers.values) {
            item.backup()
        }
        for (item in dispatchers.values) {
            item.backupTree()
        }
    }

    // INFO: 决策树恢复
    override suspend fun restoreTree() {
        restore()
        for (item in providers.values) {
            item.restore()
        }
        for (item in dispatchers.values) {
            item.restoreTree()
        }
    }

    /**
     * 刷新当前 topic 的 Provider 映射，保留查询顺序和原实例，包含未启用组件。
     * 重复 ID 或查询失败时保留原映射；不绑定回调或调用生命周期方法。
     * 应在启动前或停止后串行调用，不支持运行中热更新。
     */
    override suspend fun scanProviders() {
        val scanned = linkedMapOf<Uuid, Provider>()
        for (provider in PlugManager.findProviders(topic)) {
            val providerId = provider.id
            check(providerId !in scanned) { "topic=$topic 的 Provider ID 重复：$providerId" }
            scanned[providerId] = provider
        }
        providers.clear()
        providers.putAll(scanned)
    }

    /**
     * 刷新直接子 topic 的 Dispatcher 映射，保留查询顺序和原实例，包含未启用组件。
     * 子节点 ID 不得与当前节点相同，各子节点 ID 和 topic 必须唯一；失败保留原映射。
     * 不递归扫描、绑定回调或调用生命周期方法；应在启动前或停止后串行调用。
     */
    override suspend fun scanDispatchers() {
        val scanned = linkedMapOf<Uuid, Dispatcher>()
        val topics = mutableSetOf<String>()
        for (dispatcher in PlugManager.findChildDispatchers(topic)) {
            val dispatcherId = dispatcher.id
            check(dispatcherId != id) { "子 Dispatcher 与当前节点 ID 相同：$dispatcherId" }
            check(dispatcherId !in scanned) { "topic=$topic 的子 Dispatcher ID 重复：$dispatcherId" }
            check(topics.add(dispatcher.topic)) { "子 topic 的 Dispatcher 重复：${dispatcher.topic}" }
            scanned[dispatcherId] = dispatcher
        }
        dispatchers.clear()
        dispatchers.putAll(scanned)
    }

    // INFO: 备份
    override suspend fun backup() {
        RecordsStore.write(context, ComponentRef.Kind.DISPATCHER, id, records)
    }

    // INFO: 恢复
    override suspend fun restore() {
        records = RecordsStore.read(context, ComponentRef.Kind.DISPATCHER, id)
    }

}
