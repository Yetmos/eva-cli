package com.eva_cli.core

import com.eva_cli.core.io.IOManager
import com.eva_cli.core.runtime.Runtime
import com.eva_cli.core.status.Status

// INFO: 模块统一对外暴露接口
object Eva {

	// INFO: 启动
	/** 使用平台提供的输入和输出适配器启动运行时。 */
	fun start(ioManager: IOManager): Runtime = Runtime(ioManager).also { it.start() }

	// INFO: 获取版本信息
	fun info(): String = Status.sdk

}
