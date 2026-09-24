package com.eva_cli.core.io

import kotlin.uuid.Uuid

object IOHandler {
    // 被 IOManager 调用，处理接收的消息；暂时只记录事件，后续再调用其他模块。
    fun handleInput(event: InputEvent) {
        println("[IOHandler] $event")
    }

    // 由 IOManager 投递给指定的输出 Provider；未指定 ID 时广播，投递前实时检查 enable。
    suspend fun sendOutput(event: OutputEvent, providerId: Uuid? = null) {
        IOManager.emit(event, providerId)
    }
}
