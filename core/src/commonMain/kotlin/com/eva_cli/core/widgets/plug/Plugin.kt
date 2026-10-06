package com.eva_cli.core.widgets.plug

import kotlinx.serialization.json.JsonObject

/** 插件身份由 plugin.json 提供，启用状态和业务配置由宿主管理。 */
data class Plugin(
    val id: String,
    val name: String,
    val source: PluginSource,
    val enabled: Boolean = true,
    val config: JsonObject = JsonObject(emptyMap()),
)

// 插件的获取位置，与 plugin.json 中描述包内源码位置的 source 不同。
sealed interface PluginSource {
    val sourceType: SourceType
    enum class SourceType { URL, GIT, FILE, }

    /** [url] 指向插件发布包，[sha256] 为可选的预期文件摘要。 */
    data class Url(
        val url: String,
        val sha256: String? = null,
    ) : PluginSource {
        override val sourceType: SourceType
            get() = SourceType.URL
    }

    /**
     * [ref] 为分支、标签或提交号，未指定时使用仓库默认分支。
     * [directory] 为仓库内插件工程的相对目录。
     */
    data class Git(
        val repository: String,
        val ref: String? = null,
        val directory: String = ".",
    ) : PluginSource {
        override val sourceType: SourceType
            get() = SourceType.GIT
    }

    /** [uri] 为本地 .eva 文件路径或平台文件 URI，[sha256] 为可选的预期文件摘要。 */
    data class File(
        val uri: String,
        val sha256: String? = null,
    ) : PluginSource {
        override val sourceType: SourceType
            get() = SourceType.FILE
    }
}
