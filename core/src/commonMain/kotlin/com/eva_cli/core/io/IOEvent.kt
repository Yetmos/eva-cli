package com.eva_cli.core.io

// IO类型
enum class IOType {
    TEXT,
    IMG,
    AUDIO,
    VIDEO,
}

// 可跨越平台边界的载荷，不暴露 commonMain 之外的平台类型。
sealed interface IOPayload {
    // 文本
    data class Text(val value: String) : IOPayload
    // 二进制
    data class Binary(val value: ByteArray, val mimeType: String? = null) : IOPayload
}

data class IOEvent(
    val type: IOType,
    val providerId: String = "",
    val time: Long = 0L,
    val payload: IOPayload,
)

typealias InputEvent = IOEvent
typealias OutputEvent = IOEvent
