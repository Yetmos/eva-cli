package com.eva_cli.core.utils.file

import java.nio.file.Path

internal actual fun platformRootPath(): String =
    System.getProperty("user.dir")
        ?.takeIf(String::isNotBlank)
        ?: Path.of(".").toAbsolutePath().normalize().toString()
