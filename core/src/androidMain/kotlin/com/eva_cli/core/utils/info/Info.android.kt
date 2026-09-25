package com.eva_cli.core.utils.info

import android.content.Context
import android.provider.Settings
import java.util.UUID

private const val PREFERENCES_NAME = "eva_status"
private const val DEVICE_ID_KEY = "fallback_device_id"

@Volatile
private var applicationContext: Context? = null

/** 提供读取 Android 安全设备标识所需的应用上下文。 */
fun Info.initialize(context: Context) {
    applicationContext = context.applicationContext ?: context
}

internal actual fun currentPlatform(): Platform = Platform.Android

internal actual fun currentDeviceId(): String {
    val context = checkNotNull(applicationContext) {
        "Info.initialize(context) must be called before reading Info.deviceId on Android"
    }
    val androidId = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()

    return androidId?.trim()?.takeIf(String::isNotEmpty)
        ?: getOrCreateFallbackDeviceId(context)
}

private fun getOrCreateFallbackDeviceId(context: Context): String = synchronized(Info) {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    preferences.getString(DEVICE_ID_KEY, null)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: UUID.randomUUID().toString().also { generatedId ->
            preferences.edit().putString(DEVICE_ID_KEY, generatedId).commit()
        }
}
