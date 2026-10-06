package com.eva_cli.core.widgets.provider

import com.eva_cli.core.widgets.event.ContentEvent

abstract class ContentProvider : WidgetProvider() {
    abstract suspend fun input(event: ContentEvent)
    abstract var output: (suspend (event: ContentEvent) -> Unit)?
}

abstract class ModProvider : ContentProvider() {
    abstract val modType: ModType
    enum class ModType {
        MUL,
        TEXT,
        TTS,
        STT,
        IMG,
        VIDEO
    }

    abstract var responseType: ResponseType
    enum class ResponseType {
        FULL,
        STREAM
    }

    abstract var transportType: TransportType
    enum class TransportType {
        HTTP,
        SSE,
        WEBSOCKET,
        WEBRTC,
        GRPC,
        SIP
    }
}

abstract class AgentProvider : ContentProvider() {
    abstract val connectType: ConnectType
    enum class ConnectType {
        CLI,
        MCP,
        MUL
    }
}
