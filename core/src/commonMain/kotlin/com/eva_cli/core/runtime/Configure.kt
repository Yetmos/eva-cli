package com.eva_cli.core.runtime

import com.eva_cli.core.utils.file.FileUtils

/**
 * 配置项目的运行环境
 */
object Configure {

    const val PLUGINS_DIR = "Plugins"
    const val LOGS_DIR = "Logs"
    const val DATA_DIR = "Data"
    const val EVENTS_DIR = "Events"

    fun start() {
        checkDic()
    }


    /**
     * 检查项目运行目录下的文件夹，确保存在如下文件夹(已有目录则跳过，不存在则创建对应的文件夹)
     * /Plugins: 放各种 插件(jar包)，jar包里是各种Dispatcher和Provider的具体实现
     * /Logs: 存放本地日志
     * /Data: 存放各种Dispatcher和Provider的备份数据(records，注册表和各种备份数据)
     * /Events: 存放各种处理过的Event记录
     */
    fun checkDic() {
        val rootPath = FileUtils.rootPath()
        for (directory in listOf(PLUGINS_DIR, LOGS_DIR, DATA_DIR, EVENTS_DIR)) {
            FileUtils.createDirectories("$rootPath/$directory")
        }
    }
}
