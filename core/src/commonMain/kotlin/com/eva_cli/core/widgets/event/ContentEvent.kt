package com.eva_cli.core.widgets.event

import kotlin.uuid.Uuid

data class ContentEvent(
    override val id: Uuid = Uuid.random(),
    override val payload: Event.Payload,
    override var records: List<EventRecord> = emptyList(),
    override val metadata: Event.Metadata = Event.Metadata(),
) : Event