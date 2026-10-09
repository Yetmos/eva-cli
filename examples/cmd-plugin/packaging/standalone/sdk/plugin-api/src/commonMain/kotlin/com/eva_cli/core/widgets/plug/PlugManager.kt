package com.eva_cli.core.widgets.plug

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.provider.PluginProvider

/** 独立插件 SDK 的编译入口；正式运行时由宿主提供已加载组件的查询实现。 */
object PlugManager {
    fun findProviders(topic: String): List<PluginProvider> =
        throw UnsupportedOperationException("查询 Provider 需要宿主运行时：$topic")

    fun findChildDispatchers(topic: String): List<PluginDispatcher> =
        throw UnsupportedOperationException("查询子 Dispatcher 需要宿主运行时：$topic")
}
