package com.telestudy.tv.core.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.compositionLocalOf

enum class DeviceType {
    PHONE,
    TABLET,
    TV
}

val LocalDeviceType = compositionLocalOf { DeviceType.PHONE }

object DeviceTypeProvider {
    fun resolveDeviceType(context: Context): DeviceType {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTelevisionUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        val packageManager = context.packageManager
        val hasLeanbackFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasTvFeature = packageManager.hasSystemFeature("android.hardware.type.television")

        if (isTelevisionUiMode || hasLeanbackFeature || hasTvFeature) {
            return DeviceType.TV
        }

        val configuration = context.resources.configuration
        return if (configuration.smallestScreenWidthDp >= 600) {
            DeviceType.TABLET
        } else {
            DeviceType.PHONE
        }
    }
}
