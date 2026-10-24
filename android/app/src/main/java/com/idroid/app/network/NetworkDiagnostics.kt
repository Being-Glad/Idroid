package com.idroid.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.idroid.app.service.CompanionBridge
import java.net.Inet4Address
import java.net.NetworkInterface

enum class DiagnosticType {
    CONNECTED,
    CELLULAR_DATA_ONLY,
    DIFFERENT_WIFI_NETWORK,
    ROUTER_AP_ISOLATION,
    MAC_APP_NOT_RUNNING,
    USB_DISCONNECTED,
    SECURITY_AUTH_REQUIRED,
    GENERIC_ERROR
}

enum class DiagnosticAction {
    OPEN_WIFI_SETTINGS,
    OPEN_HOTSPOT_SETTINGS,
    CONNECT_USB,
    OPEN_DOCTOR,
    NONE
}

data class NetworkDiagnosticReport(
    val type: DiagnosticType,
    val headline: String,
    val explanation: String,
    val actionPrompt: String,
    val phoneIp: String,
    val phoneIsWifi: Boolean,
    val phoneIsCellular: Boolean,
    val targetIp: String,
    val targetSsid: String?,
    val isSubnetMatch: Boolean,
    val primaryAction: DiagnosticAction
)

object NetworkDiagnostics {

    fun extractSubnet(ip: String): String {
        return if (ip.contains(".") && ip.count { it == '.' } == 3) {
            ip.substringBeforeLast(".")
        } else {
            ""
        }
    }

    fun isSameSubnet(ip1: String, ip2: String): Boolean {
        val s1 = extractSubnet(ip1)
        val s2 = extractSubnet(ip2)
        return s1.isNotBlank() && s2.isNotBlank() && s1 == s2
    }

    fun getLocalPhoneIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotBlank() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    fun getWifiLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val name = iface.name.lowercase()
                // wlan, ap, eth, or en interfaces (Android & JVM tests)
                if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("en") || name.startsWith("eth")) {
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress ?: ""
                            if (host.isNotBlank() && !host.startsWith("127.")) {
                                return host
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    fun isCellularIp(ip: String): Boolean {
        if (ip.startsWith("192.0.0.")) return true
        if (ip.startsWith("100.")) {
            val secondOctet = ip.substringAfter("100.").substringBefore(".").toIntOrNull() ?: 0
            if (secondOctet in 64..127) return true
        }
        return false
    }

    fun isWifiConnected(context: Context?): Boolean {
        if (context == null) {
            val ip = getLocalPhoneIp()
            return ip.isNotBlank() && !isCellularIp(ip)
        }
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true || (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == false && getLocalPhoneIp().isNotBlank())
        } catch (_: Exception) {
            val ip = getLocalPhoneIp()
            ip.isNotBlank() && !isCellularIp(ip)
        }
    }

    fun isCellularActive(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Performs a comprehensive, dynamic diagnosis of the connection state.
     * Generates plain-English, non-technical diagnostics and 1-tap actions.
     */
    fun diagnose(
        context: Context?,
        targetIp: String,
        targetPort: Int = 53100,
        targetSsid: String? = null,
        rawError: String? = null,
        isConnected: Boolean = false,
        phoneIpOverride: String? = null
    ): NetworkDiagnosticReport {
        val detectedPhoneIp = phoneIpOverride ?: getLocalPhoneIp()
        if (isConnected) {
            return NetworkDiagnosticReport(
                type = DiagnosticType.CONNECTED,
                headline = "Connected & Active",
                explanation = "Direct high-speed communication active with Mac ($targetIp).",
                actionPrompt = "All features (clipboard, continuity camera, telemetry) are ready.",
                phoneIp = detectedPhoneIp,
                phoneIsWifi = true,
                phoneIsCellular = false,
                targetIp = targetIp,
                targetSsid = targetSsid,
                isSubnetMatch = true,
                primaryAction = DiagnosticAction.NONE
            )
        }

        val effectiveSsid = targetSsid?.takeIf { it.isNotBlank() }
            ?: CompanionBridge.targetMacSsid.value?.takeIf { it.isNotBlank() }
            ?: CompanionBridge.macWifiSsid.value.takeIf { it.isNotBlank() }

        val phoneIp = detectedPhoneIp
        val wifiIp = if (phoneIpOverride != null) phoneIpOverride else getWifiLocalIp()
        val hasWifi = if (phoneIpOverride != null) !isCellularIp(phoneIpOverride) else isWifiConnected(context)
        val hasCellular = if (phoneIpOverride != null) isCellularIp(phoneIpOverride) else isCellularActive(context)

        // 1. USB Connection Mode (127.0.0.1)
        if (targetIp == "127.0.0.1") {
            return NetworkDiagnosticReport(
                type = DiagnosticType.USB_DISCONNECTED,
                headline = "USB Cable Disconnected",
                explanation = "Direct USB-C tethering is not responding on your phone.",
                actionPrompt = "Plug your phone into your Mac with a USB-C cable, or scan the Wi-Fi QR code to link wirelessly.",
                phoneIp = "127.0.0.1",
                phoneIsWifi = hasWifi,
                phoneIsCellular = hasCellular,
                targetIp = targetIp,
                targetSsid = effectiveSsid,
                isSubnetMatch = true,
                primaryAction = DiagnosticAction.CONNECT_USB
            )
        }

        // 2. Mobile Data Active (Phone has no Wi-Fi connection)
        if (!hasWifi || (hasCellular && wifiIp.isBlank())) {
            val explanation = if (!effectiveSsid.isNullOrBlank()) {
                "Your phone is on Cellular Mobile Data, but your Mac is on Wi-Fi '$effectiveSsid'."
            } else {
                "Your phone is currently using Cellular Mobile Data instead of Wi-Fi."
            }
            val prompt = if (!effectiveSsid.isNullOrBlank()) {
                "Connect this phone to Wi-Fi '$effectiveSsid', or turn on your Personal Hotspot."
            } else {
                "Connect this phone to the same Wi-Fi as your Mac, or turn on your Personal Hotspot."
            }
            return NetworkDiagnosticReport(
                type = DiagnosticType.CELLULAR_DATA_ONLY,
                headline = "Phone is on Mobile Data",
                explanation = explanation,
                actionPrompt = prompt,
                phoneIp = phoneIp,
                phoneIsWifi = false,
                phoneIsCellular = true,
                targetIp = targetIp,
                targetSsid = effectiveSsid,
                isSubnetMatch = false,
                primaryAction = DiagnosticAction.OPEN_WIFI_SETTINGS
            )
        }

        // 3. Different Wi-Fi Network / Subnet Mismatch
        val activeIp = if (wifiIp.isNotBlank()) wifiIp else phoneIp
        val subnetMatch = isSameSubnet(activeIp, targetIp)

        if (!subnetMatch && activeIp.isNotBlank() && targetIp.isNotBlank()) {
            val macSubnet = extractSubnet(targetIp)
            val phoneSubnet = extractSubnet(activeIp)
            val explanation = if (!effectiveSsid.isNullOrBlank()) {
                "Your Mac is on Wi-Fi '$effectiveSsid' ($targetIp), but your phone is on a different network ($activeIp)."
            } else {
                "Your Mac is on network $macSubnet.x ($targetIp), but your phone is on $phoneSubnet.x ($activeIp)."
            }
            val prompt = if (!effectiveSsid.isNullOrBlank()) {
                "Switch your phone's Wi-Fi to '$effectiveSsid', or turn on Personal Hotspot."
            } else {
                "Connect both your phone and Mac to the exact same Wi-Fi router or Personal Hotspot."
            }
            return NetworkDiagnosticReport(
                type = DiagnosticType.DIFFERENT_WIFI_NETWORK,
                headline = "Different Wi-Fi Network Detected",
                explanation = explanation,
                actionPrompt = prompt,
                phoneIp = activeIp,
                phoneIsWifi = true,
                phoneIsCellular = hasCellular,
                targetIp = targetIp,
                targetSsid = effectiveSsid,
                isSubnetMatch = false,
                primaryAction = DiagnosticAction.OPEN_WIFI_SETTINGS
            )
        }

        // 4. Same Subnet, but Connection Refused (Mac app closed)
        val err = rawError ?: ""
        if (err.contains("Security Authorization", ignoreCase = true) || err.contains("auth_rejected", ignoreCase = true) || err.contains("not authorized", ignoreCase = true)) {
            return NetworkDiagnosticReport(
                type = DiagnosticType.SECURITY_AUTH_REQUIRED,
                headline = "Device Authorization Required",
                explanation = "This Mac has Strict Device Lock enabled to block unauthorized devices on this Wi-Fi network.",
                actionPrompt = "Scan the QR code on your Mac screen with this phone, or click 'Authorize' on your Mac.",
                phoneIp = activeIp,
                phoneIsWifi = true,
                phoneIsCellular = hasCellular,
                targetIp = targetIp,
                targetSsid = effectiveSsid,
                isSubnetMatch = true,
                primaryAction = DiagnosticAction.NONE
            )
        }
        if (err.contains("ECONNREFUSED", ignoreCase = true) || err.contains("Connection refused", ignoreCase = true)) {
            return NetworkDiagnosticReport(
                type = DiagnosticType.MAC_APP_NOT_RUNNING,
                headline = "Idroid is Closed on Mac",
                explanation = "Your Mac refused the connection at $targetIp. The Idroid app is not running.",
                actionPrompt = "Open the Idroid app on your Mac and ensure it is active in your menu bar.",
                phoneIp = activeIp,
                phoneIsWifi = true,
                phoneIsCellular = hasCellular,
                targetIp = targetIp,
                targetSsid = effectiveSsid,
                isSubnetMatch = true,
                primaryAction = DiagnosticAction.OPEN_DOCTOR
            )
        }

        // 5. Same Subnet, but Timeout / Router AP Isolation
        return NetworkDiagnosticReport(
            type = DiagnosticType.ROUTER_AP_ISOLATION,
            headline = "Router Blocking Direct Link (AP Isolation)",
            explanation = "Both devices are on the same Wi-Fi, but your router is blocking them from talking to each other.",
            actionPrompt = "Turn on Personal Hotspot on your phone and connect your Mac to it for an instant direct link, or plug in a USB-C cable.",
            phoneIp = activeIp,
            phoneIsWifi = true,
            phoneIsCellular = hasCellular,
            targetIp = targetIp,
            targetSsid = effectiveSsid,
            isSubnetMatch = true,
            primaryAction = DiagnosticAction.OPEN_HOTSPOT_SETTINGS
        )
    }
}
