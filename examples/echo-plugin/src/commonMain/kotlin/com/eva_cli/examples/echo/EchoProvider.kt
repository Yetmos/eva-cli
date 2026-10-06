package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.provider.Provider
import com.eva_cli.core.widgets.provider.WidgetProvider
import kotlin.uuid.Uuid

/** 最小文本处理示例；宿主须串行调用生命周期方法和 receive。 */
class EchoProvider : WidgetProvider() {
    override val id: Uuid = Uuid.random()
    override val name = "Echo Provider"
    override val desc = "Replies to text events with an Echo: prefix"
    override val topic = "/Echo"
    override var enable = true
    override var status = Provider.Status.STOP
    override var records: List<EventRecord> = emptyList()
    override var emit: (suspend (event: Event) -> Unit)? = null

    override fun start() {
        status = Provider.Status.IDLE
    }

    override fun stop() {
        status = Provider.Status.STOP
    }

    override suspend fun receive(event: Event) {
        if (!enable || status == Provider.Status.STOP) return
        val text = event.payload as? Event.Payload.Text ?: return
        val output = checkNotNull(emit) { "Bind EchoProvider.emit before sending text events" }

        status = Provider.Status.WORKING
        try {
            output(EchoReply("Echo: ${text.value}", id))
        } finally {
            // 输出回调可能停止组件，完成本次处理时保留 STOP 状态。
            if (status != Provider.Status.STOP) status = Provider.Status.IDLE
        }
    }
}

private class EchoReply(text: String, providerId: Uuid) : Event {
    override val id: Uuid = Uuid.random()
    override val metadata = Event.Metadata(providerId = providerId)
    override var records: List<EventRecord> = emptyList()
    override val payload = Event.Payload.Text(text)
}
