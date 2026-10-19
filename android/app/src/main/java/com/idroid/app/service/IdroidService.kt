package com.idroid.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.idroid.app.model.ClipboardPayload
import com.idroid.app.network.TcpClient
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class IdroidService : Service() {

    private var tcpClient: TcpClient? = null
    private var macIpAddress: String = ""

    private val packageChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == Intent.ACTION_PACKAGE_ADDED ||
                action == Intent.ACTION_PACKAGE_REMOVED ||
                action == Intent.ACTION_PACKAGE_REPLACED) {
                sendQuickAppsIcons()
            }
        }
    }

    private val bluetoothReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (tcpClient?.isConnected() == true) {
                sendHandshake()
            }
        }
    }

    companion object {
        var instance: IdroidService? = null
            private set
        private const val CHANNEL_ID = "idroid_persistent_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Waiting for Mac..."))

        try {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            registerReceiver(packageChangeReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val btFilter = android.content.IntentFilter().apply {
                addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")
                addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
                addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            }
            registerReceiver(bluetoothReceiver, btFilter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetIp = intent?.getStringExtra("target_ip")
        val targetPort = intent?.getIntExtra("target_port", 53100) ?: 53100
        if (!targetIp.isNullOrBlank()) {
            if (tcpClient?.isConnected() == true && macIpAddress == targetIp) {
                // Already connected to this target, do not recreate socket or flood
                return START_STICKY
            }
            macIpAddress = targetIp
            CompanionBridge.lastTargetIp.value = targetIp
            CompanionBridge.lastTargetPort.value = targetPort
            connectToMac(targetIp, targetPort)
        }
        return START_STICKY
    }

    private fun connectToMac(ip: String, port: Int = 53100) {
        tcpClient?.disconnect()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            CompanionBridge.isConnecting.value = true
            CompanionBridge.connectionError.value = null
        }
        tcpClient = TcpClient(
            host = ip,
            port = port,
            onMessageReceived = { message ->
                handleIncomingMessage(message)
            },
            onConnectionStateChanged = { isConnected ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    CompanionBridge.isConnected.value = isConnected
                    CompanionBridge.isConnecting.value = false
                    if (isConnected) {
                        CompanionBridge.connectionError.value = null
                    } else {
                        CompanionBridge.macApps.clear()
                    }
                }
                val statusText = if (isConnected) "Connected to Mac" else "Searching for Mac..."
                updateNotification(statusText)

                if (isConnected) {
                    sendHandshake()
                    sendQuickAppsIcons()
                }
            },
            onError = { errorReason ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    CompanionBridge.isConnecting.value = false
                    CompanionBridge.connectionError.value = errorReason
                }
            }
        )
        tcpClient?.connect()
    }

    fun sendQuickAppsIcons() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val pm = packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

                // Query all launcher applications actually installed on this device
                val launchables = pm.queryIntentActivities(mainIntent, 0)
                if (launchables.isEmpty()) return@launch

                // Separate third-party user apps from system apps
                val userApps = mutableListOf<android.content.pm.ResolveInfo>()
                val systemApps = mutableListOf<android.content.pm.ResolveInfo>()

                for (info in launchables) {
                    val pkg = info.activityInfo.packageName
                    if (pkg == packageName) continue // Skip Idroid itself

                    val flags = info.activityInfo.applicationInfo?.flags ?: 0
                    val isSystem = (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            (flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                    if (isSystem) {
                        systemApps.add(info)
                    } else {
                        userApps.add(info)
                    }
                }

                // Sort apps alphabetically by their localized display name
                userApps.sortBy { it.loadLabel(pm).toString().lowercase() }
                systemApps.sortBy { it.loadLabel(pm).toString().lowercase() }

                // Dynamic priority: user installed apps first, then essential system apps (up to 60)
                val orderedApps = (userApps + systemApps).take(60)

                val appsArray = org.json.JSONArray()
                for (info in orderedApps) {
                    val pkg = info.activityInfo.packageName
                    val label = info.loadLabel(pm).toString()
                    val drawable = info.loadIcon(pm) ?: continue

                    val bmp = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                        android.graphics.Bitmap.createScaledBitmap(drawable.bitmap, 64, 64, true)
                    } else {
                        val b = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(b)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        b
                    }
                    val baos = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, baos)
                    val iconBase64 = "data:image/png;base64," + android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)

                    if (iconBase64.isNotBlank()) {
                        appsArray.put(org.json.JSONObject().apply {
                            put("name", label)
                            put("package", pkg)
                            put("icon", iconBase64)
                        })
                    }
                }

                val msg = org.json.JSONObject().apply {
                    put("type", "quick_apps_icons")
                    put("apps", appsArray)
                }.toString()

                sendRaw(msg)
            } catch (e: Exception) {
                android.util.Log.e("IdroidService", "Dynamic sendQuickAppsIcons error: ${e.message}")
            }
        }
    }

    fun sendHandshake() {
        try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val batteryLevel = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 88
            val status = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS) ?: -1
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL

            val deviceName = try {
                android.provider.Settings.Global.getString(contentResolver, "device_name")
            } catch (_: Exception) { null } ?: android.os.Build.MODEL ?: "Om's S23 Ultra"

            val deviceIp = getWifiIpAddress(this)
            val btStatus = com.idroid.app.utils.BluetoothHelper.getStatus(this)

            val json = org.json.JSONObject().apply {
                put("type", "handshake")
                put("device_name", deviceName)
                put("brand", android.os.Build.BRAND ?: "Samsung")
                put("device_ip", deviceIp)
                put("battery", batteryLevel)
                put("is_charging", isCharging)
                put("storage_used_gb", "98G")
                put("storage_total_gb", "256G")
                put("storage_percent", 38)
                put("bluetooth_status", btStatus.statusText)
                put("bluetooth_enabled", btStatus.isEnabled)
                put("bluetooth_connected", btStatus.isConnected)
                put("bluetooth_device_name", btStatus.connectedDeviceName ?: "")
            }.toString()
            sendRaw(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getWifiIpAddress(context: Context): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    private fun handleIncomingMessage(rawJson: String) {
        try {
            val obj = org.json.JSONObject(rawJson)
            when (obj.optString("type")) {
                "mac_status" -> {
                    val macBt = obj.optString("bluetooth_status", "")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        CompanionBridge.macName.value = obj.optString("name", "Om’s MacBook Pro")
                        CompanionBridge.macUserName.value = obj.optString("user_name", "Om Pandey")
                        CompanionBridge.connectionType.value = obj.optString("connection_type", "on the air")
                        CompanionBridge.macBattery.value = obj.optInt("battery", 100)
                        CompanionBridge.macIsCharging.value = obj.optBoolean("is_charging", true)
                        if (macBt.isNotBlank()) {
                            CompanionBridge.macBluetoothStatus.value = macBt
                        }
                        val clip = obj.optString("clipboard", "")
                        if (clip.isNotBlank()) {
                            CompanionBridge.macClipboard.value = clip
                        }
                        if (obj.has("volume")) {
                            CompanionBridge.macVolume.value = obj.optInt("volume", 50) / 100f
                        }
                        if (obj.has("brightness")) {
                            CompanionBridge.macBrightness.value = obj.optDouble("brightness", 0.85).toFloat()
                        }
                        val appsArr = obj.optJSONArray("apps")
                        if (appsArr != null) {
                            CompanionBridge.macApps.clear()
                            for (i in 0 until appsArr.length()) {
                                val a = appsArr.getJSONObject(i)
                                CompanionBridge.macApps.add(
                                    com.idroid.app.model.MacAppItem(
                                        name = a.optString("name", "App"),
                                        bundle_id = a.optString("bundle_id", ""),
                                        path = a.optString("path", ""),
                                        category = a.optString("category", ""),
                                        icon = a.optString("icon", "")
                                    )
                                )
                            }
                        }
                    }
                }
                "apps_list" -> {
                    val appsArr = obj.optJSONArray("apps")
                    if (appsArr != null) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            CompanionBridge.macApps.clear()
                            for (i in 0 until appsArr.length()) {
                                val a = appsArr.getJSONObject(i)
                                CompanionBridge.macApps.add(
                                    com.idroid.app.model.MacAppItem(
                                        name = a.optString("name", "App"),
                                        bundle_id = a.optString("bundle_id", ""),
                                        path = a.optString("path", ""),
                                        category = a.optString("category", ""),
                                        icon = a.optString("icon", "")
                                    )
                                )
                            }
                        }
                    }
                }
                "pong" -> {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        CompanionBridge.macBattery.value = obj.optInt("battery", CompanionBridge.macBattery.value)
                        CompanionBridge.macIsCharging.value = obj.optBoolean("is_charging", CompanionBridge.macIsCharging.value)
                        val ct = obj.optString("connection_type", "")
                        if (ct.isNotBlank()) {
                            CompanionBridge.connectionType.value = ct
                        }
                        if (obj.has("volume")) {
                            CompanionBridge.macVolume.value = obj.optInt("volume", (CompanionBridge.macVolume.value * 100).toInt()) / 100f
                        }
                        if (obj.has("brightness")) {
                            CompanionBridge.macBrightness.value = obj.optDouble("brightness", CompanionBridge.macBrightness.value.toDouble()).toFloat()
                        }
                    }
                }
                "clipboard_update" -> {
                    val content = obj.optString("content", "")
                    if (content.isNotBlank()) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            CompanionBridge.macClipboard.value = content
                        }
                    }
                }
                "toast" -> {
                    val message = obj.optString("message", "")
                    if (message.isNotBlank()) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setVolume(percent: Int) {
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", "set_volume")
            put("volume", percent.coerceIn(0, 100))
        }
        sendRaw(obj.toString())
    }

    fun setBrightness(brightness: Float) {
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", "set_brightness")
            put("brightness", brightness.coerceIn(0f, 1f).toDouble())
        }
        sendRaw(obj.toString())
    }

    fun sendCommand(action: String, extraKey: String? = null, extraValue: String? = null) {
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", action)
            if (extraKey != null && extraValue != null) {
                put(extraKey, extraValue)
            }
        }
        sendRaw(obj.toString())
    }

    fun sendMediaKey(key: String) {
        sendCommand("media_key", "key", key)
    }

    fun sendMouseMove(dx: Float, dy: Float) {
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", "mouse_move")
            put("dx", dx)
            put("dy", dy)
        }
        sendRaw(obj.toString())
    }

    fun sendMouseClick(isRightClick: Boolean = false) {
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", if (isRightClick) "mouse_right_click" else "mouse_click")
        }
        sendRaw(obj.toString())
    }

    fun sendMouseScroll(dy: Int) {
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", "mouse_scroll")
            put("dy", dy)
        }
        sendRaw(obj.toString())
    }

    fun sendClipboard(text: String) {
        val obj = org.json.JSONObject().apply {
            put("type", "clipboard_push")
            put("text", text)
        }
        sendRaw(obj.toString())
    }

    fun sendTypeText(text: String) {
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", "type_text")
            put("text", text)
        }
        sendRaw(obj.toString())
    }

    fun sendKeyPress(key: String) {
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", "key_press")
            put("key", key)
        }
        sendRaw(obj.toString())
    }


    fun sendRaw(json: String) {
        tcpClient?.send(json)
    }

    fun disconnect() {
        try {
            val obj = org.json.JSONObject().apply {
                put("type", "disconnect")
            }
            sendRaw(obj.toString())
        } catch (_: Exception) {}
        tcpClient?.disconnect()
        tcpClient = null
        CompanionBridge.isConnected.value = false
        updateNotification("Disconnected")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Idroid Active Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps connection alive to your Mac"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(status: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Idroid Companion")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotification(status: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(status))
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
        tcpClient?.disconnect()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
