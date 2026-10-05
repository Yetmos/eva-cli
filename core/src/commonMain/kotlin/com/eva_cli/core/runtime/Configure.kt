package com.eva_cli.core.runtime

import com.eva_cli.core.utils.file.FileUtils

/**
 * 配置项目的运行环境
 */
object Configure {

    fun start() {
        checkDic()
    }


    /**
     * 检查项目运行目录下的文件夹，确保存在如下文件夹(已有目录则跳过，不存在则创建对应的文件夹)
     * /Plugins: 放各种 插件(jar包)，jar包里是各种Dispatcher和Provider的具体实现
     * /Logs: 存放本地日志
     * /Datas: 存放各种Dispatcher和Provider的备份数据(records，注册表和各种备份数据)
     * /Events: 存放各种处理过的Event记录
     */
    fun checkDic() {
        val files = FileUtils()
        val rootPath = files.rootPath()
        for (directory in listOf("Plugins", "Logs", "Datas", "Events")) {
            files.createDirectories("$rootPath/$directory")
        }
    }
}
