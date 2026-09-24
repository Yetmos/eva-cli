package com.eva_cli.core.io

import com.eva_cli.core.log.EventType
import com.eva_cli.core.log.LogUtil

object IOManager {

    fun start() {
        for (provider in inProviders.values) {
            try {
                provider.start()
            } catch (cause: Throwable) {
                LogUtil.logE(EventType.IO, "Provider start error ", cause.message)
            }
        }
        for (provider in outProviders.values) {
            try {
                provider.start()
            } catch (cause: Throwable) {
                LogUtil.logE(EventType.IO, "Provider start error ", cause.message)
            }
        }
    }

    fun stop() {
        for (provider in inProviders.values) {
            try {
                provider.stop()
            } catch (cause: Throwable) {
                LogUtil.logE(EventType.IO, "Provider stop error ", cause.message)
            }
        }
        for (provider in outProviders.values) {
            try {
                provider.stop()
            } catch (cause: Throwable) {
                LogUtil.logE(EventType.IO, "Provider stop error ", cause.message)
            }
        }
    }

    suspend fun emit(event: OutputEvent) {
        outProviders[event.metadata.providerId]?.apply {
            if (enable) {
                emit(event)
            }
        }
    }

    suspend fun receive(event: InputEvent) {
        inProviders[event.metadata.providerId]?.apply {
            if (enable) {
                IOHandler.handleInput(event)
            }
        }
    }

}
