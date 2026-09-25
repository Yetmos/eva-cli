package com.eva_cli.core.provider.content

import com.eva_cli.core.provider.ProviderEvent
import kotlin.uuid.Uuid

data class ContentEvent(
    override val id: Uuid = Uuid.random(),
    override val gid: Uuid?,
    override val payload: ProviderEvent.Payload,
    override val metadata: ProviderEvent.Metadata = ProviderEvent.Metadata(),
) : ProviderEvent