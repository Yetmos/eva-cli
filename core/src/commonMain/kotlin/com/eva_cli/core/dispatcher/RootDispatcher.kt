package com.eva_cli.core.dispatcher

import com.eva_cli.core.provider.ProviderEvent

object RootDispatcher: BaseDispatcher() {

    override val name: String = "RootDispatcher"
    override val desc: String = "RootDispatcher"
    override val topic: String = "/Root"

    override suspend fun dispatch(event: ProviderEvent) {
        TODO("Not yet implemented")
    }

    override suspend fun receive(event: ProviderEvent) {
        TODO("Not yet implemented")
    }


}