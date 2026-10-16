package com.eva_cli.core.runtime

import com.eva_cli.core.utils.file.FileUtils

/**
 * 配置项目的运行环境
 */
object Configure {

    const val PLUGINS_DIR = "Plugins"
    /** 每个插件 ID 下用于存放带版本安装文件的目录。 */
    const val PLUGIN_VERSIONS_DIR = "versions"
    const val LOGS_DIR = "Logs"
    const val DATA_DIR = "Data"
    const val EVENTS_DIR = "Events"
    const val BACKUP = "Backup"

    fun start() {
        checkDic()
    }


    /**
     * 检查项目运行目录下的文件夹，确保存在如下文件夹(已有目录则跳过，不存在则创建对应的文件夹)
     * /Plugins: 各种插件的安装目录，插件的数据放在具体的插件目录中
     * /Logs: 存放本地日志
     * /Data: 存放各种Dispatcher和Provider的备份数据(records，注册表和各种备份数据)
     * /Events: 存放各种处理过的Event记录
     * /Backup: 存放插件安装快照
     */
    fun checkDic() {
        val rootPath = FileUtils.rootPath()
        for (directory in listOf(PLUGINS_DIR, LOGS_DIR, DATA_DIR, EVENTS_DIR, BACKUP)) {
            FileUtils.createDirectories("$rootPath/$directory")
        }
    }
}
