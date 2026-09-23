package com.eva_cli.core.io

// 可跨越平台边界的载荷，不暴露 commonMain 之外的平台类型。
sealed interface IOPayload {
    data class Text(val value: String) : IOPayload

    data class Binary(
        val value: ByteArray,
        val mimeType: String? = null,
    ) : IOPayload
}

data class IOEvent(
    val type: IOType,
    val payload: IOPayload,
)

typealias InputEvent = IOEvent
typealias OutputEvent = IOEvent
