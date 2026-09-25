package com.eva_cli.core.log

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

internal actual fun coreLogWriter(): LogWriter = StderrLogWriter()

private class StderrLogWriter : LogWriter() {
    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        System.err.println("[$severity][$tag] $message")
        throwable?.printStackTrace(System.err)
    }
}
