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

