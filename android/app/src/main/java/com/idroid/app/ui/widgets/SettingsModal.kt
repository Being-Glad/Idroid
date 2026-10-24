package com.idroid.app.ui.widgets

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.service.IdroidService
import com.idroid.app.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModal(
    macName: String,
    connType: String,
    isConnected: Boolean,
    onDismiss: () -> Unit,
    onOpenDoctor: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("idroid_config", Context.MODE_PRIVATE) }

    // Mirroring & Display Preferences
    var audioMirror by remember { mutableStateOf(prefs.getBoolean("pref_audio_mirror", true)) }
    var screenOffMode by remember { mutableStateOf(prefs.getBoolean("pref_screen_off", false)) }
    var dockNavBar by remember { mutableStateOf(prefs.getBoolean("pref_dock_nav", true)) }
    var highRefresh by remember { mutableStateOf(prefs.getBoolean("pref_high_refresh", true)) }

    // Continuity Camera Preferences
    var cameraLens by remember { mutableStateOf(prefs.getString("pref_camera_lens", "1.0x Primary") ?: "1.0x Primary") }
    var cameraOrientation by remember { mutableStateOf(prefs.getInt("pref_camera_orientation", 180)) }
    var cameraMirror by remember { mutableStateOf(prefs.getBoolean("pref_camera_mirror", false)) }
    var streamQuality by remember { mutableStateOf(prefs.getString("pref_camera_quality", "1080p 60fps") ?: "1080p 60fps") }

    // Cross-Device Sync Preferences
    var clipboardSync by remember { mutableStateOf(prefs.getBoolean("pref_clipboard_sync", true)) }
    var notificationSync by remember { mutableStateOf(prefs.getBoolean("pref_notification_sync", true)) }
    var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean("pref_haptics", true)) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        scrimColor = ObsidianVoid.copy(alpha = 0.85f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.5.dp)
                    .clip(CircleShape)
                    .background(ObsidianBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SolarOrange.copy(alpha = 0.15f))
                            .border(1.dp, SolarOrange.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = SolarOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Idroid Settings",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                        Text(
                            text = "macOS & Phone Ecosystem Controls",
                            fontSize = 12.sp,
                            fontFamily = InterFontFamily,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurfaceVariant)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Section 1: Connected Device Profile
            Surface(
                color = ObsidianVoid,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PureWhite.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LaptopMac,
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = macName.ifBlank { "Apple Mac" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = InterFontFamily,
                                color = PureWhite
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isConnected) EmeraldBeacon else TextMuted)
                                )
                                Text(
                                    text = if (isConnected) "Connected • High Speed Active" else "Disconnected",
                                    fontSize = 11.5.sp,
                                    fontFamily = InterFontFamily,
                                    color = if (isConnected) EmeraldBeacon else TextMuted
                                )
                            }
                        }

                        Surface(
                            color = if (connType.contains("USB", ignoreCase = true)) EmeraldBeacon.copy(alpha = 0.15f) else SolarOrange.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (connType.contains("USB", ignoreCase = true)) EmeraldBeacon.copy(alpha = 0.4f) else SolarOrange.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = if (connType.contains("USB", ignoreCase = true)) "⚡️ USB Direct" else "📡 Wi-Fi Mesh",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = InterFontFamily,
                                color = if (connType.contains("USB", ignoreCase = true)) EmeraldBeacon else SolarOrange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (isConnected) {
                        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

                        // Quick Mac Control Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    IdroidService.instance?.sendCommand("lock_mac")
                                    Toast.makeText(context, "Lock command sent to Mac", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Lock Mac", fontSize = 12.sp, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    IdroidService.instance?.sendCommand("sleep_mac")
                                    Toast.makeText(context, "Sleep command sent to Mac", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sleep Display", fontSize = 12.sp, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Section 2: Continuity Camera Settings
            SettingsSectionCard(title = "Continuity Camera Studio", icon = Icons.Default.Videocam) {
                // Quality Picker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stream Quality & Framerate", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, fontFamily = InterFontFamily)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1080p 60fps", "4K Ultra", "Low Latency").forEach { q ->
                            val isSelected = streamQuality == q
                            val qShape = RoundedCornerShape(10.dp)
                            Surface(
                                onClick = {
                                    streamQuality = q
                                    prefs.edit().putString("pref_camera_quality", q).apply()
                                },
                                color = if (isSelected) SolarOrange.copy(alpha = 0.2f) else ObsidianVoid,
                                shape = qShape,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) SolarOrange else ObsidianBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(qShape)
                            ) {
                                Text(
                                    text = q,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) SolarOrange else PureWhite,
                                    fontFamily = InterFontFamily,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = ObsidianBorder, thickness = 0.8.dp)

                // Default Lens
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Default Camera Optics", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, fontFamily = InterFontFamily)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("0.6x UltraWide", "1.0x Primary", "Front Selfie").forEach { lens ->
                            val isSelected = cameraLens == lens
                            val lensShape = RoundedCornerShape(10.dp)
                            Surface(
                                onClick = {
                                    cameraLens = lens
                                    prefs.edit().putString("pref_camera_lens", lens).apply()
                                },
                                color = if (isSelected) SolarOrange.copy(alpha = 0.2f) else ObsidianVoid,
                                shape = lensShape,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) SolarOrange else ObsidianBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(lensShape)
                            ) {
                                Text(
                                    text = lens,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) SolarOrange else PureWhite,
                                    fontFamily = InterFontFamily,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = ObsidianBorder, thickness = 0.8.dp)

                // Orientation
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Default Video Orientation", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, fontFamily = InterFontFamily)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            180 to "180° Upright",
                            0 to "0° Standard",
                            90 to "90° CW",
                            270 to "270° CCW"
                        ).forEach { (rot, label) ->
                            val isSelected = cameraOrientation == rot
                            val rotShape = RoundedCornerShape(8.dp)
                            Surface(
                                onClick = {
                                    cameraOrientation = rot
                                    prefs.edit().putInt("pref_camera_orientation", rot).apply()
                                },
                                color = if (isSelected) SolarOrange.copy(alpha = 0.2f) else ObsidianVoid,
                                shape = rotShape,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) SolarOrange else ObsidianBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(rotShape)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) SolarOrange else PureWhite,
                                    fontFamily = InterFontFamily,
                                    modifier = Modifier.padding(vertical = 7.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = ObsidianBorder, thickness = 0.8.dp)

                SettingsToggleRow(
                    title = "Mirror Front Selfie Camera",
                    subtitle = "Flip horizontally like a mirror when using front lens",
                    checked = cameraMirror,
                    onCheckedChange = {
                        cameraMirror = it
                        prefs.edit().putBoolean("pref_camera_mirror", it).apply()
                    }
                )
            }

            // Section 3: Mirroring & Display Preferences
            SettingsSectionCard(title = "Display & Screen Mirroring", icon = Icons.Default.Monitor) {
                SettingsToggleRow(
                    title = "Play Device Audio on Mac",
                    subtitle = "Stream phone sound directly to Mac speakers (Opus 128 kbps)",
                    checked = audioMirror,
                    onCheckedChange = {
                        audioMirror = it
                        prefs.edit().putBoolean("pref_audio_mirror", it).apply()
                    }
                )

                HorizontalDivider(color = ObsidianBorder, thickness = 0.8.dp)

                SettingsToggleRow(
                    title = "Turn Phone Screen Off on Desk",
                    subtitle = "Keep AMOLED display black while streaming to avoid burn-in",
                    checked = screenOffMode,
                    onCheckedChange = {
                        screenOffMode = it
                        prefs.edit().putBoolean("pref_screen_off", it).apply()
                    }
                )

                HorizontalDivider(color = ObsidianBorder, thickness = 0.8.dp)

                SettingsToggleRow(
                    title = "Dock Hardware Navigation Bar Below Mirror",
                    subtitle = "Attach physical Back, Home, and Recents buttons under window",
                    checked = dockNavBar,
                    onCheckedChange = {
                        dockNavBar = it
                        prefs.edit().putBoolean("pref_dock_nav", it).apply()
                    }
                )

                HorizontalDivider(color = ObsidianBorder, thickness = 0.8.dp)

                SettingsToggleRow(
                    title = "High Refresh Rate (120 FPS)",
                    subtitle = "Enable silky 120 FPS streaming on high-speed USB and Wi-Fi 6",
                    checked = highRefresh,
                    onCheckedChange = {
                        highRefresh = it
                        prefs.edit().putBoolean("pref_high_refresh", it).apply()
                    }
                )
            }

            // Section 4: Ecosystem Sync
            SettingsSectionCard(title = "Seamless Handover & Sync", icon = Icons.Default.Sync) {
                SettingsToggleRow(
                    title = "Universal Clipboard Sync",
                    subtitle = "Bidirectional copy/paste between Mac and phone instantly",
                    checked = clipboardSync,
                    onCheckedChange = {
                        clipboardSync = it
                        prefs.edit().putBoolean("pref_clipboard_sync", it).apply()
                    }
                )

                HorizontalDivider(color = ObsidianBorder, thickness = 0.8.dp)

                SettingsToggleRow(
                    title = "Mirror Phone Notifications",
                    subtitle = "Deliver incoming SMS, WhatsApp, and call alerts to macOS banner",
                    checked = notificationSync,
                    onCheckedChange = {
                        notificationSync = it
                        prefs.edit().putBoolean("pref_notification_sync", it).apply()
                    }
                )

                HorizontalDivider(color = ObsidianBorder, thickness = 0.8.dp)

                SettingsToggleRow(
                    title = "Haptic Touch Feedback",
                    subtitle = "Gentle tactile vibration when clicking trackpad and navigation keys",
                    checked = hapticsEnabled,
                    onCheckedChange = {
                        hapticsEnabled = it
                        prefs.edit().putBoolean("pref_haptics", it).apply()
                    }
                )
            }

            // Section 5: Diagnostics & Troubleshooting
            Surface(
                color = ObsidianVoid,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Diagnostics & Maintenance",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = PureWhite
                    )

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenDoctor()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SolarOrange.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SolarOrange),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.size(16.dp), tint = SolarOrange)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Idroid Doctor & Network Benchmark", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, color = SolarOrange, fontSize = 12.5.sp)
                    }

                    if (isConnected) {
                        Button(
                            onClick = {
                                onDismiss()
                                onDisconnect()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger.copy(alpha = 0.18f), contentColor = CrimsonDanger),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Disconnect Session", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }

            // Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Idroid Companion v1.0.4 • Build 2026.10\nDesigned for macOS Apple Silicon & Android",
                    fontSize = 11.sp,
                    fontFamily = InterFontFamily,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = ObsidianVoid,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(18.dp))
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val rowShape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFontFamily,
                color = PureWhite
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                fontFamily = InterFontFamily,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureWhite,
                checkedTrackColor = SolarOrange,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = ObsidianBorder
            )
        )
    }
}
