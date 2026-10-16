package com.eva_cli.cmd.api

/** 命令行插件提供的宿主桥接接口。 */
interface CommandLinePort {
    fun bindOutput(handler: (suspend (text: String) -> Unit)?)

    suspend fun submit(line: String)
}
