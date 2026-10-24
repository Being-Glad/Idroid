package com.idroid.app.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.network.DiagnosticAction
import com.idroid.app.network.DiagnosticType
import com.idroid.app.network.NetworkDiagnostics
import com.idroid.app.service.CompanionBridge
import com.idroid.app.ui.*
import com.idroid.app.ui.widgets.IdroidDoctorModal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ScannerScreen - Elegant Peer-to-Peer Mac Discovery & Pairing Hub.
 * Features automatic USB / Wi-Fi beacon detection without aggressive camera hogging.
 */
@Composable
fun ScannerScreen(
    savedIp: String,
    savedPort: Int,
    onConnectRequest: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var manualIp by remember { mutableStateOf(savedIp.ifBlank { "" }) }
    var manualPort by remember { mutableStateOf(savedPort.toString()) }
    var isManualExpanded by remember { mutableStateOf(false) }
    var showDoctorModal by remember { mutableStateOf(false) }
    var showQrScannerSheet by remember { mutableStateOf(false) }

    var isUsbDetected by remember { mutableStateOf(false) }
    var isWifiHostDetected by remember { mutableStateOf(false) }
    var discoveredWifiIp by remember { mutableStateOf(savedIp.ifBlank { "" }) }
    var isPhoneWifiConnected by remember { mutableStateOf(true) }

    val isConnecting by CompanionBridge.isConnecting
    val connectionError by CompanionBridge.connectionError

    // Helper to get active Wi-Fi IPv4 address of phone
    fun getDeviceLocalWifiIp(): String {
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

    // Background peer prober (USB 127.0.0.1:53100 and Wi-Fi host candidates)
    LaunchedEffect(savedIp, savedPort) {
        withContext(Dispatchers.IO) {
            while (isActive) {
                if (CompanionBridge.isConnected.value) {
                    delay(3000)
                    continue
                }

                // 1. Probe USB Reverse Tunnel
                var usbFound = false
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress("127.0.0.1", 53100), 300)
                    val out = java.io.PrintWriter(s.getOutputStream(), true)
                    out.println("{\"type\":\"probe\"}")
                    s.close()
                    usbFound = true
                } catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    val wasUsb = isUsbDetected
                    isUsbDetected = usbFound
                    if (usbFound && !CompanionBridge.isConnected.value && !CompanionBridge.isConnecting.value && !wasUsb) {
                        onConnectRequest("127.0.0.1", 53100)
                    }
                }

                // 2. Check Wi-Fi state & Probe Wi-Fi Host
                val localPhoneIp = getDeviceLocalWifiIp()
                val hasWifi = localPhoneIp.isNotBlank()
                var wifiFound = false
                var foundWifiHost = ""

                val candidates = mutableListOf<String>()
                if (savedIp.isNotBlank() && savedIp != "127.0.0.1") candidates.add(savedIp)
                val lastTarget = CompanionBridge.lastTargetIp.value
                if (lastTarget.isNotBlank() && lastTarget != "127.0.0.1" && !candidates.contains(lastTarget)) {
                    candidates.add(lastTarget)
                }

                // Subnet peer detection (Mac as Wi-Fi peer or Hotspot gateway/client)
                if (localPhoneIp.contains(".")) {
                    val subnetPrefix = localPhoneIp.substringBeforeLast(".") + "."
                    // Gateway is always priority candidate #1 on any network
                    val gw = subnetPrefix + "1"
                    if (!candidates.contains(gw) && gw != localPhoneIp) {
                        candidates.add(gw)
                    }

                    // Common LAN hosts for computers on typical home/office/hotspot subnets
                    val commonHostIds = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 20, 100, 128)
                    for (hostId in commonHostIds) {
                        val peer = subnetPrefix + hostId
                        if (!candidates.contains(peer) && peer != localPhoneIp) {
                            candidates.add(peer)
                        }
                    }
                }

                // Parallel socket probe with fast 350ms timeout
                coroutineScope {
                    val probeJobs = candidates.map { cand ->
                        async {
                            try {
                                val s = Socket()
                                s.connect(InetSocketAddress(cand, savedPort), 350)
                                val out = java.io.PrintWriter(s.getOutputStream(), true)
                                out.println("{\"type\":\"probe\"}")
                                s.close()
                                cand
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    val probeResults = probeJobs.awaitAll()
                    val discoveredCand = probeResults.filterNotNull().firstOrNull { it.isNotBlank() }
                    if (discoveredCand != null) {
                        wifiFound = true
                        foundWifiHost = discoveredCand
                    }
                }

                withContext(Dispatchers.Main) {
                    isPhoneWifiConnected = hasWifi
                    isWifiHostDetected = wifiFound
                    if (foundWifiHost.isNotBlank()) {
                        discoveredWifiIp = foundWifiHost
                        if (manualIp.isBlank()) {
                            manualIp = foundWifiHost
                        }
                        if (!CompanionBridge.isConnected.value && !CompanionBridge.isConnecting.value && CompanionBridge.connectionError.value != null) {
                            CompanionBridge.connectionError.value = null
                            onConnectRequest(foundWifiHost, savedPort)
                        }
                    }
                }

                delay(2500)
            }
        }
    }

    if (showDoctorModal) {
        IdroidDoctorModal(onDismiss = { showDoctorModal = false })
    }

    if (showQrScannerSheet) {
        QrScannerBottomSheet(
            onDismiss = { showQrScannerSheet = false },
            onQrCodeDetected = { ip, port ->
                android.util.Log.i("IdroidScanner", "QR Code Scanned successfully: $ip:$port")
                manualIp = ip
                manualPort = port.toString()
                discoveredWifiIp = ip
                val prefs = context.getSharedPreferences("idroid_config", Context.MODE_PRIVATE)
                val token = CompanionBridge.pairingToken.value ?: ""
                val editor = prefs.edit().putString("last_ip", ip).putInt("last_port", port)
                if (token.isNotBlank()) {
                    editor.putString("pairing_token", token)
                }
                editor.apply()
                onConnectRequest(ip, port)
                Toast.makeText(context, "Pairing securely with Mac at $ip:$port...", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header & Discovery Radar Card
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isUsbDetected || isWifiHostDetected) EmeraldBeacon else SolarOrange)
                        )
                        Text(
                            text = if (isUsbDetected || isWifiHostDetected) "Mac Ready to Link" else "Searching for Mac",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                    }

                    // Idroid Doctor Pill
                    val diagShape = RoundedCornerShape(12.dp)
                    Surface(
                        onClick = { showDoctorModal = true },
                        color = SolarOrange.copy(alpha = 0.14f),
                        shape = diagShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SolarOrange.copy(alpha = 0.35f)),
                        modifier = Modifier.clip(diagShape)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.MedicalServices, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(13.dp))
                            Text("Diagnostics", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SolarOrange, fontFamily = InterFontFamily)
                        }
                    }
                }

                // Discovery Radar Beacon (Pulsing Concentric Rings)
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "RadarBeacon")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.35f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1800, easing = EaseOutCubic),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "pulseScale"
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.65f,
                        targetValue = 0.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1800, easing = EaseOutCubic),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "pulseAlpha"
                    )

                    // Outer pulse ring
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .border(
                                1.5.dp,
                                if (isUsbDetected) EmeraldBeacon.copy(alpha = pulseAlpha) else SolarOrange.copy(alpha = pulseAlpha),
                                CircleShape
                            )
                    )

                    // Middle ambient halo
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        if (isUsbDetected) EmeraldBeacon.copy(alpha = 0.25f) else SolarOrange.copy(alpha = 0.22f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Center Glass Mac Emblem
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(ObsidianVoid)
                            .border(
                                1.5.dp,
                                if (isUsbDetected) EmeraldBeacon else SolarOrange.copy(alpha = 0.6f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LaptopMac,
                            contentDescription = "Mac",
                            tint = if (isUsbDetected) EmeraldBeacon else PureWhite,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Text(
                    text = when {
                        isUsbDetected -> "High-speed direct USB-C connection detected"
                        isWifiHostDetected -> "Mac detected on local Wi-Fi network"
                        else -> "Ensure Idroid is running on your Mac on the same Wi-Fi or connected via USB-C"
                    },
                    fontSize = 12.5.sp,
                    fontFamily = InterFontFamily,
                    color = if (isUsbDetected || isWifiHostDetected) PureWhite else TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 17.sp
                )
            }
        }

        // Live Discovered Device Action Card (Direct 1-Tap Connect!)
        if (isUsbDetected) {
            Surface(
                color = EmeraldBeacon.copy(alpha = 0.10f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldBeacon.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(EmeraldBeacon.copy(alpha = 0.2f))
                            .border(1.dp, EmeraldBeacon.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = null, tint = EmeraldBeacon, modifier = Modifier.size(24.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Om's MacBook Pro", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily, color = PureWhite)
                            Surface(color = EmeraldBeacon, shape = RoundedCornerShape(6.dp)) {
                                Text("USB 0.2ms", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                        Text("Zero network lag & simultaneous charging", fontSize = 11.5.sp, fontFamily = InterFontFamily, color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            onConnectRequest("127.0.0.1", 53100)
                            Toast.makeText(context, "Linking via USB-C...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldBeacon, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Link Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily)
                    }
                }
            }
        } else if (isWifiHostDetected && discoveredWifiIp.isNotBlank()) {
            Surface(
                color = SolarOrange.copy(alpha = 0.10f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SolarOrange.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(SolarOrange.copy(alpha = 0.2f))
                            .border(1.dp, SolarOrange.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(24.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Om's MacBook Pro", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily, color = PureWhite)
                            Surface(color = SolarOrange, shape = RoundedCornerShape(6.dp)) {
                                Text("Wi-Fi", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PureWhite, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                        FluidPrivacyMask(
                            rawText = "$discoveredWifiIp:$savedPort",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }

                    Button(
                        onClick = {
                            onConnectRequest(discoveredWifiIp, savedPort)
                            Toast.makeText(context, "Connecting to Mac at $discoveredWifiIp...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SolarOrange, contentColor = PureWhite),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = InterFontFamily)
                    }
                }
            }
        }

        // Wi-Fi Disconnected Warning HUD
        if (!isPhoneWifiConnected) {
            Surface(
                color = SolarOrange.copy(alpha = 0.14f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SolarOrange.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.WifiOff, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(22.dp))
                    Column {
                        Text(
                            text = "Phone Wi-Fi is Disconnected",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = SolarOrange
                        )
                        Text(
                            text = "Connect this phone to your Wi-Fi router or Mac Hotspot to link wirelessly.",
                            fontSize = 11.5.sp,
                            fontFamily = InterFontFamily,
                            color = PureWhite.copy(alpha = 0.8f),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Live Connection Status HUD
        AnimatedVisibility(
            visible = isConnecting,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = SolarOrange.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SolarOrange.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = SolarOrange,
                        strokeWidth = 2.dp
                    )
                    Column {
                        Text(
                            text = "Establishing Connection...",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = SolarOrange
                        )
                        Text(
                            text = "Handshaking with Idroid desktop engine ($manualIp)...",
                            fontSize = 11.5.sp,
                            fontFamily = InterFontFamily,
                            color = PureWhite.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        // Dynamic Idroid Doctor Diagnosis HUD
        AnimatedVisibility(
            visible = !isConnecting && connectionError != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val targetSsid = CompanionBridge.targetMacSsid.value ?: CompanionBridge.macWifiSsid.value
            val diagReport = remember(connectionError, manualIp, targetSsid) {
                NetworkDiagnostics.diagnose(
                    context = context,
                    targetIp = manualIp.ifBlank { savedIp },
                    targetPort = manualPort.toIntOrNull() ?: savedPort,
                    targetSsid = targetSsid,
                    rawError = connectionError,
                    isConnected = CompanionBridge.isConnected.value
                )
            }

            val cardColor = when (diagReport.type) {
                DiagnosticType.CELLULAR_DATA_ONLY, DiagnosticType.DIFFERENT_WIFI_NETWORK -> SolarOrange
                DiagnosticType.ROUTER_AP_ISOLATION -> SolarOrange
                DiagnosticType.MAC_APP_NOT_RUNNING -> SolarOrange
                DiagnosticType.SECURITY_AUTH_REQUIRED -> SolarOrange
                else -> CrimsonDanger
            }

            Surface(
                color = cardColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, cardColor.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Row with Diagnosis Tag
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = when (diagReport.type) {
                                    DiagnosticType.CELLULAR_DATA_ONLY -> Icons.Default.WifiOff
                                    DiagnosticType.DIFFERENT_WIFI_NETWORK -> Icons.Default.WifiOff
                                    DiagnosticType.ROUTER_AP_ISOLATION -> Icons.Default.Router
                                    DiagnosticType.MAC_APP_NOT_RUNNING -> Icons.Default.LaptopMac
                                    DiagnosticType.SECURITY_AUTH_REQUIRED -> Icons.Default.Shield
                                    else -> Icons.Default.WarningAmber
                                },
                                contentDescription = null,
                                tint = cardColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = diagReport.headline,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = InterFontFamily,
                                color = cardColor
                            )
                        }

                        Surface(
                            onClick = { showDoctorModal = true },
                            color = cardColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, cardColor.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(Icons.Default.MedicalServices, contentDescription = null, tint = cardColor, modifier = Modifier.size(12.dp))
                                Text("Doctor", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cardColor, fontFamily = InterFontFamily)
                            }
                        }
                    }

                    // Natural Language Explanation
                    Text(
                        text = diagReport.explanation,
                        fontSize = 12.sp,
                        fontFamily = InterFontFamily,
                        color = PureWhite.copy(alpha = 0.92f),
                        lineHeight = 16.5.sp
                    )

                    // Doctor Action Prompt
                    Surface(
                        color = ObsidianVoid.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.TipsAndUpdates,
                                contentDescription = null,
                                tint = cardColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = diagReport.actionPrompt,
                                fontSize = 11.5.sp,
                                fontFamily = InterFontFamily,
                                color = PureWhite,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Professional OEM-Resilient Action Deck
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (diagReport.type) {
                            DiagnosticType.CELLULAR_DATA_ONLY, DiagnosticType.DIFFERENT_WIFI_NETWORK -> {
                                Button(
                                    onClick = { PlatformIntentLauncher.openWifiSettings(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SolarOrange),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1.25f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Wi-Fi Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }

                                Button(
                                    onClick = { PlatformIntentLauncher.openHotspotSettings(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Hotspot", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }

                                Button(
                                    onClick = {
                                        onConnectRequest("127.0.0.1", savedPort)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.weight(0.9f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("USB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }
                            }
                            DiagnosticType.ROUTER_AP_ISOLATION -> {
                                Button(
                                    onClick = { PlatformIntentLauncher.openHotspotSettings(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SolarOrange),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1.25f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Personal Hotspot", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }

                                Button(
                                    onClick = {
                                        onConnectRequest("127.0.0.1", savedPort)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("USB Cable", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }

                                Button(
                                    onClick = { showDoctorModal = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.weight(0.9f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.size(13.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Doctor", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }
                            }
                            DiagnosticType.SECURITY_AUTH_REQUIRED -> {
                                Button(
                                    onClick = { showQrScannerSheet = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SolarOrange),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1.3f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Scan QR Code", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }

                                Button(
                                    onClick = {
                                        val ip = manualIp.trim()
                                        val port = manualPort.toIntOrNull() ?: savedPort
                                        onConnectRequest(ip, port)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Retry Link", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }

                                Button(
                                    onClick = { showDoctorModal = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.weight(0.9f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.size(13.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Doctor", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        val ip = manualIp.trim()
                                        val port = manualPort.toIntOrNull() ?: savedPort
                                        onConnectRequest(ip, port)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = cardColor),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1.2f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Retry Link", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }

                                Button(
                                    onClick = { showQrScannerSheet = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Scan QR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }

                                Button(
                                    onClick = {
                                        onConnectRequest("127.0.0.1", savedPort)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.weight(0.9f).height(36.dp)
                                ) {
                                    Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                                    Spacer(Modifier.width(4.dp))
                                    Text("USB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pairing Options Deck
        Text(
            text = "Pairing Options",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = InterFontFamily,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )

        // Option 1: 📷 Scan QR Code (On-Demand Bottom Sheet, No Permanent Camera!)
        val qrShape = RoundedCornerShape(18.dp)
        Surface(
            onClick = { showQrScannerSheet = true },
            color = ObsidianSurface,
            shape = qrShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clip(qrShape)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SolarOrange.copy(alpha = 0.15f))
                        .border(1.dp, SolarOrange.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = SolarOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Scan Mac QR Code",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = PureWhite
                    )
                    Text(
                        text = "Open camera scanner to point at your Mac screen",
                        fontSize = 12.sp,
                        fontFamily = InterFontFamily,
                        color = TextSecondary
                    )
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Option 2: ⚡️ Direct USB-C Plug & Play Card
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PureWhite.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Usb,
                        contentDescription = null,
                        tint = if (isUsbDetected) EmeraldBeacon else PureWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "USB-C Direct Link",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                        if (isUsbDetected) {
                            Surface(color = EmeraldBeacon.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text("Plugged In", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = EmeraldBeacon, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Text(
                        text = "Plug phone into your Mac for zero-config 0.2ms speed",
                        fontSize = 12.sp,
                        fontFamily = InterFontFamily,
                        color = TextSecondary
                    )
                }
            }
        }

        // Option 3: ⌨️ Manual IP Address Entry (Accordion)
        val manualCardShape = RoundedCornerShape(18.dp)
        Surface(
            color = ObsidianSurface,
            shape = manualCardShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clip(manualCardShape)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val accShape = RoundedCornerShape(10.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(accShape)
                        .clickable { isManualExpanded = !isManualExpanded }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(PureWhite.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SettingsEthernet,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Manual IP Entry (Advanced)",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                    }

                    Icon(
                        if (isManualExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(visible = isManualExpanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            label = { Text("Mac IP Address (e.g. 192.168.1.9)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SolarOrange,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            )
                        )

                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it },
                            label = { Text("Port (Default 53100)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SolarOrange,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            )
                        )

                        Button(
                            onClick = {
                                val p = manualPort.toIntOrNull() ?: 53100
                                onConnectRequest(manualIp.trim(), p)
                                Toast.makeText(context, "Connecting to ${manualIp.trim()}:$p...", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SolarOrange),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connect to IP", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}
