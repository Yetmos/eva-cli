package com.eva_cli.core.widgets.provider.impl.tool

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider

/** 传递给输入适配器的请求。 */
data class InputRequest(
    val prompt: String? = null,
    val event: Event? = null,
    val context: Map<String, String> = emptyMap(),
)

/** 输入适配器产生的值。 */
data class InputResult(
    val value: String? = null,
    val event: Event? = null,
    val endOfInput: Boolean = false,
    val payload: Event.Payload? = null,
) {
    val isEnd: Boolean
        get() = endOfInput
}

/** 发送给输出适配器的请求。 */
data class OutputRequest(
    val value: String? = null,
    val event: Event? = null,
    val context: Map<String, String> = emptyMap(),
    val payload: Event.Payload? = null,
)

/** 输出适配器写入后的结果。 */
data class OutputResult(
    val accepted: Boolean = true,
    val detail: String? = null,
)

/** 从外部来源读取输入的 Provider 能力。 */
interface InputCapability {
    /** 读取一个值。返回 null 表示当前没有可读取的值。 */
    suspend fun input(request: InputRequest = InputRequest()): InputResult? = null


    suspend fun read(request: InputRequest = InputRequest()): InputResult? = input(request)
}

/** 向外部目标写入值的 Provider 能力。 */
interface OutputCapability {
    /** 写入一个值，并报告目标是否接受该值。 */
    suspend fun output(request: OutputRequest): OutputResult = OutputResult()

    /** 面向使用常见流式术语的集成场景提供的写入别名。 */
    suspend fun write(request: OutputRequest): OutputResult = output(request)
}

/** 同时支持输入和输出方向的适配器能力。 */
interface IOCapability : InputCapability, OutputCapability

/**
 * 连接宿主与输入/输出系统的 Provider 基类。
 *
 * 两种能力都提供空操作默认实现，因此适配器只需实现自身支持的方向。
 * [InputProvider] 和 [OutputProvider] 是对应单一方向的便捷子类。
 */
abstract class IOProvider : PluginProvider(), IOCapability {
    override var status: Provider.Status = Provider.Status.STOP

    override suspend fun start() {
        status = Provider.Status.IDLE
    }

    override suspend fun stop() {
        status = Provider.Status.STOP
    }

    override suspend fun receive(event: Event) = Unit
}

/** 仅支持输入适配器的 Provider 类型。 */
abstract class InputProvider : IOProvider()

/** 仅支持输出适配器的 Provider 类型。 */
abstract class OutputProvider : IOProvider()
