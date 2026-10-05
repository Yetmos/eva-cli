package com.eva_cli.core.widgets.event

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * 一次调度决策或处理结果，可同时保存在事件履历和执行组件的日志中。
 * 只通过 [eventId] 和组件标识关联对象，避免记录引用完整事件而形成循环引用。
 *
 * @property actor 实际做出决策或完成处理的组件。
 */
data class EventRecord(
    val id: Uuid = Uuid.random(),
    val eventId: Uuid,
    val occurredAt: Instant = Clock.System.now(),
    val actor: ComponentRef,
    val action: Action,
)

// 组件标识：Provider 或 Dispatcher
data class ComponentRef(
    val id: Uuid,
    val kind: Kind,
) {
    enum class Kind {
        PROVIDER,
        DISPATCHER,
    }
}

// 调度决策/处理结果
sealed interface Action {
    /**
     * 调度决策仅表示准备将事件交给 [target]，不代表已经送达或处理成功。
     * [reason] 用于记录选择该流向和目标的原因。
     */
    data class Dispatch(
        val type: DispatchType,
        val target: ComponentRef,
        val reason: String? = null,
    ) : Action

    // 组件处理事件后的结果，[detail] 用于补充结果说明或错误信息。
    data class Handle(
        val result: Result,
        val detail: String? = null,
    ) : Action
}

/** 调度决策中的事件流向。 */
enum class DispatchType {
    // 交给 Provider 处理。
    HANDLE,
    // 流转给下一级 Dispatcher 处理。
    DISPATCH,
    // 上报给上一级 Dispatcher 处理。
    FEED_UP,
}

/** Provider 对事件的处理结果。 */
enum class Result {
    // 处理完成，无需反馈结果，终结事件。
    DONE,
    // 无效事件，终结事件。
    END,
    // 处理出错，反馈给 Dispatcher。
    BACK,
    // 处理完成，返回 Dispatcher 继续分发给其他组件。
    DO_BACK,
}
