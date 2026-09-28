package com.eva_cli.core

import com.eva_cli.core.dispatcher.Booter
import com.eva_cli.core.runtime.Runtime
import com.eva_cli.core.utils.info.Info

// INFO: 模块统一对外暴露接口
object Eva {

	// INFO: 启动
	/** 启动运行时及全局 IOManager 中已注册的适配器。 */
	fun start() {
		Runtime.start()
	}

	// TODO: 停止
	fun stop() {
		Runtime.stop()
	}

	// INFO: 获取版本信息
	fun info(): String = Info.sdk

}
