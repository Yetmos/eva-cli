package com.eva_cli.core.widgets.provider.io

import com.eva_cli.core.widgets.event.EventRecord
import com.eva_cli.core.widgets.event.Event
import kotlin.uuid.Uuid

// IO事件
sealed interface IOEvent : Event

// 由 [InProvider] 产生的事件。
data class InEvent(
    override val id: Uuid = Uuid.random(),
    override val payload: Event.Payload,
    override val metadata: Event.Metadata = Event.Metadata(),
    override var records: List<EventRecord> = emptyList(),
) : IOEvent

// 投递给 [OutProvider] 的事件。
data class OutEvent(
    override val id: Uuid = Uuid.random(),
    override val payload: Event.Payload,
    override val metadata: Event.Metadata = Event.Metadata(),
    override var records: List<EventRecord> = emptyList(),
) : IOEvent
