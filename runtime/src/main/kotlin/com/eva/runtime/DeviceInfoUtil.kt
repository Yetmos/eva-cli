package com.eva.runtime

// INFO: 存放设备信息
object DeviceInfoUtil {

	// 获取设备信息
	fun info(): RuntimeInfo = RuntimeInfo(version = RuntimeBuildInfo.VERSION, host = getPlatform())

	private fun getPlatform(): HostPlatform {
		val osName = System.getProperty("os.name", "")
		return when {
			osName.startsWith("Mac", ignoreCase = true) || osName.equals("Darwin", ignoreCase = true) -> HostPlatform.MACOS
			osName.startsWith("Windows", ignoreCase = true) -> HostPlatform.WINDOWS
			osName.startsWith("Linux", ignoreCase = true) -> HostPlatform.LINUX
			else -> HostPlatform.OTHER
		}
	}

}