package com.eva_cli.core.io

enum class IOType {
    TEXT,
    IMG,
    AUDIO,
    VIDEO,
}

/**
 * 协调一个 Runtime 实例使用的输入和输出适配器。
 *
 * Provider 由平台或应用层提供。管理器负责其生命周期，
 * 并将平台细节隔离在 commonMain 之外。
 * Provider 的 stop 实现应能安全处理 start 部分完成或失败的情况。
 */
class IOManager(
    val inputProvider: InputProvider,
    val outputProvider: OutputProvider,
) {
    private var started = false

    val isStarted: Boolean
        get() = started

    fun start() {
        if (started) return

        try {
            inputProvider.start()
            outputProvider.start()
            started = true
        } catch (cause: Throwable) {
            stopAfterStartFailure(inputProvider::stop, cause)
            stopAfterStartFailure(outputProvider::stop, cause)
            throw cause
        }
    }

    fun stop() {
        if (!started) return

        var failure: Throwable? = null
        try {
            inputProvider.stop()
        } catch (cause: Throwable) {
            failure = cause
        }
        try {
            outputProvider.stop()
        } catch (cause: Throwable) {
            if (failure == null) {
                failure = cause
            } else {
                failure.addSuppressed(cause)
            }
        } finally {
            started = false
        }

        failure?.let { throw it }
    }

    suspend fun receive(): InputEvent? = inputProvider.receive()

    suspend fun emit(event: OutputEvent) {
        outputProvider.emit(event)
    }

    private fun stopAfterStartFailure(stop: () -> Unit, cause: Throwable) {
        try {
            stop()
        } catch (cleanupFailure: Throwable) {
            cause.addSuppressed(cleanupFailure)
        }
    }
}
