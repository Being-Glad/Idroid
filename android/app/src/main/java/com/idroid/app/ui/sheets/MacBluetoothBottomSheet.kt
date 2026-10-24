package com.idroid.app.ui.sheets

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.service.CompanionBridge
import com.idroid.app.service.IdroidService
import com.idroid.app.service.MacBtDevice
import com.idroid.app.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacBluetoothBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val devices = CompanionBridge.macBtDevices
    val isFetching by CompanionBridge.isFetchingMacBt
    val connectingAddress by CompanionBridge.connectingBtAddress
    val macName by CompanionBridge.macName
    val isConnectedToMac by CompanionBridge.isConnected

    // Auto-fetch on sheet open
    LaunchedEffect(Unit) {
        if (isConnectedToMac) {
            IdroidService.instance?.requestMacBluetoothDevices()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ObsidianBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue.copy(alpha = 0.15f))
                            .border(1.dp, ElectricBlue.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth",
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Mac Accessories",
                            style = IdroidTypography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (isConnectedToMac) "Manage paired devices on $macName" else "Connect to Mac first",
                            style = IdroidTypography.bodyMedium.copy(
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        )
                    }
                }

                // Refresh Button
                IconButton(
                    onClick = {
                        if (isConnectedToMac) {
                            IdroidService.instance?.requestMacBluetoothDevices()
                        }
                    },
                    enabled = !isFetching && isConnectedToMac
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isFetching) SolarOrange else TextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .then(if (isFetching) Modifier.rotate(spinAngle) else Modifier)
                    )
                }
            }

            // Device List
            if (isFetching && devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = SolarOrange,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Scanning Mac paired accessories...",
                            style = IdroidTypography.bodyMedium.copy(fontSize = 12.sp, color = TextSecondary)
                        )
                    }
                }
            } else if (!isConnectedToMac) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Phone is currently disconnected from Mac.\nConnect to Mac to view and toggle Bluetooth accessories.",
                        style = IdroidTypography.bodyMedium.copy(fontSize = 13.sp, color = TextMuted),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No paired Bluetooth accessories found on Mac.\nPair earbuds, headphones, or mice on your Mac once to manage them here.",
                        style = IdroidTypography.bodyMedium.copy(fontSize = 13.sp, color = TextMuted),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(devices) { dev ->
                        MacBtDeviceItem(
                            device = dev,
                            isConnecting = connectingAddress == dev.address,
                            onConnect = {
                                IdroidService.instance?.connectMacBluetoothDevice(dev.address)
                            },
                            onDisconnect = {
                                IdroidService.instance?.disconnectMacBluetoothDevice(dev.address)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Phone Native Bluetooth Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurfaceVariant)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        try {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Phone Bluetooth Settings",
                            style = IdroidTypography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MacBtDeviceItem(
    device: MacBtDevice,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isAudio = device.name.contains("Buds", ignoreCase = true) ||
            device.name.contains("AirPods", ignoreCase = true) ||
            device.name.contains("Headset", ignoreCase = true) ||
            device.name.contains("Audio", ignoreCase = true) ||
            device.name.contains("Noise", ignoreCase = true) ||
            device.name.contains("OnePlus", ignoreCase = true)

    val icon = if (isAudio) Icons.Default.Headphones else Icons.Default.Bluetooth

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (device.connected) EmeraldBeacon.copy(alpha = 0.07f) else ObsidianSurfaceVariant)
            .border(
                width = 1.dp,
                color = if (device.connected) EmeraldBeacon.copy(alpha = 0.35f) else ObsidianBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (device.connected) EmeraldBeacon.copy(alpha = 0.15f) else ObsidianBorder.copy(alpha = 0.4f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = device.name,
                        tint = if (device.connected) EmeraldBeacon else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = IdroidTypography.bodyLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        ),
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (device.connected) EmeraldBeacon else TextMuted)
                        )
                        Text(
                            text = if (device.connected) "Connected to Mac" else "Not connected",
                            style = IdroidTypography.bodyMedium.copy(
                                fontSize = 11.sp,
                                color = if (device.connected) EmeraldBeacon else TextMuted
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Action Button
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = SolarOrange,
                    strokeWidth = 2.dp
                )
            } else if (device.connected) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonDanger.copy(alpha = 0.12f),
                        contentColor = CrimsonDanger
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Disconnect",
                        style = IdroidTypography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            } else {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldBeacon,
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Connect",
                        style = IdroidTypography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}
