package com.eva_cli.core.widget.event

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface Event {
    val id: Uuid
    val metadata: Metadata
    data class Metadata(
        var providerId: Uuid? = null,
        val createdAt: Instant = Clock.System.now(),
    )

    /**
     * 当前事件的流转和处理记录，按追加顺序排列；调用方保证 eventId 指向当前事件。
     * [List] 只读接口不保证深度不可变，应避免修改底层集合，并发更新需同步。
     */
    var records: List<EventRecord>

    val payload: Payload
    sealed interface Payload {
        data class Text(val value: String) : Payload
        // 二进制数据在写入和读取时都会复制，避免某个输出 Provider 修改其他  Provider 看到的字节内容
        class Binary(value: ByteArray, val mimeType: String? = null) : Payload {
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