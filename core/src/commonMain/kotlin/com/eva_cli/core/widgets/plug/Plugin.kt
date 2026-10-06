package com.eva_cli.core.widgets.plug


data class Plugin(val name: String, val source: PluginSource)

data class PluginSource(val sourceType: SourceType) {
    enum class SourceType{
        URL,
        GIT,
        CODE
    }
}
