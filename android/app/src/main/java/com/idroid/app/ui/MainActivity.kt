package com.idroid.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.service.CompanionBridge
import com.idroid.app.camera.CameraStreamManager
import com.idroid.app.service.IdroidService
import com.idroid.app.ui.navigation.CompanionNavTab
import com.idroid.app.ui.navigation.FloatingBottomBar
import com.idroid.app.ui.screens.*
import com.idroid.app.ui.widgets.IdroidDoctorModal

class MainActivity : ComponentActivity() {

    private var initialIp by mutableStateOf("192.168.1.10")
    private var initialPort by mutableStateOf(53100)

    private val requestPermissionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        IdroidService.instance?.refreshListeners()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            requestPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAndRequestPermissions()
        val prefs = getSharedPreferences("idroid_config", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("last_ip", null)
        val savedPort = prefs.getInt("last_port", 53100)
        if (!savedIp.isNullOrBlank()) {
            initialIp = savedIp
            initialPort = savedPort
            // Attempt background connection to last paired Mac
            connectService(initialIp, initialPort)
        }

        handlePairingIntent(intent)

        setContent {
            IdroidTheme {
                MainAppScreen(
                    defaultIp = initialIp,
                    defaultPort = initialPort,
                    onConnectRequest = { ip, port ->
                        initialIp = ip
                        initialPort = port
                        val p = getSharedPreferences("idroid_config", Context.MODE_PRIVATE)
                        p.edit().putString("last_ip", ip).putInt("last_port", port).apply()
                        connectService(ip, port)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePairingIntent(intent)
    }

    private fun handlePairingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "idroid" && uri.host == "pair") {
            val ip = uri.getQueryParameter("ip")
            val portStr = uri.getQueryParameter("port")
            val token = uri.getQueryParameter("token") ?: ""
            if (!ip.isNullOrBlank()) {
                initialIp = ip
                initialPort = portStr?.toIntOrNull() ?: 53100
                val prefs = getSharedPreferences("idroid_config", Context.MODE_PRIVATE)
                val editor = prefs.edit().putString("last_ip", initialIp).putInt("last_port", initialPort)
                if (token.isNotBlank()) {
                    editor.putString("pairing_token", token)
                }
                editor.apply()
                connectService(initialIp, initialPort)
                Toast.makeText(this, "Paired securely with $ip:$initialPort", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var lastConnectTime = 0L
    private var lastConnectTarget = ""

    private fun connectService(ip: String, port: Int) {
        val target = "$ip:$port"
        val now = System.currentTimeMillis()
        if (target == lastConnectTarget && (now - lastConnectTime) < 2000L) {
            // Drop burst requests within 2000ms
            return
        }
        lastConnectTime = now
        lastConnectTarget = target
        val intent = Intent(this, IdroidService::class.java).apply {
            putExtra("target_ip", ip)
            putExtra("target_port", port)
        }
        startService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    defaultIp: String,
    defaultPort: Int,
    onConnectRequest: (String, Int) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(CompanionNavTab.OVERVIEW) }

    val isConnected by CompanionBridge.isConnected
    val macName by CompanionBridge.macName
    val macBattery by CompanionBridge.macBattery
    val macIsCharging by CompanionBridge.macIsCharging
    val sharedClipboardText by CompanionBridge.macClipboard
    val connType by CompanionBridge.connectionType

    var isCameraActive by remember { mutableStateOf(false) }
    var selectedLens by remember { mutableStateOf("1.0x Primary") }
    var showSettingsModal by remember { mutableStateOf(false) }
    var showDoctorModal by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ObsidianVoid,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianVoid,
                    titleContentColor = PureWhite
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.idroid.app.R.drawable.ic_idroid_brand),
                            contentDescription = "Idroid Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Idroid",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                        // Live Pulse Beacon Dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) EmeraldBeacon else TextMuted.copy(alpha = 0.5f))
                        )
                    }
                },
                actions = {
                    if (isConnected) {
                        // Sleek, minimal 2026 Connected Glass Pill (e.g. "Om's Mac • 100% ⚡")
                        val statusShape = RoundedCornerShape(20.dp)
                        Surface(
                            onClick = { showSettingsModal = true },
                            color = ObsidianSurfaceVariant.copy(alpha = 0.6f),
                            shape = statusShape,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                ObsidianBorder.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(statusShape)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.LaptopMac,
                                    contentDescription = null,
                                    tint = EmeraldBeacon,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${if (macName.contains("'s")) macName.substringBefore("'s") + "'s Mac" else macName.take(10).ifBlank { "Mac" }} • $macBattery%${if (macIsCharging) " ⚡" else ""}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = InterFontFamily,
                                    color = PureWhite
                                )
                            }
                        }
                    }

                    // Sleek circular glass icon button for Diagnostics / Help
                    Surface(
                        onClick = { showDoctorModal = true },
                        color = ObsidianSurfaceVariant.copy(alpha = 0.6f),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Idroid Doctor & Help",
                                tint = TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Sleek circular glass icon button for Settings
                    Surface(
                        onClick = { showSettingsModal = true },
                        color = ObsidianSurfaceVariant.copy(alpha = 0.6f),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Device Settings",
                                tint = TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                }
            )
        }
    ) { innerPadding ->
        // Settings & Device Diagnostics Modal
        if (showSettingsModal) {
            com.idroid.app.ui.widgets.SettingsModal(
                macName = macName,
                connType = connType,
                isConnected = isConnected,
                onDismiss = { showSettingsModal = false },
                onOpenDoctor = { showDoctorModal = true },
                onDisconnect = {
                    IdroidService.instance?.disconnect()
                    Toast.makeText(context, "Disconnected from Mac", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Idroid Doctor & Diagnostic Help Modal
        if (showDoctorModal) {
            IdroidDoctorModal(onDismiss = { showDoctorModal = false })
        }

        // Main Content Area with Animated Transition and Floating Bottom Bar Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(ObsidianVoid)
        ) {
            Crossfade(
                targetState = isConnected,
                label = "connection_state_fade",
                modifier = Modifier.fillMaxSize()
            ) { connected ->
                if (!connected) {
                    // When DISCONNECTED: Show live camera QR viewfinder pairing & manual connection
                    ScannerScreen(
                        savedIp = defaultIp,
                        savedPort = defaultPort,
                        onConnectRequest = onConnectRequest
                    )
                } else {
                    // When CONNECTED: Show active tab screen
                    Crossfade(
                        targetState = selectedTab,
                        label = "tab_crossfade",
                        modifier = Modifier.fillMaxSize()
                    ) { tab ->
                        when (tab) {
                            CompanionNavTab.OVERVIEW -> OverviewScreen(
                                macName = macName,
                                macBattery = macBattery,
                                macIsCharging = macIsCharging,
                                connType = connType,
                                sharedClipboardText = sharedClipboardText,
                                onOpenSettings = { showSettingsModal = true },
                                onNavigateToTrackpad = { selectedTab = CompanionNavTab.TRACKPAD },
                                onNavigateToClipboard = { selectedTab = CompanionNavTab.CLIPBOARD },
                                onNavigateToFiles = { selectedTab = CompanionNavTab.FILES },
                                onNavigateToAdvanced = { selectedTab = CompanionNavTab.ADVANCED }
                            )

                            CompanionNavTab.TRACKPAD -> TrackpadScreen()

                            CompanionNavTab.CLIPBOARD -> ClipboardScreen(
                                sharedClipboardText = sharedClipboardText
                            )

                            CompanionNavTab.FILES -> FilesScreen()

                            CompanionNavTab.ADVANCED -> WebcamScreen(
                                isStreaming = isCameraActive,
                                selectedLens = selectedLens,
                                onToggleStream = {
                                    if (!isCameraActive) {
                                        val targetIp = CompanionBridge.lastTargetIp.value
                                        if (targetIp.isNotBlank()) {
                                            CameraStreamManager.startStream(context, targetIp, targetPort = 53104, fps = 60, quality = "1080p 60fps")
                                            isCameraActive = true
                                        } else {
                                            Toast.makeText(context, "Connect to Mac first to start wireless stream", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        CameraStreamManager.stopStream()
                                        isCameraActive = false
                                    }
                                },
                                onSelectLens = { selectedLens = it }
                            )
                        }
                    }
                }
            }

            // Self-contained Floating Bottom Bar Overlay (Truly floating, no black rectangular bar!)
            if (isConnected) {
                FloatingBottomBar(
                    currentTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                )
            }
        }
    }
}
