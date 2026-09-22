package com.eva.core

// 对外暴露的接口
object EvaCore {

	fun info(): RuntimeInfo {
		return DeviceInfoUtil.info()
	}

}