package com.eva_cli.core.dispatcher

import com.eva_cli.core.provider.ProviderEvent

class RootDispatcher: BaseDispatcher() {

    override val name: String = "RootDispatcher"
    override val desc: String = "RootDispatcher"
    override val topic: String = "/Root"

    // INFO: 把消息分发给下级Dispatcher或者交给Provider处理
    override suspend fun dispatch(event: ProviderEvent) {
        TODO("Not yet implemented")
    }

    // INFO: 处理从下级Dispatcher上报的消息，或者从Provider发出的消息
    override suspend fun receive(event: ProviderEvent) {
        TODO("Not yet implemented")
    }

}