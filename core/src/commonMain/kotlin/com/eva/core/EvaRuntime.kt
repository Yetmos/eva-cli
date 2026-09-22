package com.eva.core

/** Entry point shared by terminal and graphical clients. */
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
