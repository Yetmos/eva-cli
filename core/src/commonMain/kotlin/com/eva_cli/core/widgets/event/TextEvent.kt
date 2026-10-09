package com.eva_cli.core.widgets.event

import kotlin.uuid.Uuid

/** A host-created text event used by CLI and other thin input adapters. */
class TextEvent(
    text: String,
    override val id: Uuid = Uuid.random(),
    override val metadata: Event.Metadata = Event.Metadata(),
) : Event {
    override var records: List<EventRecord> = emptyList()
    override val payload: Event.Payload = Event.Payload.Text(text)
}
