package com.idroid.app.service

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.idroid.app.model.MacAppItem

object CompanionBridge {
    val isConnected = mutableStateOf(false)
    val macName = mutableStateOf("MacBook Pro")
    val macBattery = mutableStateOf(100)
    val macIsCharging = mutableStateOf(true)
    val macClipboard = mutableStateOf("Ready to sync with Mac")
    val macApps = mutableStateListOf<MacAppItem>()
    val connectionType = mutableStateOf("on the air")
    val macUserName = mutableStateOf("Om Pandey")
    val macBluetoothStatus = mutableStateOf("Bluetooth On")
    val lastTargetIp = mutableStateOf("192.168.1.10")
    val lastTargetPort = mutableStateOf(53100)
    val isConnecting = mutableStateOf(false)
    val connectionError = mutableStateOf<String?>(null)
    val macVolume = mutableStateOf(0.5f)
    val macBrightness = mutableStateOf(0.85f)
}
