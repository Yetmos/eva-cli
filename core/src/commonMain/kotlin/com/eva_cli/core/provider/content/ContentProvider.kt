package com.eva_cli.core.provider.content

import com.eva_cli.core.provider.Provider
import kotlin.uuid.Uuid

interface ContentProvider : Provider {
    suspend fun input(event: ContentEvent)
    var output: (suspend (event: ContentEvent) -> Unit)?
}

interface ModProvider : ContentProvider {
    val modType: ModType
    enum class ModType {
        MUL,
        TEXT,
        TTS,
        STT,
        IMG,
        VIDEO
    }

    var responseType: ResponseType
    enum class ResponseType {
        FULL,
        STREAM
    }

    var transportType: TransportType
    enum class TransportType {
        HTTP,
        SSE,
        WEBSOCKET,
        WEBRTC,
        GRPC,
        SIP
    }
}

interface AgentProvider : ContentProvider {
    val connectType: ConnectType
    enum class ConnectType {
        CLI,
        MCP,
        MUL
    }
}
