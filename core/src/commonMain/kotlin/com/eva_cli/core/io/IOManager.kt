package com.eva_cli.core.io

import com.eva_cli.core.log.EventType
import com.eva_cli.core.log.LogUtil
import kotlin.uuid.Uuid

object IOManager {

    private val providers = LinkedHashMap<Uuid, IOProvider>()

    /** 按唯一 id 注册；启动失败时撤销本次登记并抛出原异常。 */
    fun register(provider: IOProvider) {
        if (provider !is InProvider && provider !is OutProvider) {
            LogUtil.logI(EventType.IO, IOLogConfig.REGISTER_FAIL, "Not found the provider[${provider.id}] type")
            return
        }

        val id = provider.id
        if (providers.containsKey(id)) {
            LogUtil.logI(EventType.IO, IOLogConfig.REGISTER_FAIL, "The provider[$id] has been registered.")
            return
        }

        providers[id] = provider
        try {
            provider.start()
        } catch (cause: Throwable) {
            if (providers[id] === provider) {
                providers.remove(id)
            }
            throw cause
        }
    }

    /** 仅注销已登记的同一实例；停止失败时保留登记并抛出原异常。 */
    fun unregister(provider: IOProvider) {
        val id = provider.id
        if (providers[id] !== provider) {
            LogUtil.logI(EventType.IO, IOLogConfig.UNREGISTER_FAIL, "The provider[$id] is not the registered instance.")
            return
        }

        provider.stop()
        if (providers[id] === provider) {
            providers.remove(id)
        }
    }

    fun start() {
        for (provider in providers.values) {
            try {
                provider.start()
            } catch (cause: Throwable) {
                LogUtil.logE(EventType.IO, "Provider start error", desc = cause.message, throwable = cause)
            }
        }
    }

    fun stop() {
        for (provider in providers.values) {
            try {
                provider.stop()
            } catch (cause: Throwable) {
                LogUtil.logE(EventType.IO, "Provider stop error", desc = cause.message, throwable = cause)
            }
        }
    }

    // INFO: 对OutProvider发送消息
    suspend fun emit(event: OutEvent) {

        val providerId = event.metadata.providerId
            ?: return IOHandler.handleEmitFail(event, "providerId is null")
        val provider = providers[providerId] as? OutProvider
            ?: return IOHandler.handleEmitFail(event, "Provider[$providerId] does not exist")

        if (!provider.enable) {
            return IOHandler.handleEmitFail(event, "Provider[$providerId] is disabled")
        }

        val status = provider.status
        if (status != IOStatus.IDLE) {
            return IOHandler.handleEmitFail(
                event,
                "Provider[$providerId] is not idle, status is [${status.name}]"
            )
        }

        provider.emit(event)
    }

    // INFO: 处理从InProvider接收的消息
    suspend fun receive(event: InEvent) {
        val providerId = event.metadata.providerId
            ?: return IOHandler.handleReceiveFail(event, "providerId is null")
        val provider = providers[providerId] as? InProvider
            ?: return IOHandler.handleReceiveFail(event, "Provider[$providerId] does not exist")

        if (!provider.enable) {
            return IOHandler.handleReceiveFail(event, "Provider[$providerId] is disabled")
        }

        IOHandler.receive(event)
    }

}
