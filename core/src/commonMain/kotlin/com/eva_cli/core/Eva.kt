package com.eva_cli.core

import com.eva_cli.core.runtime.Runtime
import com.eva_cli.core.status.Status

// INFO: 模块统一对外暴露接口
object Eva {

	// INFO: 启动
	fun start() {
		Runtime.start()
	}

	// INFO: 获取版本信息
	fun info(): String = Status.sdk

}
