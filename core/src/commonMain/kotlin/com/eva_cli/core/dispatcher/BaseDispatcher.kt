package com.eva_cli.core.dispatcher

import com.eva_cli.core.provider.Provider
import com.eva_cli.core.provider.ProviderEvent
import kotlin.uuid.Uuid

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
        for (item in providers.values) {
            item.stop()
        }
        for (item in dispatchers.values) {
            item.stop()
        }
    }

    override var feedUp: (suspend (event: ProviderEvent) -> Unit)? = null

    override suspend fun scanProviders() {
        TODO("Not yet implemented")
    }

    override suspend fun scanDispatchers() {
        TODO("Not yet implemented")
    }

    override suspend fun backup() {
        TODO("Not yet implemented")
    }

    override suspend fun restore() {
        TODO("Not yet implemented")
    }
}