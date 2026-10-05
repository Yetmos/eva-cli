package com.eva_cli.core.widgets.provider

abstract class BaseProvider: Provider {
    override suspend fun backup() {
        // TODO: 备份当前 Provider 的 records
    }

    override suspend fun restore() {
        // TODO: 恢复当前 Provider 的 records
    }
}