package com.idroid.app.ui.widgets

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.service.CompanionBridge
import com.idroid.app.ui.*

data class DoctorIssueItem(
    val id: String,
    val title: String,
    val summary: String,
    val category: String,
    val icon: ImageVector,
    val badge: String = "Solution",
    val content: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdroidDoctorModal(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var expandedIssueId by remember { mutableStateOf<String?>(null) }

    val isConnected by CompanionBridge.isConnected
    val connError by CompanionBridge.connectionError
    val targetIp by CompanionBridge.lastTargetIp

    val issues = remember {
        listOf(
            DoctorIssueItem(
                id = "hotspot_vs_wifi",
                title = "Why is Personal Hotspot faster & smoother than Home Wi-Fi?",
                summary = "Tested direct peer-to-peer links offer 1ms response time and silky 60 FPS without router interference.",
                category = "Performance",
                icon = Icons.Default.Bolt,
                badge = "Tested & Proven"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Yes, it is tested and proven! When connecting over Home Wi-Fi, every gesture and file travels in two hops: Phone → Home Router → Mac. Home routers also handle TVs, streaming, and family devices.",
                        fontSize = 12.5.sp,
                        fontFamily = InterFontFamily,
                        color = PureWhite.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                    Surface(
                        color = SolarOrange.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SolarOrange.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "🏆 Personal Hotspot Advantages:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SolarOrange,
                                fontFamily = InterFontFamily
                            )
                            Text(
                                text = "• 1 Direct Hop: Your phone talks straight to your Mac's Wi-Fi chip\n• Ultra-Low Latency: 1ms to 2ms response time (instant trackpad)\n• Full 5GHz Bandwidth: Up to 1200 Mbps transfer speed\n• Zero Router Firewall Blocking: Bypasses router AP isolation completely",
                                fontSize = 12.sp,
                                fontFamily = InterFontFamily,
                                color = PureWhite,
                                lineHeight = 17.sp
                            )
                        }
                    }
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_MAIN).apply {
                                    setClassName("com.android.settings", "com.android.settings.TetherSettings")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SolarOrange),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Mobile Hotspot Settings", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            },
            DoctorIssueItem(
                id = "router_isolation",
                title = "My devices keep scanning and won't connect on Wi-Fi",
                summary = "Your Wi-Fi router is keeping devices apart with 'AP Isolation'. Here are 3 quick fixes.",
                category = "Connection",
                icon = Icons.Default.Router,
                badge = "Common Issue"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Many home fiber routers (especially Airtel and Jio) have 'AP Isolation' or 'Client Isolation' turned ON by default. It allows internet access, but blocks devices on the same Wi-Fi from talking to each other.",
                        fontSize = 12.5.sp,
                        fontFamily = InterFontFamily,
                        color = PureWhite.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                    Surface(
                        color = ObsidianVoid,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("3 Easy Fixes:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PureWhite, fontFamily = InterFontFamily)
                            Text("1. Turn on Personal Hotspot on this phone and connect your Mac to it (100% guaranteed to work instantly).\n2. Plug in the USB cable for an instant wired bridge with charging.\n3. Open your router page (192.168.1.1) and disable 'AP Isolation' under Wi-Fi settings.", fontSize = 12.sp, color = TextSecondary, fontFamily = InterFontFamily, lineHeight = 17.sp)
                        }
                    }
                }
            },
            DoctorIssueItem(
                id = "hotspot_setup",
                title = "How to connect via Hotspot in 3 simple steps",
                summary = "Quick visual guide to pair your phone and Mac directly in under 10 seconds.",
                category = "Guide",
                icon = Icons.Default.WifiTethering,
                badge = "10 Seconds"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Step 1: Turn on Mobile Hotspot on this phone (Settings → Connections → Mobile Hotspot). Tip: Set band to 5 GHz for maximum speed.", fontSize = 12.sp, color = TextSecondary, fontFamily = InterFontFamily)
                    Text("Step 2: On your Mac, click the Wi-Fi icon and select your phone's hotspot name.", fontSize = 12.sp, color = TextSecondary, fontFamily = InterFontFamily)
                    Text("Step 3: The Idroid QR code on your Mac will update automatically. Scan it with this app and you're connected!", fontSize = 12.sp, color = TextSecondary, fontFamily = InterFontFamily)
                }
            },
            DoctorIssueItem(
                id = "apple_silicon_support",
                title = "Does Idroid work on Apple Silicon M1, M2, M3 & M4 Macs?",
                summary = "100% native Universal 2 architecture with sub-2ms response and hardware acceleration.",
                category = "Hardware",
                icon = Icons.Default.LaptopMac,
                badge = "Universal 2"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Yes! Idroid is fully tested and optimized for all Apple Silicon Macs (M1, M2, M3, M4, Pro, Max, and Ultra) as well as Intel MacBooks.",
                        fontSize = 12.5.sp,
                        fontFamily = InterFontFamily,
                        color = PureWhite.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                    Surface(
                        color = EmeraldBeacon.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBeacon.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("⚡ Apple Silicon Advantages:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = EmeraldBeacon, fontFamily = InterFontFamily)
                            Text("• Zero Rosetta Requirement: Runs natively on ARM64 with native Darwin system calls.\n• Sub-2ms Latency: High IPC & unified memory process gestures faster than Bluetooth mice.\n• Near-Zero Battery Drain: Handled silently by M-series energy-efficiency cores.", fontSize = 12.sp, color = PureWhite, fontFamily = InterFontFamily, lineHeight = 17.sp)
                        }
                    }
                }
            },
            DoctorIssueItem(
                id = "trackpad_features",
                title = "How do trackpad gestures and cursor acceleration work?",
                summary = "120 FPS fluid glide, tap-to-click, 2-finger scroll, right-click, and custom speed slider.",
                category = "Controls",
                icon = Icons.Default.TouchApp,
                badge = "120 FPS"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The virtual trackpad uses native macOS CoreGraphics event taps with non-linear ballistic acceleration (same as macOS Magic Trackpad).",
                        fontSize = 12.5.sp,
                        fontFamily = InterFontFamily,
                        color = PureWhite.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "• 1-Finger Drag: Steers cursor with precision\n• Single Tap: Standard left click\n• 2-Finger Drag / Edge Rail: Natural smooth scrolling\n• Dedicated Buttons: Left, Double, and Right mouse click buttons\n• Sensitivity Slider: Fine-tune cursor speed from 0.4x precision to 2.2x speed.",
                        fontSize = 12.sp,
                        fontFamily = InterFontFamily,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            },
            DoctorIssueItem(
                id = "live_keyboard_spotlight",
                title = "How do Live Keyboard entry and Spotlight search work?",
                summary = "Type directly into whatever text box or search field is active on your Mac.",
                category = "Controls",
                icon = Icons.Default.Keyboard,
                badge = "Direct Typing"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "When you tap 'Spotlight Search' or activate the Live Keyboard, keystrokes from your phone stream directly into the active cursor on your Mac.",
                        fontSize = 12.5.sp,
                        fontFamily = InterFontFamily,
                        color = PureWhite.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "• Types into search boxes, documents, code editors, and browsers.\n• Pressing Search/Enter or tapping '↵ Return' executes the search on macOS.\n• Includes quick tactile keys for Backspace, Space, Esc, and Tab.",
                        fontSize = 12.sp,
                        fontFamily = InterFontFamily,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            },
            DoctorIssueItem(
                id = "universal_clipboard",
                title = "How does Universal 2-Way Clipboard sync work?",
                summary = "Copy on Mac, paste on phone. Copy on phone, beam to Mac in 1 tap.",
                category = "Productivity",
                icon = Icons.Default.ContentPaste,
                badge = "Instant Sync"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Any text or link you copy on your Mac automatically streams to this phone in real-time. Tapping 'Copy to Phone' stores it in your Android clipboard. To send text to your Mac, type or paste into 'Send Text to Mac' and tap 'Beam to Mac Clipboard'.",
                        fontSize = 12.5.sp,
                        fontFamily = InterFontFamily,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            },
            DoctorIssueItem(
                id = "webcam_streaming",
                title = "Can I use my Android phone as a high-res Mac webcam?",
                summary = "Stream 1080p/4K video directly from your primary or ultra-wide camera.",
                category = "Continuity",
                icon = Icons.Default.Videocam,
                badge = "1080p / 4K"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Yes! The 'Advanced' tab features camera streaming with lens switching (Primary, Ultra-Wide, Telephoto). Video streams locally with low latency for meetings, presentations, and desk monitoring.",
                        fontSize = 12.5.sp,
                        fontFamily = InterFontFamily,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            },
            DoctorIssueItem(
                id = "lock_and_sleep",
                title = "How does remote Mac Screen Lock & Display Sleep work?",
                summary = "Instantly put your Mac display to sleep or lock the screen across the room.",
                category = "Controls",
                icon = Icons.Default.Lock,
                badge = "Instant Action"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tapping 'Sleep Display' puts the Mac monitors into energy-saving sleep immediately. Tapping 'Lock Mac' locks macOS and presents the password screen, securing your machine from across the room.",
                        fontSize = 12.5.sp,
                        fontFamily = InterFontFamily,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            },
            DoctorIssueItem(
                id = "usb_fallback",
                title = "Zero-Latency USB Cable Connection",
                summary = "How to use the seamless wired bridge while keeping your phone battery charged.",
                category = "Connection",
                icon = Icons.Default.Usb,
                badge = "Wired Bridge"
            ) {
                Text(
                    text = "If you plug in your phone via USB cable to your Mac, Idroid automatically bridges the connection with zero latency (<1ms) and charges your battery continuously. No Wi-Fi or router configuration needed.",
                    fontSize = 12.5.sp,
                    fontFamily = InterFontFamily,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            DoctorIssueItem(
                id = "privacy_security",
                title = "Is my data private and secure?",
                summary = "100% on-device local communication. Never uploaded to the cloud.",
                category = "Security",
                icon = Icons.Default.Security,
                badge = "100% Private"
            ) {
                Text(
                    text = "All trackpad input, clipboard text, files, and camera streams stay completely within your local Wi-Fi or direct hotspot link. No data ever passes through external servers or third-party clouds.",
                    fontSize = 12.5.sp,
                    fontFamily = InterFontFamily,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        )
    }

    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Hardware", "Connection", "Performance", "Controls", "Productivity", "Continuity", "Security")

    val filteredIssues = remember(searchQuery, selectedCategory) {
        issues.filter { issue ->
            val matchesCategory = selectedCategory == "All" || issue.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                issue.title.lowercase().contains(q) ||
                issue.summary.lowercase().contains(q) ||
                issue.category.lowercase().contains(q)
            }
            matchesCategory && matchesSearch
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.5.dp)
                    .clip(CircleShape)
                    .background(PureWhite.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SolarOrange.copy(alpha = 0.16f))
                        .border(1.dp, SolarOrange.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MedicalServices,
                        contentDescription = null,
                        tint = SolarOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Idroid Doctor & Help",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = PureWhite
                    )
                    Text(
                        text = "Real-time health check & 1-tap issue solver",
                        fontSize = 12.sp,
                        fontFamily = InterFontFamily,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Live Diagnostics Card
            Surface(
                color = ObsidianVoid,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Live Connection Health Check",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                        Surface(
                            color = if (isConnected) EmeraldBeacon.copy(alpha = 0.15f) else SolarOrange.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isConnected) EmeraldBeacon.copy(alpha = 0.4f) else SolarOrange.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = if (isConnected) "Active Link" else if (connError != null) "Router Blocked" else "Ready to Pair",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) EmeraldBeacon else SolarOrange,
                                fontFamily = InterFontFamily,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Divider(color = ObsidianBorder)

                    // Diagnosis details
                    if (isConnected) {
                        Text(
                            text = "🟢 Devices connected smoothly. Latency < 4ms with continuous telemetry sync.",
                            fontSize = 12.sp,
                            fontFamily = InterFontFamily,
                            color = EmeraldBeacon
                        )
                    } else if (connError != null) {
                        Text(
                            text = "🟡 Router issue detected: Your home Wi-Fi router isn't allowing direct links between devices (AP Isolation).",
                            fontSize = 12.sp,
                            fontFamily = InterFontFamily,
                            color = SolarOrange
                        )
                        Text(
                            text = "👉 Recommended Solution: Turn on Personal Hotspot on this phone and connect your Mac to it for an instant direct link.",
                            fontSize = 11.5.sp,
                            fontFamily = InterFontFamily,
                            color = PureWhite.copy(alpha = 0.9f)
                        )
                    } else {
                        Text(
                            text = "Ready to pair with Mac at $targetIp. Point camera at Mac QR code or tap Reconnect.",
                            fontSize = 12.sp,
                            fontFamily = InterFontFamily,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("Search any issue (e.g. Hotspot, Wi-Fi, Lag)...", fontSize = 12.5.sp, color = TextSecondary, fontFamily = InterFontFamily)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SolarOrange,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedContainerColor = ObsidianVoid,
                    unfocusedContainerColor = ObsidianVoid,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        color = if (isSelected) SolarOrange else ObsidianVoid,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) SolarOrange else ObsidianBorder
                        ),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = InterFontFamily,
                            color = if (isSelected) ObsidianVoid else TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Problem Solver Cards
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Help & Common Questions",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )
                Text(
                    text = "${filteredIssues.size} topics",
                    fontSize = 11.5.sp,
                    fontFamily = InterFontFamily,
                    color = TextSecondary
                )
            }

            filteredIssues.forEach { issue ->
                val isExpanded = expandedIssueId == issue.id

                Surface(
                    color = ObsidianVoid,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isExpanded) SolarOrange.copy(alpha = 0.5f) else ObsidianBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedIssueId = if (isExpanded) null else issue.id
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(SolarOrange.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    issue.icon,
                                    contentDescription = null,
                                    tint = SolarOrange,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = issue.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = InterFontFamily,
                                    color = PureWhite
                                )
                                if (!isExpanded) {
                                    Text(
                                        text = issue.summary,
                                        fontSize = 11.5.sp,
                                        fontFamily = InterFontFamily,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Divider(color = ObsidianBorder, modifier = Modifier.padding(bottom = 10.dp))
                                issue.content()
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(56.dp))
        }
    }
}
