package com.eva_cli.core.io

/**
 * 与平台无关的输出事件接收端。
 *
 * 平台适配器负责决定如何呈现或传递 [OutputEvent]。
 */
interface OutputProvider {
    fun start() = Unit
    fun stop() = Unit
    suspend fun emit(event: OutputEvent)

}
