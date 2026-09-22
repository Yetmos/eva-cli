package com.eva.runtime

enum class HostPlatform {
    MACOS,
    WINDOWS,
    LINUX,
    OTHER,
}

data class RuntimeInfo(
    val version: String,
    val host: HostPlatform,
)

// 初始运行时入口；任务执行和沙盒功能尚未实现。
object Runtime{

    fun start() {

    }

}
