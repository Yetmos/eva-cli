package com.eva_cli.core.widgets.plug

import com.eva_cli.core.widgets.dispatcher.PluginDispatcher
import com.eva_cli.core.widgets.provider.PluginProvider

/**
 * 插件代码入口；[Plugin] 保存安装信息，[PluginManifest] 描述发布产物。
 *
 * JVM/Android 入口须提供公开无参构造器，类名与清单的 entryClass 一致。
 * 同一入口实例应始终提供同一组组件实例。宿主先绑定 WidgetContext，再按 topic 注册组件、绑定回调，
 * 并调用组件的 start/stop；读取这些属性不会启动组件。
 */
interface PluginEntry {
    val providers: List<PluginProvider>
        get() = emptyList()

    val dispatchers: List<PluginDispatcher>
        get() = emptyList()
}
