package com.eva_cli.core.status

enum class Platform {
	MACOS,
	WINDOWS,
	LINUX,
	Android,
	IOS
}

object Status{

	// TODO: SDK版本
	val sdk = "sdkVersion"

	// TODO: 设备类型
	val platform = Platform.MACOS

	// TODO: 设备ID
	val deviceId = ""

}