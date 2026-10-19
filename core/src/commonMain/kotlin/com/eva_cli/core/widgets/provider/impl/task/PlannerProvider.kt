package com.eva_cli.core.widgets.provider.impl.task

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider

/**
 * 传递给 [PlannerCapability] 的规划输入。
 *
 * 可以直接通过 [input] 调用规划器，也可以通过 [event] 传入触发规划的原始事件。
 * 同时保留这两种形式，可以让规划器同时适配事件驱动和请求驱动的集成场景，
 * 无需再引入另一种 Provider 类型。
 */
data class PlanRequest(
    val input: String? = null,
    val event: Event? = null,
    val context: Map<String, String> = emptyMap(),
)

/** 计划中的一个可执行或可分发步骤。 */
data class PlanStep(
    val id: String,
    val description: String = id,
    val topic: String? = null,
    val payload: String? = null,
    val dependsOn: List<String> = emptyList(),
    val context: Map<String, String> = emptyMap(),
)

/** 规划器返回的结果。 */
data class Plan(
    val steps: List<PlanStep> = emptyList(),
    val detail: String? = null,
    val complete: Boolean = true,
)

/** 早期插件实现使用的兼容名称。 */
typealias PlanResult = Plan

/** 由规划类 Provider 实现的领域操作。 */
interface PlannerCapability {
    /**
     * 根据 [request] 生成计划。
     * 对于只参与通用 Provider 生命周期的组件，空结果是安全的默认行为。
     */
    suspend fun plan(request: PlanRequest): Plan = Plan()
}

/** 负责规划决策的 Provider 基类。 */
abstract class PlannerProvider : PluginProvider(), PlannerCapability {
    override var status: Provider.Status = Provider.Status.STOP

    override suspend fun start() {
        status = Provider.Status.IDLE
    }

    override suspend fun stop() {
        status = Provider.Status.STOP
    }

    override suspend fun receive(event: Event) = Unit
}
