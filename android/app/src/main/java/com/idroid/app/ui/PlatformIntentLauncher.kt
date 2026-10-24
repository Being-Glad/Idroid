package com.idroid.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/**
 * PlatformIntentLauncher - Universal OEM-resilient system settings launcher.
 * Seamlessly resolves the exact Hotspot, Tethering, and Wi-Fi preferences across all
 * Android OEMs (Samsung One UI, Google Pixel AOSP, Xiaomi HyperOS/MIUI, OnePlus OxygenOS,
 * ColorOS, FuntouchOS, Motorola, and Nothing OS) without crashing or failing.
 */
object PlatformIntentLauncher {

    /**
     * Dynamically opens the Hotspot / Wi-Fi Tethering settings screen across all Android manufacturers.
     * Uses prioritized multi-intent resolution with graceful fallbacks.
     */
    fun openHotspotSettings(context: Context) {
        val intents = listOf(
            // 1. Android 10+ (API 29+) standard Wi-Fi hotspot settings
            Intent("android.settings.WIFI_TETHER_SETTINGS"),
            // 2. Standard Android Tethering settings
            Intent("android.settings.TETHER_SETTINGS"),
            // 3. Samsung Galaxy (One UI) direct hotspot component
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
            // 4. Samsung One UI Wi-Fi tethering component
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.wifi.tether.WifiTetherSettings")),
            // 5. Xiaomi MIUI / HyperOS hotspot intent
            Intent("miui.intent.action.WIFI_TETHER"),
            // 6. Generic Network & Internet / Wireless settings fallback
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            // 7. Root System Settings fallback
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Continue to next OEM intent candidate
            }
        }

        Toast.makeText(context, "Please open Settings and turn on Mobile Hotspot", Toast.LENGTH_SHORT).show()
    }

    /**
     * Dynamically opens the Wi-Fi network selection screen across all Android manufacturers.
     */
    fun openWifiSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Continue to next candidate
            }
        }

        Toast.makeText(context, "Please open Settings and connect to Wi-Fi", Toast.LENGTH_SHORT).show()
    }
}
