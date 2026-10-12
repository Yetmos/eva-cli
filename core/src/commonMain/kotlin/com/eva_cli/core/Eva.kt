package com.eva_cli.core

import com.eva_cli.core.runtime.Runtime
import com.eva_cli.core.runtime.Booter
import com.eva_cli.core.utils.info.Info
import com.eva_cli.core.widgets.event.Event

// INFO: 模块统一对外暴露接口
object Eva {

	// INFO: 启动
	/** 启动运行时及全局 IOManager 中已注册的适配器。 */
	suspend fun start() {
		Runtime.start()
	}

	// INFO: 停止
	suspend fun stop() {
		Runtime.stop()
	}

	/** 将事件提交给已启动的根 Dispatcher。 */
	suspend fun dispatch(event: Event) {
		Booter.dispatch(event)
	}

	// INFO: 获取版本信息
	fun info(): String = Info.sdk

}
