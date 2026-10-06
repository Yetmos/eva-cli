package com.eva_cli.core.utils.log

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.platformLogWriter

internal actual fun coreLogWriter(): LogWriter = platformLogWriter()
