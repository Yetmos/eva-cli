@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.eva_cli.core.utils.file

import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

internal actual fun platformRootPath(): String = "${NSHomeDirectory()}/Library/Application Support/Eva"

internal actual fun platformCreateDirectories(path: String) {
    check(NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)) {
        "无法创建运行目录：$path，请检查路径是否被文件占用及写入权限"
    }
}
