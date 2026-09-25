package com.eva_cli.core.utils.info

import com.eva_cli.core.status.EvaBuildInfo

enum class Platform {
    MACOS,
    WINDOWS,
    LINUX,
    Android,
    IOS
}

object Info {
    /** 构建时从 Gradle 项目版本写入的 Eva SDK 版本。 */
    val sdk: String = EvaBuildInfo.VERSION

    /** 为使用完整版本字段名的调用方保留的兼容属性。 */
    val sdkVersion: String
        get() = sdk

    /** SDK 当前运行的平台。 */
    val platform: Platform = currentPlatform()

    /**
     * 当前平台提供的稳定设备标识。
     *
     * 标识范围因平台而异：桌面端对应本地 Eva 安装，Android 对应应用签名与用户，
     * iOS 对应同一开发者。重装操作系统或应用后，该标识可能发生变化。
     * Android 调用方必须先调用平台专用的 `Info.initialize(context)` 扩展，
     * 再读取此属性。
     */
    val deviceId: String by lazy { currentDeviceId() }
}

internal expect fun currentPlatform(): Platform

internal expect fun currentDeviceId(): String
