package com.idroid.app.utils

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import com.idroid.app.service.IdroidService

/**
 * LocationHelper - One-click instant Location (GPS) toggle on Android.
 * Performs direct in-background toggle without ever launching the Android Settings page.
 * Uses WRITE_SECURE_SETTINGS / system API, with Mac companion fallback if connected.
 */
object LocationHelper {

    /**
     * Checks if master Location (GPS) is currently active.
     */
    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            try {
                val mode = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF
                )
                mode != Settings.Secure.LOCATION_MODE_OFF
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Toggles Location state with one click.
     * Returns true if changed successfully, or false if permission is needed.
     */
    fun toggleLocation(context: Context): Boolean {
        val target = !isLocationEnabled(context)
        return setLocationEnabled(context, target)
    }

    /**
     * Sets Location state directly in background with zero UI redirect.
     */
    fun setLocationEnabled(context: Context, enabled: Boolean): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        // Method 1: LocationManager.setLocationEnabledForUser(boolean, UserHandle) via reflection
        if (lm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val method = LocationManager::class.java.getMethod(
                    "setLocationEnabledForUser",
                    Boolean::class.javaPrimitiveType,
                    UserHandle::class.java
                )
                method.invoke(lm, enabled, Process.myUserHandle())
                return true
            } catch (_: Throwable) {}
        }

        // Method 2: LocationManager.setLocationEnabledForUser(boolean, int) via reflection
        if (lm != null) {
            try {
                val method = LocationManager::class.java.getMethod(
                    "setLocationEnabledForUser",
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                method.invoke(lm, enabled, 0)
                return true
            } catch (_: Throwable) {}
        }

        // Method 3: Direct Settings.Secure write (requires WRITE_SECURE_SETTINGS)
        try {
            val mode = if (enabled) 3 else 0 // 3 = LOCATION_MODE_HIGH_ACCURACY, 0 = LOCATION_MODE_OFF
            val success = Settings.Secure.putInt(context.contentResolver, Settings.Secure.LOCATION_MODE, mode)
            if (success) return true
        } catch (_: Throwable) {}

        // Method 4: Settings.Secure.setLocationProviderEnabled for providers
        try {
            Settings.Secure.setLocationProviderEnabled(context.contentResolver, LocationManager.GPS_PROVIDER, enabled)
            Settings.Secure.setLocationProviderEnabled(context.contentResolver, LocationManager.NETWORK_PROVIDER, enabled)
            return true
        } catch (_: Throwable) {}

        // Method 5: Companion link fallback: if connected to Mac, Mac can trigger over adb daemon
        try {
            IdroidService.instance?.sendCommand("toggle_phone_location")
        } catch (_: Throwable) {}

        return false
    }
}
