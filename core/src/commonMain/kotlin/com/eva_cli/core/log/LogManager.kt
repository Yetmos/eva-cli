package com.eva_cli.core.log

// INFO: 日志处理库
object LogManager {

	fun init() {
		LogUtil.init()
	}

	// INFO: 日志处理器
	fun log(data: LogData) {
		// 打印到对应平台的日志输出
		logPlatform(data)
		// TODO: 存储，日志分析，状态处理
	}

}
