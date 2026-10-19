package com.idroid.app.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.service.CompanionBridge
import com.idroid.app.ui.*
import com.idroid.app.ui.widgets.IdroidDoctorModal

/**
 * ScannerScreen - Instant in-app QR code camera scanner & manual pairing flow.
 * Presented whenever Idroid is disconnected.
 */
@Composable
fun ScannerScreen(
    savedIp: String,
    savedPort: Int,
    onConnectRequest: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var manualIp by remember { mutableStateOf(savedIp.ifBlank { "192.168.1.10" }) }
    var isManualExpanded by remember { mutableStateOf(false) }
    var showDoctorModal by remember { mutableStateOf(false) }

    val isConnecting by CompanionBridge.isConnecting
    val connectionError by CompanionBridge.connectionError
    val targetIp by CompanionBridge.lastTargetIp
    val targetPort by CompanionBridge.lastTargetPort

    if (showDoctorModal) {
        IdroidDoctorModal(onDismiss = { showDoctorModal = false })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header Card with Idroid Doctor Access
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                                .background(SolarOrange)
                        )
                        Text(
                            text = "Connect to Your Mac",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                    }

                    // Idroid Doctor Trigger Pill
                    Surface(
                        color = SolarOrange.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SolarOrange.copy(alpha = 0.35f)),
                        modifier = Modifier.clickable { showDoctorModal = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.MedicalServices, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(13.dp))
                            Text("Idroid Doctor", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SolarOrange, fontFamily = InterFontFamily)
                        }
                    }
                }
                Text(
                    text = "Aim your camera at the QR code displayed on your Mac's Idroid screen to link instantly.",
                    fontSize = 12.5.sp,
                    fontFamily = InterFontFamily,
                    color = TextSecondary
                )
            }
        }

        // CameraX Viewfinder with Barcode Detection
        InAppQrScanner(
            onQrCodeDetected = { ip, port ->
                val prefs = context.getSharedPreferences("idroid_config", Context.MODE_PRIVATE)
                prefs.edit().putString("last_ip", ip).putInt("last_port", port).apply()
                onConnectRequest(ip, port)
                Toast.makeText(context, "Pairing with Mac at $ip:$port...", Toast.LENGTH_SHORT).show()
            }
        )

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
                            text = "Connecting to Your Mac...",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = SolarOrange
                        )
                        Text(
                            text = "Establishing instant wireless link",
                            fontSize = 11.5.sp,
                            fontFamily = InterFontFamily,
                            color = PureWhite.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        // Live Connection Error Diagnostic Banner (Consumer Friendly)
        AnimatedVisibility(
            visible = !isConnecting && connectionError != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                color = SolarOrange.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SolarOrange.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = SolarOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Wi-Fi Router Keeping Devices Apart",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = SolarOrange
                        )
                    }
                    Text(
                        text = "Your home Wi-Fi router is blocking direct cross-device traffic. Connect in seconds using Personal Hotspot for a tested 1ms direct link.",
                        fontSize = 12.sp,
                        fontFamily = InterFontFamily,
                        color = PureWhite.copy(alpha = 0.9f),
                        lineHeight = 17.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Button(
                            onClick = { showDoctorModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SolarOrange),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Idroid Doctor", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3 Connection Ways Card (Educational & Consumer-Friendly)
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "3 Ways to Connect",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )

                // Option 1: Hotspot (Recommended)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SolarOrange.copy(alpha = 0.08f))
                        .border(1.dp, SolarOrange.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable { showDoctorModal = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SolarOrange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(17.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Personal Hotspot", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PureWhite, fontFamily = InterFontFamily)
                            Surface(color = SolarOrange, shape = RoundedCornerShape(4.dp)) {
                                Text("Best Speed", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = PureWhite, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                        Text("1-hop direct link with 1ms latency & silky 60 FPS", fontSize = 11.5.sp, color = TextSecondary, fontFamily = InterFontFamily)
                    }
                }

                // Option 2: Home Wi-Fi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianVoid)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(PureWhite.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = PureWhite, modifier = Modifier.size(17.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Home / Office Wi-Fi", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PureWhite, fontFamily = InterFontFamily)
                        Text("Both devices share the same local Wi-Fi router", fontSize = 11.5.sp, color = TextSecondary, fontFamily = InterFontFamily)
                    }
                }

                // Option 3: USB Cable Bridge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianVoid)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(EmeraldBeacon.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = null, tint = EmeraldBeacon, modifier = Modifier.size(17.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("USB Cable Bridge", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PureWhite, fontFamily = InterFontFamily)
                        Text("Instant connection with continuous phone charging", fontSize = 11.5.sp, color = TextSecondary, fontFamily = InterFontFamily)
                    }
                }
            }
        }

        // Quick Reconnect to Last Mac
        if (savedIp.isNotBlank()) {
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onConnectRequest(savedIp, savedPort)
                        Toast.makeText(context, "Reconnecting to $savedIp...", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SolarOrange.copy(alpha = 0.14f))
                            .border(1.dp, SolarOrange.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = SolarOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reconnect to Previous Mac",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                        Text(
                            text = "$savedIp:$savedPort",
                            fontSize = 12.sp,
                            fontFamily = InterFontFamily,
                            color = TextMuted
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
        }

        // 3-Step Simple Guide Card
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "How to Connect",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )

                GuideStepRow(step = "1", text = "Launch Idroid on your Mac")
                GuideStepRow(step = "2", text = "Ensure phone & Mac share the same Wi-Fi")
                GuideStepRow(step = "3", text = "Scan QR code or click Reconnect")
            }
        }

        // Manual IP Expandable
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isManualExpanded = !isManualExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.SettingsEthernet,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Manual IP Entry (Advanced)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFontFamily,
                            color = TextSecondary
                        )
                    }
                    Icon(
                        if (isManualExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }

                if (isManualExpanded) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("Mac IP Address (e.g. 192.168.1.10)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SolarOrange,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            focusedLabelColor = SolarOrange,
                            unfocusedLabelColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val ip = manualIp.trim()
                            if (ip.isNotBlank()) {
                                val prefs = context.getSharedPreferences("idroid_config", Context.MODE_PRIVATE)
                                prefs.edit().putString("last_ip", ip).putInt("last_port", savedPort).apply()
                                onConnectRequest(ip, savedPort)
                                Toast.makeText(context, "Connecting to $ip...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SolarOrange),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Text("Connect to IP", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun GuideStepRow(
    step: String,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(SolarOrange.copy(alpha = 0.15f))
                .border(1.dp, SolarOrange.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = SolarOrange,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFontFamily
            )
        }
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 12.5.sp,
            fontFamily = InterFontFamily
        )
    }
}
