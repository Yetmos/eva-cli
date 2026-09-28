package com.eva_cli.core.utils.file

class FileUtils {

    fun rootPath(): String = platformRootPath()

}

internal expect fun platformRootPath(): String
