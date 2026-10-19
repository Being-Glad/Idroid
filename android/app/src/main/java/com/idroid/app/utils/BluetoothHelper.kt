package com.idroid.app.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

data class DynamicBluetoothStatus(
    val isEnabled: Boolean,
    val isConnected: Boolean,
    val connectedDeviceName: String?,
    val statusText: String
)

object BluetoothHelper {

    fun getStatus(context: Context): DynamicBluetoothStatus {
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val btAdapter = btManager?.adapter

            val isEnabled = btAdapter?.isEnabled == true
            if (!isEnabled) {
                return DynamicBluetoothStatus(
                    isEnabled = false,
                    isConnected = false,
                    connectedDeviceName = null,
                    statusText = "Off"
                )
            }

            // 1. Detect connected Bluetooth Audio Peripherals (Buds, Headphones, Car Audio, Speakers)
            // Works reliably on Android 6.0+ via AudioManager without requiring runtime permission prompts
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (dev in devices) {
                    val t = dev.type
                    val isBtAudio = t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            (Build.VERSION.SDK_INT >= 31 && (t == 26 || t == 27)) // TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER
                    if (isBtAudio) {
                        val name = dev.productName?.toString()?.trim()
                        if (!name.isNullOrBlank()) {
                            return DynamicBluetoothStatus(
                                isEnabled = true,
                                isConnected = true,
                                connectedDeviceName = name,
                                statusText = "Connected to $name"
                            )
                        }
                    }
                }
            }

            // 2. Query GATT / Profile connected devices (Watches, Trackers, Keyboards)
            if (btManager != null) {
                try {
                    val gattDevices = btManager.getConnectedDevices(BluetoothProfile.GATT)
                    if (gattDevices.isNotEmpty()) {
                        val name = try { gattDevices[0].name } catch (_: SecurityException) { null }
                        if (!name.isNullOrBlank()) {
                            return DynamicBluetoothStatus(
                                isEnabled = true,
                                isConnected = true,
                                connectedDeviceName = name,
                                statusText = "Connected to $name"
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            // Bluetooth is ON, but no peripheral is currently paired/connected
            return DynamicBluetoothStatus(
                isEnabled = true,
                isConnected = false,
                connectedDeviceName = null,
                statusText = "Bluetooth On"
            )

        } catch (e: Exception) {
            return DynamicBluetoothStatus(
                isEnabled = false,
                isConnected = false,
                connectedDeviceName = null,
                statusText = "Off"
            )
        }
    }
}
