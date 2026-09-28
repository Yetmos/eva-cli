package com.eva_cli.core.utils.file

import java.io.File

internal actual fun platformRootPath(): String = runCatching {
    File(".").canonicalPath
}.getOrElse {
    File(".").absolutePath
}
