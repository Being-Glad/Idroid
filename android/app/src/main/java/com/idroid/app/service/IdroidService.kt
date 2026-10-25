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
import com.idroid.app.R
import com.idroid.app.model.ClipboardPayload
import com.idroid.app.network.TcpClient
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.telephony.TelephonyManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.view.KeyEvent
import android.provider.ContactsContract
import android.database.ContentObserver

class IdroidService : Service() {

    private var tcpClient: TcpClient? = null
    private var macIpAddress: String = ""

    private var isUpdatingClipboardInternally = false
    private var lastSentImageHash: Int = 0
    private var lastSentText: String? = null

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

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: Any? = null

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

        setupTelephonyListener()
        setupContentObservers()
        setupClipboardListener()
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
                    requestMacWifiStatus()
                }
            },
            onError = { errorReason ->
                if (ip != "127.0.0.1") {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val probeSocket = java.net.Socket()
                            probeSocket.connect(java.net.InetSocketAddress("127.0.0.1", 53100), 300)
                            probeSocket.close()
                            android.util.Log.i("IdroidService", "Wi-Fi link to $ip unreachable, automatically falling back to USB 127.0.0.1")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                connectToMac("127.0.0.1", 53100)
                            }
                            return@launch
                        } catch (_: Exception) {}

                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            CompanionBridge.isConnecting.value = false
                            CompanionBridge.connectionError.value = errorReason
                        }
                    }
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        CompanionBridge.isConnecting.value = false
                        CompanionBridge.connectionError.value = errorReason
                    }
                }
            },
            context = this
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

            val prefs = getSharedPreferences("idroid_config", Context.MODE_PRIVATE)
            var deviceId = prefs.getString("device_uuid", null)
            if (deviceId.isNullOrBlank()) {
                val androidId = try {
                    android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                } catch (_: Exception) { null }
                deviceId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                    androidId
                } else {
                    java.util.UUID.randomUUID().toString()
                }
                prefs.edit().putString("device_uuid", deviceId).apply()
            }

            val pairingToken = prefs.getString("pairing_token", "") ?: ""

            val json = org.json.JSONObject().apply {
                put("type", "handshake")
                put("device_id", deviceId)
                put("pairing_token", pairingToken)
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
                "auth_rejected" -> {
                    val reason = obj.optString("reason", "Connection not authorized by Mac user")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        CompanionBridge.isConnecting.value = false
                        CompanionBridge.isConnected.value = false
                        CompanionBridge.connectionError.value = "Security Authorization Required: $reason"
                        Toast.makeText(this, "Mac Authorization Required: Scan QR code or authorize on Mac", Toast.LENGTH_LONG).show()
                    }
                    tcpClient?.disconnect()
                }
                "mac_status" -> {
                    val macBt = obj.optString("bluetooth_status", "")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        CompanionBridge.macName.value = obj.optString("name", "Om’s MacBook Pro")
                        CompanionBridge.macUserName.value = obj.optString("user_name", "Om Pandey")
                        CompanionBridge.connectionType.value = obj.optString("connection_type", "on the air")
                        CompanionBridge.macBattery.value = obj.optInt("battery", 100)
                        CompanionBridge.macIsCharging.value = obj.optBoolean("is_charging", true)
                        if (obj.has("mac_location_enabled")) {
                            CompanionBridge.macLocationEnabled.value = obj.optBoolean("mac_location_enabled", false)
                        }
                        if (macBt.isNotBlank()) {
                            CompanionBridge.macBluetoothStatus.value = macBt
                        }
                        val clip = obj.optString("clipboard", "")
                        if (clip.isNotBlank()) {
                            CompanionBridge.macClipboard.value = clip
                        }
                        val clipArr = obj.optJSONArray("clipboard_history") ?: obj.optJSONArray("history")
                        parseClipboardHistory(clipArr)
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
                "request_handshake" -> {
                    sendHandshake()
                    sendQuickAppsIcons()
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
                    val imgB64 = obj.optString("image_base64", "").takeIf { it.isNotBlank() }
                    val content = obj.optString("content", "")
                    if (imgB64 != null) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            CompanionBridge.macClipboard.value = "[Image]"
                            setAndroidClipboardImage(imgB64)
                        }
                    } else if (content.isNotBlank()) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            CompanionBridge.macClipboard.value = content
                            try {
                                isUpdatingClipboardInternally = true
                                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Idroid Mac", content)
                                cm?.setPrimaryClip(clip)
                            } catch (_: Exception) {} finally {
                                isUpdatingClipboardInternally = false
                            }
                        }
                    }
                    val clipArr = obj.optJSONArray("clipboard_history") ?: obj.optJSONArray("history")
                    parseClipboardHistory(clipArr)
                }
                "clipboard_history" -> {
                    val clipArr = obj.optJSONArray("clipboard_history") ?: obj.optJSONArray("history")
                    parseClipboardHistory(clipArr)
                }
                "reply_notification", "command" -> {
                    val action = obj.optString("action", obj.optString("type", ""))
                    if (action == "reply_notification" || obj.optString("type") == "reply_notification") {
                        val notifId = obj.optString("id", obj.optString("notificationId", ""))
                        val replyText = obj.optString("text", obj.optString("reply_text", ""))
                        if (notifId.isNotBlank() && replyText.isNotBlank()) {
                            val ok = IdroidNotificationService.replyToNotification(notifId, replyText)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                Toast.makeText(
                                    applicationContext,
                                    if (ok) "Replied: $replyText" else "Notification closed or not replyable",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
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
                "mac_bluetooth_devices" -> {
                    val arr = obj.optJSONArray("devices")
                    val list = mutableListOf<MacBtDevice>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val d = arr.optJSONObject(i) ?: continue
                            list.add(
                                MacBtDevice(
                                    name = d.optString("name", "Unknown Device"),
                                    address = d.optString("address", ""),
                                    connected = d.optBoolean("connected", false)
                                )
                            )
                        }
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        CompanionBridge.macBtDevices.clear()
                        CompanionBridge.macBtDevices.addAll(list)
                        CompanionBridge.isFetchingMacBt.value = false
                        CompanionBridge.connectingBtAddress.value = null
                    }
                }
                "mac_bt_result" -> {
                    val msg = obj.optString("message", "")
                    val devicesArr = obj.optJSONArray("devices")
                    val list = mutableListOf<MacBtDevice>()
                    if (devicesArr != null) {
                        for (i in 0 until devicesArr.length()) {
                            val d = devicesArr.optJSONObject(i) ?: continue
                            list.add(
                                MacBtDevice(
                                    name = d.optString("name", "Unknown Device"),
                                    address = d.optString("address", ""),
                                    connected = d.optBoolean("connected", false)
                                )
                            )
                        }
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (list.isNotEmpty()) {
                            CompanionBridge.macBtDevices.clear()
                            CompanionBridge.macBtDevices.addAll(list)
                        }
                        CompanionBridge.connectingBtAddress.value = null
                        if (msg.isNotBlank()) {
                            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                "mac_wifi_status" -> {
                    val power = obj.optBoolean("power", true)
                    val ssid = obj.optString("ssid", "")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        CompanionBridge.macWifiPower.value = power
                        CompanionBridge.macWifiSsid.value = ssid
                        CompanionBridge.isFetchingMacWifi.value = false
                    }
                }
                "set_call_audio_route" -> {
                    val route = obj.optString("route", "phone")
                    applyCallAudioRoute(route)
                }
                "get_phone_contacts" -> {
                    sendPhoneContacts()
                }
                "get_call_history" -> {
                    sendPhoneCallHistory()
                }
                "delete_call_record" -> {
                    val recordId = obj.optString("id", "")
                    val number = obj.optString("number", "")
                    deleteCallLogEntry(recordId, number)
                }
                "clear_call_history" -> {
                    clearCallLogEntries()
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

    fun requestMacBluetoothDevices() {
        CompanionBridge.isFetchingMacBt.value = true
        sendCommand("get_mac_bluetooth_devices")
    }

    fun connectMacBluetoothDevice(address: String) {
        CompanionBridge.connectingBtAddress.value = address
        sendCommand("connect_mac_bt", "address", address)
    }

    fun disconnectMacBluetoothDevice(address: String) {
        CompanionBridge.connectingBtAddress.value = address
        sendCommand("disconnect_mac_bt", "address", address)
    }

    fun requestMacWifiStatus() {
        CompanionBridge.isFetchingMacWifi.value = true
        sendCommand("get_mac_wifi_status")
    }

    fun setMacWifiPower(power: Boolean) {
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", "set_mac_wifi_power")
            put("power", power)
        }
        sendRaw(obj.toString())
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

    private fun parseClipboardHistory(arr: org.json.JSONArray?) {
        if (arr == null) return
        val list = mutableListOf<ClipboardItem>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            list.add(
                ClipboardItem(
                    id = item.optString("id", java.util.UUID.randomUUID().toString()),
                    text = item.optString("text", ""),
                    source = item.optString("source", "Mac"),
                    time = item.optString("time", "Just now"),
                    pinned = item.optBoolean("pinned", false),
                    imageBase64 = item.optString("image_base64", "").takeIf { it.isNotBlank() }
                )
            )
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            CompanionBridge.clipboardHistory.clear()
            CompanionBridge.clipboardHistory.addAll(list)
            if (list.isNotEmpty() && CompanionBridge.macClipboard.value.isBlank()) {
                val first = list.first()
                CompanionBridge.macClipboard.value = if (!first.imageBase64.isNullOrBlank()) "[Image]" else first.text
            }
        }
    }

    fun sendClipboard(text: String) {
        val obj = org.json.JSONObject().apply {
            put("type", "clipboard_push")
            put("text", text)
        }
        sendRaw(obj.toString())
    }

    fun sendImageClipboard(b64: String) {
        val obj = org.json.JSONObject().apply {
            put("type", "clipboard_push")
            put("image_base64", b64)
        }
        sendRaw(obj.toString())
    }

    fun setAndroidClipboardImage(b64: String) {
        try {
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            val imageFile = java.io.File(cacheDir, "shared_clipboard_image.png")
            imageFile.writeBytes(bytes)
            imageFile.setReadable(true, false)
            lastSentImageHash = bytes.contentHashCode()

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "com.idroid.app.fileprovider",
                imageFile
            )
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val description = android.content.ClipDescription("Idroid Image", arrayOf("image/png", "image/jpeg", "image/*"))
            val item = android.content.ClipData.Item(uri)
            val clip = android.content.ClipData(description, item)

            // Grant URI read permission for keyboards and messaging apps
            val targetPackages = listOf(
                "com.google.android.inputmethod.latin",
                "com.samsung.android.honeyboard",
                "com.whatsapp",
                "com.google.android.apps.messaging",
                "com.samsung.android.messaging"
            )
            for (pkg in targetPackages) {
                try {
                    grantUriPermission(pkg, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }

            isUpdatingClipboardInternally = true
            cm?.setPrimaryClip(clip)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isUpdatingClipboardInternally = false
        }
    }

    private fun setupClipboardListener() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
            cm.addPrimaryClipChangedListener {
                if (isUpdatingClipboardInternally) return@addPrimaryClipChangedListener
                try {
                    val clip = cm.primaryClip ?: return@addPrimaryClipChangedListener
                    if (clip.itemCount == 0) return@addPrimaryClipChangedListener
                    val item = clip.getItemAt(0)
                    
                    // 1. Check for image URI (copied image or screenshot)
                    val uri = item.uri
                    if (uri != null) {
                        val mimeType = contentResolver.getType(uri) ?: clip.description?.let {
                            if (it.hasMimeType("image/*")) "image/png" else null
                        }
                        if (mimeType != null && mimeType.startsWith("image/")) {
                            contentResolver.openInputStream(uri)?.use { stream ->
                                val bytes = stream.readBytes()
                                if (bytes.isNotEmpty()) {
                                    val hash = bytes.contentHashCode()
                                    if (hash != lastSentImageHash) {
                                        lastSentImageHash = hash
                                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                        sendImageClipboard(b64)
                                        return@addPrimaryClipChangedListener
                                    }
                                }
                            }
                        }
                    }
                    
                    // 2. Fall back to text
                    val text = item.text?.toString() ?: item.coerceToText(this)?.toString()
                    if (!text.isNullOrBlank() && text != lastSentText) {
                        lastSentText = text
                        sendClipboard(text)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePinClipboard(id: String) {
        // Optimistic local state update for 0ms visual feedback
        val index = CompanionBridge.clipboardHistory.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = CompanionBridge.clipboardHistory[index]
            val updated = item.copy(pinned = !item.pinned)
            CompanionBridge.clipboardHistory.removeAt(index)
            if (updated.pinned) {
                CompanionBridge.clipboardHistory.add(0, updated)
            } else {
                val firstUnpinned = CompanionBridge.clipboardHistory.indexOfFirst { !it.pinned }
                if (firstUnpinned != -1) {
                    CompanionBridge.clipboardHistory.add(firstUnpinned, updated)
                } else {
                    CompanionBridge.clipboardHistory.add(updated)
                }
            }
        }
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", "toggle_pin_clipboard")
            put("id", id)
        }
        sendRaw(obj.toString())
    }

    fun deleteClipboardItem(id: String) {
        CompanionBridge.clipboardHistory.removeAll { it.id == id }
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", "delete_clipboard_entry")
            put("id", id)
        }
        sendRaw(obj.toString())
    }

    fun clearClipboardHistory() {
        CompanionBridge.clipboardHistory.removeAll { !it.pinned }
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", "clear_clipboard_history")
        }
        sendRaw(obj.toString())
    }

    fun requestClipboardHistory() {
        val obj = org.json.JSONObject().apply {
            put("type", "command")
            put("action", "get_clipboard_history")
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
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF007AFF.toInt())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotification(status: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(status))
    }

    private fun setupTelephonyListener() {
        try {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallStateTransition(state, null)
                    }
                }
                telephonyCallback = callback
                telephonyManager?.registerTelephonyCallback(androidx.core.content.ContextCompat.getMainExecutor(this), callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallStateTransition(state, phoneNumber)
                    }
                }
                phoneStateListener = listener
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun teardownTelephonyListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let {
                    telephonyManager?.unregisterTelephonyCallback(it)
                }
            } else {
                phoneStateListener?.let {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var wasMediaPlayingBeforeCall = false

    private fun pausePhoneMediaForCall() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (audioManager.isMusicActive) {
                wasMediaPlayingBeforeCall = true
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
                audioManager.dispatchMediaKeyEvent(downEvent)
                audioManager.dispatchMediaKeyEvent(upEvent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resumePhoneMediaAfterCall() {
        if (!wasMediaPlayingBeforeCall) return
        wasMediaPlayingBeforeCall = false
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyCallAudioRoute(route: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val availableDevices = audioManager.availableCommunicationDevices
                when (route) {
                    "bluetooth" -> {
                        val btDevice = availableDevices.find {
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        }
                        if (btDevice != null) {
                            audioManager.setCommunicationDevice(btDevice)
                        } else {
                            val speaker = availableDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                            if (speaker != null) audioManager.setCommunicationDevice(speaker)
                        }
                    }
                    "phone" -> {
                        val earpiece = availableDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                        if (earpiece != null) {
                            audioManager.setCommunicationDevice(earpiece)
                        } else {
                            audioManager.clearCommunicationDevice()
                        }
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = false
                    }
                    else -> {
                        val speaker = availableDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        if (speaker != null) {
                            audioManager.setCommunicationDevice(speaker)
                        }
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    }
                }
            } else {
                when (route) {
                    "phone" -> {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = false
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun handleCallStateTransition(state: Int, phoneNumber: String?) {
        val stateStr = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> "ringing"
            TelephonyManager.CALL_STATE_OFFHOOK -> "active"
            TelephonyManager.CALL_STATE_IDLE -> "idle"
            else -> "idle"
        }

        if (stateStr == "ringing" || stateStr == "active") {
            pausePhoneMediaForCall()
            if (stateStr == "active") {
                applyCallAudioRoute("phone_speaker")
            }
        } else if (stateStr == "idle") {
            resumePhoneMediaAfterCall()
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager?.clearCommunicationDevice()
            }
            audioManager?.mode = AudioManager.MODE_NORMAL
        }

        val obj = org.json.JSONObject().apply {
            put("type", "call_state_changed")
            put("state", stateStr)
            put("number", phoneNumber ?: "")
            put("timestamp", System.currentTimeMillis())
        }
        sendRaw(obj.toString())

        // When call terminates or transitions to idle, immediately push updated call history
        if (stateStr == "idle") {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendPhoneCallHistory()
            }, 800)
        }
    }

    private var contactsObserver: ContentObserver? = null

    private fun setupContentObservers() {
        try {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            if (contactsObserver == null) {
                contactsObserver = object : ContentObserver(handler) {
                    private var lastSync = 0L
                    override fun onChange(selfChange: Boolean) {
                        super.onChange(selfChange)
                        val now = System.currentTimeMillis()
                        if (now - lastSync > 2000) {
                            lastSync = now
                            sendPhoneContacts()
                        }
                    }
                }
                contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    true,
                    contactsObserver!!
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun teardownContentObservers() {
        try {
            contactsObserver?.let { contentResolver.unregisterContentObserver(it) }
            contactsObserver = null
        } catch (_: Exception) {}
    }

    fun refreshListeners() {
        setupTelephonyListener()
        setupContentObservers()
    }


    fun sendPhoneContacts() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val list = org.json.JSONArray()
                val cr = contentResolver
                val cursor = cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE
                    ),
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )
                cursor?.use { c ->
                    val idCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val typeCol = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val seenNumbers = HashSet<String>()
                    while (c.moveToNext()) {
                        val id = if (idCol >= 0) c.getString(idCol) else ""
                        val name = if (nameCol >= 0) c.getString(nameCol) ?: "Unknown" else "Unknown"
                        val num = if (numCol >= 0) c.getString(numCol) ?: "" else ""
                        val clean = num.replace(Regex("[^0-9+]"), "")
                        if (clean.isBlank() || seenNumbers.contains(clean)) continue
                        seenNumbers.add(clean)
                        val typeNum = if (typeCol >= 0) c.getInt(typeCol) else 2
                        val typeStr = when (typeNum) {
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                            else -> "Main"
                        }
                        val item = org.json.JSONObject().apply {
                            put("id", id)
                            put("name", name)
                            put("number", num)
                            put("type", typeStr)
                        }
                        list.put(item)
                    }
                }
                val resp = org.json.JSONObject().apply {
                    put("type", "phone_contacts")
                    put("contacts", list)
                }
                sendRaw(resp.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendPhoneCallHistory() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val resp = org.json.JSONObject().apply {
                    put("type", "phone_call_history")
                    put("calls", org.json.JSONArray())
                }
                sendRaw(resp.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteCallLogEntry(recordId: String, number: String) {
        // Disabled for privacy & Play Protect compliance
    }

    fun clearCallLogEntries() {
        // Disabled for privacy & Play Protect compliance
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
        teardownContentObservers()
        teardownTelephonyListener()
        tcpClient?.disconnect()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
