package com.eva_cli.core.status

import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice

private const val DEVICE_ID_KEY = "com.eva-cli.status.device-id"

internal actual fun currentPlatform(): Platform = Platform.IOS

internal actual fun currentDeviceId(): String {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.stringForKey(DEVICE_ID_KEY)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { return it }

    val deviceId = UIDevice.currentDevice.identifierForVendor
        ?.UUIDString()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: NSUUID().UUIDString()
    return deviceId.also {
        defaults.setObject(it, forKey = DEVICE_ID_KEY)
    }
}
