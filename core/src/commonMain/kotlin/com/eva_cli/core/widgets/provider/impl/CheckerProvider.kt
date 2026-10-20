package com.eva_cli.core.widgets.provider.impl

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.provider.PluginProvider
import com.eva_cli.core.widgets.provider.Provider

/** 传递给检查器的值和上下文。 */
data class CheckRequest(
    val value: Any? = null,
    val event: Event? = null,
    val context: Map<String, String> = emptyMap(),
)

enum class CheckStatus {
    PASSED,
    REJECTED,
    ERROR,
}

/** 校验结果；如果有诊断信息，也会一并返回。 */
data class CheckResult(
    val status: CheckStatus = CheckStatus.PASSED,
    val detail: String? = null,
    val diagnostics: List<String> = emptyList(),
) {
    constructor(
        passed: Boolean,
        detail: String? = null,
        diagnostics: List<String> = emptyList(),
    ) : this(
        status = if (passed) CheckStatus.PASSED else CheckStatus.REJECTED,
        detail = detail,
        diagnostics = diagnostics,
    )

    val passed: Boolean
        get() = status == CheckStatus.PASSED

    companion object {
        fun passed(detail: String? = null): CheckResult =
            CheckResult(CheckStatus.PASSED, detail)

        fun rejected(
            detail: String? = null,
            diagnostics: List<String> = emptyList(),
        ): CheckResult = CheckResult(CheckStatus.REJECTED, detail, diagnostics)

        fun error(
            detail: String? = null,
            diagnostics: List<String> = emptyList(),
        ): CheckResult = CheckResult(CheckStatus.ERROR, detail, diagnostics)

        fun failed(
            detail: String? = null,
            diagnostics: List<String> = emptyList(),
        ): CheckResult = rejected(detail, diagnostics)
    }
}

/** 由校验和策略类 Provider 实现的领域操作。 */
interface CheckerCapability {
    suspend fun check(request: CheckRequest): CheckResult = CheckResult()
}

/** 校验值、事件或执行策略的 Provider 基类。 */
abstract class CheckerProvider : PluginProvider(), CheckerCapability {
    override var status: Provider.Status = Provider.Status.STOP

    override suspend fun start() {
        status = Provider.Status.IDLE
    }

    override suspend fun stop() {
        status = Provider.Status.STOP
    }

    override suspend fun receive(event: Event) = Unit
}
