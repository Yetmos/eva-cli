package com.eva.core

// 对外暴露的接口
object Eva {

	fun start() {

	}

	fun info(): RuntimeInfo {
		return DevUtil.info()
	}

}