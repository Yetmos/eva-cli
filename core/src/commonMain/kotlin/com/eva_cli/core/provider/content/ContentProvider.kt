package com.eva_cli.core.provider.content

import kotlin.uuid.Uuid

interface ContentProvider {
    // 实例的唯一标识，创建时确定且保持不变；需要跨重启复用时由实现持久化。
    val id: Uuid
    val name: String
    val desc: String
    var enable: Boolean

    fun input()
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
