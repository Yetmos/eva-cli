package com.eva.core

// 命令行与图形界面客户端共用的运行时入口。
interface EvaRuntime {
    fun info(): RuntimeInfo
}

data class RuntimeInfo(
    val version: String,
    val host: HostPlatform,
)

enum class HostPlatform {
    MACOS,
    WINDOWS,
    LINUX,
    OTHER,
}
