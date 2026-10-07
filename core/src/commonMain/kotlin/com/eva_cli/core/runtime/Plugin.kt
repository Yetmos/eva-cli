package com.eva_cli.core.runtime

import com.eva_cli.core.widgets.plug.PlugManager

object Plugin {

    /**
     * 加载所有有效的已安装插件，供随后启动的 Dispatcher 树扫描。
     * 同一 ID 存在多个安装版本时拒绝启动；已有同版本、同路径实例由加载器复用。
     * 失败时逆序释放本次新增实例，保留调用前已加载的实例，清理错误附加到原异常。
     * 此阶段不绑定回调或启停组件。包含同步文件 I/O，应在后台线程、组件启动前调用，
     * 且须与其他插件安装、加载、释放及运行目录变更操作串行执行。
     * Android/iOS 当前随 PlugManager 抛出 UnsupportedOperationException。
     */
    fun start() {
        val installed = PlugManager.installedList
        val conflicts = installed.groupBy { it.manifest.id }.filterValues { it.size > 1 }
        check(conflicts.isEmpty()) {
            "插件存在多个安装版本，启动前请为每个 ID 保留一个版本：" +
                conflicts.entries.joinToString("; ") { (id, plugins) ->
                    "$id: ${plugins.joinToString(", ") { it.manifest.version }}"
                }
        }
        if (installed.isEmpty()) return

        val previouslyLoaded = PlugManager.loadedList.mapTo(mutableSetOf()) { it.manifest.id }
        val newlyLoaded = mutableListOf<String>()
        try {
            for (plugin in installed) {
                val manifest = plugin.manifest
                PlugManager.loadPlugin(manifest.id, manifest.version)
                if (manifest.id !in previouslyLoaded) newlyLoaded.add(manifest.id)
            }
        } catch (error: Throwable) {
            for (id in newlyLoaded.asReversed()) {
                try {
                    PlugManager.unloadPlugin(id)
                } catch (cleanupError: Throwable) {
                    error.addSuppressed(cleanupError)
                }
            }
            throw error
        }
    }

}
