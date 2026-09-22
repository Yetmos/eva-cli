package com.eva.runtime

import com.eva.core.EvaRuntime
import com.eva.core.HostPlatform
import com.eva.core.RuntimeInfo

// 初始运行时入口；任务执行和沙盒功能尚未实现。
class LocalEvaRuntime(
    private val version: String = RuntimeBuildInfo.VERSION,
) : EvaRuntime {
    override fun info(): RuntimeInfo = RuntimeInfo(
        version = version,
        host = detectHostPlatform(),
    )

    private fun detectHostPlatform(): HostPlatform {
        val osName = System.getProperty("os.name", "")
        return when {
            osName.startsWith("Mac", ignoreCase = true) ||
                osName.equals("Darwin", ignoreCase = true) -> HostPlatform.MACOS
            osName.startsWith("Windows", ignoreCase = true) -> HostPlatform.WINDOWS
            osName.startsWith("Linux", ignoreCase = true) -> HostPlatform.LINUX
            else -> HostPlatform.OTHER
        }
    }
}
