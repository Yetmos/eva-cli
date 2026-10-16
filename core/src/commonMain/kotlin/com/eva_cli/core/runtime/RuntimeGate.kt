package com.eva_cli.core.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 串行化生命周期操作和根调度器操作。
 *
 * Runtime 持有此门时会调用 Booter 的 `*Locked` 方法，这些方法不得再次获取此门。
 */
internal object RuntimeGate {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock {
        block()
    }
}
