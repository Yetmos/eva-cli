package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.event.Event
import com.eva_cli.core.widgets.event.EventRecord
import kotlin.uuid.Uuid

internal class TestEvent(override val payload: Event.Payload) : Event {
    override val id: Uuid = Uuid.random()
    override val metadata = Event.Metadata()
    override var records: List<EventRecord> = emptyList()
}
