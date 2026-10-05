package com.eva_cli.core.widgets.dispatcher

import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.provider.Provider
import com.eva_cli.core.widgets.event.Event
import kotlin.uuid.Uuid

abstract class BaseDispatcher: Dispatcher{
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
        for (item in providers.values) {
            item.stop()
        }
        for (item in dispatchers.values) {
            item.stop()
        }
    }

    override var feedUp: (suspend (event: Event) -> Unit)? = null

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

    // INFO: 扫描 当前topic下的 Provider
    override suspend fun scanProviders() {
        TODO("Not yet implemented")
    }

    // INFO: 扫描下一级 topic 对应的 Dispatcher
    override suspend fun scanDispatchers() {
        TODO("Not yet implemented")
    }

    // INFO: 备份
    override suspend fun backup() {
        // TODO: 备份当前 Dispatcher 的 records
    }

    // INFO: 恢复
    override suspend fun restore() {
        // TODO: 恢复当前 Dispatcher 的 records
    }

}
