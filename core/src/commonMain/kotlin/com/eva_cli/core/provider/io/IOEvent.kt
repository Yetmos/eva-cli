package com.eva_cli.core.provider.io

import com.eva_cli.core.provider.ProviderEvent
import kotlin.uuid.Uuid

// IO事件
sealed interface IOEvent : ProviderEvent

// 由 [InProvider] 产生的事件。
data class InEvent(
    override val id: Uuid = Uuid.random(),
    override val payload: ProviderEvent.Payload,
    override val metadata: ProviderEvent.Metadata = ProviderEvent.Metadata(),
) : IOEvent

// 投递给 [OutProvider] 的事件。
data class OutEvent(
    override val id: Uuid = Uuid.random(),
    override val payload: ProviderEvent.Payload,
    override val metadata: ProviderEvent.Metadata = ProviderEvent.Metadata(),
) : IOEvent
