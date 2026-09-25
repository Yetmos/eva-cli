package com.eva_cli.core.io

import com.eva_cli.core.log.EventType
import com.eva_cli.core.log.LogUtil
import kotlin.uuid.Uuid

object IOHandler {
    // 被 IOManager 调用，处理接收的消息；暂时只记录事件，后续再调用其他模块。
    fun receive(event: InEvent) {
        println("[IOHandler] $event")
    }

    // 由 IOManager 投递给指定的输出 Provider；未指定 ID 时广播，投递前实时检查 enable。
    suspend fun emit(event: OutEvent) {
        if (event.metadata.providerId == null) {
            handleEmitFail(event, "providerId is null")
            return
        }
        IOManager.emit(event)
    }

    suspend fun handleEmitFail(event: OutEvent, desc: String) {
        LogUtil.logI(EventType.IO, IOLogConfig.EMIT_FAIL, desc)
        // TODO: 进行其他的处理，如降级失败重试等
    }

    suspend fun handleReceiveFail(event: InEvent, desc: String) {
        LogUtil.logI(EventType.IO, IOLogConfig.RECEIVE_FAIL, desc)
        // TODO: 进行其他的处理，如降级失败重试等
    }

}
