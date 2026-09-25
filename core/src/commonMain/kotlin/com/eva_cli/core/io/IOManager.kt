package com.eva_cli.core.io

import com.eva_cli.core.log.EventType
import com.eva_cli.core.log.LogUtil

object IOManager {

    fun start() {
        for (provider in inProviders.values) {
            try {
                provider.start()
            } catch (cause: Throwable) {
                LogUtil.logE(EventType.IO, "Provider start error", desc = cause.message, throwable = cause)
            }
        }
        for (provider in outProviders.values) {
            try {
                provider.start()
            } catch (cause: Throwable) {
                LogUtil.logE(EventType.IO, "Provider start error", desc = cause.message, throwable = cause)
            }
        }
    }

    fun stop() {
        for (provider in inProviders.values) {
            try {
                provider.stop()
            } catch (cause: Throwable) {
                LogUtil.logE(EventType.IO, "Provider stop error", desc = cause.message, throwable = cause)
            }
        }
        for (provider in outProviders.values) {
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
        val provider = outProviders[providerId]
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
        val provider = inProviders[providerId]
            ?: return IOHandler.handleReceiveFail(event, "Provider[$providerId] does not exist")

        if (!provider.enable) {
            return IOHandler.handleReceiveFail(event, "Provider[$providerId] is disabled")
        }

        IOHandler.receive(event)
    }

}
