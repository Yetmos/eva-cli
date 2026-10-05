package com.eva_cli.core.widget.provider.content

import com.eva_cli.core.widget.event.EventRecord
import com.eva_cli.core.widget.event.Event
import kotlin.uuid.Uuid

data class ContentEvent(
    override val id: Uuid = Uuid.random(),
    override val payload: Event.Payload,
    override var records: List<EventRecord> = emptyList(),
    override val metadata: Event.Metadata = Event.Metadata(),
) : Event
