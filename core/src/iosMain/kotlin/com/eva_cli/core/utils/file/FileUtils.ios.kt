package com.eva_cli.core.utils.file

import platform.Foundation.NSFileManager

internal actual fun platformRootPath(): String = NSFileManager.defaultManager.currentDirectoryPath
