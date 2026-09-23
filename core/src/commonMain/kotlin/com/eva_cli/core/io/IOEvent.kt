package com.eva_cli.core.io


sealed interface IOPayload {
    data class Text(val value: String) : IOPayload

    /**
     * 二进制数据在写入和读取时都会复制，避免某个输出 Provider 修改其他
     * Provider 看到的字节内容。
     */
    class Binary(value: ByteArray, val mimeType: String? = null) : IOPayload {
        private val bytes = value.copyOf()

        /** 返回副本，避免调用方直接修改事件内容。 */
        val value: ByteArray
            get() = bytes.copyOf()

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

enum class EventType {
    INPUT,
    OUTPUT
}

data class Metadata(
    val eventType: EventType,
    val providerId: String? = null,
    val time: Long? = null,
)

// 输入事件和输出事件共用的基础结构。
sealed interface IOEvent {
    val metadata: Metadata
    val payload: IOPayload
}

// 由 [InProvider] 产生的事件。
data class InputEvent(
    override val payload: IOPayload,
    override val metadata: Metadata = Metadata(EventType.OUTPUT),
) : IOEvent

// 投递给 [OutProvider] 的事件。
data class OutputEvent(
    override val payload: IOPayload,
    override val metadata: Metadata = Metadata(EventType.INPUT),
) : IOEvent
