package com.eva_cli.core.utils.file

import java.io.File

internal actual fun platformRootPath(): String = checkNotNull(System.getProperty("user.dir")) {
    "无法获取当前工作目录：user.dir 未设置"
}

internal actual fun platformCreateDirectories(path: String) {
    val directory = File(path)
    check(directory.isDirectory || directory.mkdirs() || directory.isDirectory) {
        "无法创建运行目录：${directory.absolutePath}，请检查路径是否被文件占用及写入权限"
    }
}
