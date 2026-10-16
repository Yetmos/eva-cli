package com.eva_cli.core.runtime

import com.eva_cli.core.widgets.event.EventStore
import com.eva_cli.core.widgets.plug.PlugManager
import kotlin.concurrent.Volatile

/** 宿主运行时生命周期协调器。 */
object Runtime {

    @Volatile
    private var currentStatus: RuntimeStatus = RuntimeStatus.STOPPED

    /** 当前宿主生命周期状态。 */
    val status: RuntimeStatus
        get() = currentStatus

    /**
     * 加载已安装插件并启动调度器树。生命周期调用会被串行化，已完成的启动操作具备幂等性。
     * [PlugManager] 的同步文件操作不受此门保护，宿主必须将其与生命周期操作串行执行。
     */
    suspend fun start() = RuntimeGate.withLock {
        if (currentStatus == RuntimeStatus.RUNNING) return@withLock
        check(currentStatus == RuntimeStatus.STOPPED) {
            "Runtime is already ${currentStatus.name.lowercase()}"
        }

        currentStatus = RuntimeStatus.STARTING
        val previouslyLoaded = try {
            Configure.start()
            PlugManager.loadedList.mapTo(mutableSetOf()) { it.manifest.id }
        } catch (error: Throwable) {
            currentStatus = RuntimeStatus.STOPPED
            throw error
        }
        val booterWasRunning = Booter.isRunning
        try {
            Plugin.start()
            Booter.startLocked()
            currentStatus = RuntimeStatus.RUNNING
        } catch (error: Throwable) {
            if (!booterWasRunning) {
                try {
                    Booter.stopLocked()
                } catch (cleanupError: Throwable) {
                    if (error !== cleanupError) error.addSuppressed(cleanupError)
                }
            }

            try {
                val newlyLoaded = PlugManager.loadedList
                    .asSequence()
                    .map { it.manifest.id }
                    .filter { it !in previouslyLoaded }
                    .toSet()
                Plugin.stopOnly(newlyLoaded)
            } catch (cleanupError: Throwable) {
                if (error !== cleanupError) error.addSuppressed(cleanupError)
            }
            if (!booterWasRunning) {
                try {
                    EventStore.closeRuntime()
                } catch (cleanupError: Throwable) {
                    if (error !== cleanupError) error.addSuppressed(cleanupError)
                }
            }
            currentStatus = RuntimeStatus.STOPPED
            throw error
        }
    }

    /**
     * 停止调度器树、释放插件上下文并关闭运行时事件存储。所有清理步骤都会尝试执行，
     * 失败会被聚合。重复调用是安全的。
     */
    suspend fun stop() = RuntimeGate.withLock {
        currentStatus = RuntimeStatus.STOPPING
        var failure: Throwable? = null
        try {
            Booter.stopLocked()
        } catch (error: Throwable) {
            failure = error
        }

        try {
            Plugin.stop()
        } catch (error: Throwable) {
            if (failure == null) failure = error
            else if (failure !== error) failure.addSuppressed(error)
        }
        try {
            EventStore.closeRuntime()
        } catch (error: Throwable) {
            if (failure == null) failure = error
            else if (failure !== error) failure.addSuppressed(error)
        }

        currentStatus = RuntimeStatus.STOPPED
        failure?.let { throw it }
    }
}
