package com.eva_cli.core.io

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid


// 输入事件和输出事件共用的基础结构。
sealed interface IOEvent {
    val id: Uuid
    // 分组id
    val gid: Uuid?
    val metadata: Metadata
    data class Metadata(
        var providerId: Uuid? = null,
        val createdAt: Instant = Clock.System.now(),
    )

    val payload: IOPayload
    sealed interface IOPayload {
        data class Text(val value: String) : IOPayload

        // 二进制数据在写入和读取时都会复制，避免某个输出 Provider 修改其他  Provider 看到的字节内容
        class Binary(value: ByteArray, val mimeType: String? = null) : IOPayload {
            private val bytes = value.copyOf()

            // 返回新的字节数组副本，避免调用方修改事件内容。
            fun toByteArray(): ByteArray = bytes.copyOf()

            val size: Int
                get() = bytes.size

            override fun equals(other: Any?): Boolean =
                other is Binary &&
                        mimeType == other.mimeType &&
                        bytes.contentEquals(other.bytes)

            override fun hashCode(): Int = 31 * bytes.contentHashCode() + (mimeType?.hashCode() ?: 0)

            override fun toString(): String =
                "Binary(size=$size, mimeType=$mimeType)"
        }
    }

}

// 由 [InProvider] 产生的事件。
data class InEvent(
    override val id: Uuid = Uuid.random(),
    override val gid: Uuid?,
    override val payload: IOEvent.IOPayload,
    override val metadata: IOEvent.Metadata = IOEvent.Metadata(),
) : IOEvent

// 投递给 [OutProvider] 的事件。
data class OutEvent(
    override val id: Uuid = Uuid.random(),
    override val gid: Uuid?,
    override val payload: IOEvent.IOPayload,
    override val metadata: IOEvent.Metadata = IOEvent.Metadata(),
) : IOEvent
