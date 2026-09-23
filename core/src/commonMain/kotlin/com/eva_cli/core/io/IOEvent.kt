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

// 输入事件从 Provider 接收时记录的元数据。
data class InputEventMetadata(
    val sourceProviderId: String? = null,
    val receivedAtMillis: Long? = null,
)

// 输出事件开始投递到 Provider 时记录的元数据。
data class OutputEventMetadata(
    val targetProviderId: String? = null,
    val dispatchStartedAtMillis: Long? = null,
)

// 输入事件和输出事件共用的基础结构。
sealed interface IOEvent {
    val payload: IOPayload

    val providerId: String
        get() = when (this) {
            is InputEvent -> metadata.sourceProviderId.orEmpty()
            is OutputEvent -> metadata.targetProviderId.orEmpty()
        }

    val time: Long
        get() = when (this) {
            is InputEvent -> metadata.receivedAtMillis ?: 0L
            is OutputEvent -> metadata.dispatchStartedAtMillis ?: 0L
        }
}

// 由 [InProvider] 产生的事件。
data class InputEvent(
    override val payload: IOPayload,
    val metadata: InputEventMetadata = InputEventMetadata(),
) : IOEvent {
    fun toOutput(): OutputEvent = OutputEvent(payload)
}

// 投递给 [OutProvider] 的事件。
data class OutputEvent(
    override val payload: IOPayload,
    val metadata: OutputEventMetadata = OutputEventMetadata(),
) : IOEvent
