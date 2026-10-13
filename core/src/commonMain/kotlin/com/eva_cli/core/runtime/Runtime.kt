package com.eva_cli.core.runtime

import com.eva_cli.core.widgets.plug.PlugManager

// 使用全局 IO 管理器；所有 Runtime 实例共享其启停状态。
object Runtime {

    /**
     * Loads installed plugins and starts the dispatcher tree.
     *
     * Calls must be serialized with other lifecycle and plugin-management
     * operations. If the tree cannot start, the failed tree is stopped and
     * only plugins loaded by this invocation are released.
     */
    suspend fun start() {
        Configure.start()
        val previouslyLoaded = PlugManager.loadedList
            .mapTo(mutableSetOf()) { it.manifest.id }
        val booterWasRunning = Booter.isRunning
        try {
            Plugin.start()
            Booter.start()
        } catch (error: Throwable) {
            // Booter retains a failed root so that it can be stopped and
            // retried. Do not stop an already-running tree when a concurrent
            // or repeated start attempt fails.
            if (!booterWasRunning) {
                try {
                    Booter.stop()
                } catch (cleanupError: Throwable) {
                    if (error !== cleanupError) error.addSuppressed(cleanupError)
                }
            }

            val newlyLoaded = PlugManager.loadedList
                .asSequence()
                .map { it.manifest.id }
                .filter { it !in previouslyLoaded }
                .toSet()
            try {
                Plugin.stopOnly(newlyLoaded)
            } catch (cleanupError: Throwable) {
                if (error !== cleanupError) error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    /**
     * Stops the dispatcher tree, then releases every loaded plugin context and
     * class loader. All releases are attempted; cleanup failures are
     * aggregated and the first failure is rethrown. Repeated calls are safe.
     */
    suspend fun stop() {
        var failure: Throwable? = null
        try {
            // Stop the component tree before closing the contexts that back it.
            Booter.stop()
        } catch (error: Throwable) {
            failure = error
        }

        // Always release plugin contexts and class loaders, even if a
        // dispatcher failed while stopping.  Plugin.stop is idempotent, so a
        // repeated Runtime.stop() is a no-op after this point.
        try {
            Plugin.stop()
        } catch (error: Throwable) {
            if (failure == null) {
                failure = error
            } else if (failure !== error) {
                failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

}
