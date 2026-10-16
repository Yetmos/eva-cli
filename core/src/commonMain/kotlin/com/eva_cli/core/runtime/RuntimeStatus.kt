package com.eva_cli.core.runtime

/** 宿主运行时的生命周期状态。 */
enum class RuntimeStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
}
