package com.eva_cli.core.utils.file

import android.content.Context
import java.io.File

@Volatile
private var applicationRootPath: String? = null

/** 在 Application.onCreate 中调用，为运行目录提供应用私有存储路径。 */
fun FileUtils.Companion.initialize(context: Context) {
    applicationRootPath = context.applicationContext.filesDir.absolutePath
}

internal actual fun platformRootPath(): String = checkNotNull(applicationRootPath) {
    "请先调用 FileUtils.initialize(context) 初始化运行目录"
}

internal actual fun platformCreateDirectories(path: String) {
    val directory = File(path)
    check(directory.isDirectory || directory.mkdirs() || directory.isDirectory) {
        "无法创建运行目录：${directory.absolutePath}，请检查路径是否被文件占用及写入权限"
    }
}
