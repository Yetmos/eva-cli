package com.eva_cli.core.utils.file

/** Android 调用方须在启动运行时前调用平台扩展 initialize(context)。 */
object FileUtils {

    /** JVM 使用当前工作目录，Android 使用应用 filesDir，iOS 使用 Application Support/Eva。 */
    fun rootPath(): String = platformRootPath()

    /** 创建目录及缺失的父目录；已有目录保留，路径被文件占用或创建失败时抛出异常。 */
    fun createDirectories(path: String) = platformCreateDirectories(path)
}

internal expect fun platformRootPath(): String

internal expect fun platformCreateDirectories(path: String)
