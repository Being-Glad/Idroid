package com.idroid.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.service.CompanionBridge
import com.idroid.app.service.IdroidService
import com.idroid.app.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacWifiBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ssid by CompanionBridge.macWifiSsid
    val isPowerOn by CompanionBridge.macWifiPower
    val isFetching by CompanionBridge.isFetchingMacWifi
    val macName by CompanionBridge.macName
    val isConnectedToMac by CompanionBridge.isConnected
    val connType by CompanionBridge.connectionType

    // Auto-fetch on sheet open
    LaunchedEffect(Unit) {
        if (isConnectedToMac) {
            IdroidService.instance?.requestMacWifiStatus()
        }
    }

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
                            .background(SolarOrange.copy(alpha = 0.15f))
                            .border(1.dp, SolarOrange.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPowerOn) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = "Wi-Fi",
                            tint = SolarOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Wi-Fi",
                            style = IdroidTypography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (isConnectedToMac) macName else "Connect to Mac first",
                            style = IdroidTypography.bodyMedium.copy(
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (isConnectedToMac) {
                            IdroidService.instance?.requestMacWifiStatus()
                        }
                    },
                    enabled = !isFetching && isConnectedToMac
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isFetching) SolarOrange else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Wi-Fi Power Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurfaceVariant)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Wi-Fi",
                            style = IdroidTypography.bodyLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (isPowerOn) "Turned On" else "Turned Off",
                            style = IdroidTypography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = if (isPowerOn) EmeraldBeacon else TextMuted
                            )
                        )
                    }

                    Switch(
                        checked = isPowerOn,
                        onCheckedChange = { checked ->
                            IdroidService.instance?.setMacWifiPower(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = SolarOrange,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = ObsidianBorder
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Current SSID Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (ssid.isNotBlank()) EmeraldBeacon.copy(alpha = 0.08f) else ObsidianSurfaceVariant)
                    .border(
                        1.dp,
                        if (ssid.isNotBlank()) EmeraldBeacon.copy(alpha = 0.35f) else ObsidianBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (ssid.isNotBlank()) EmeraldBeacon.copy(alpha = 0.15f) else ObsidianBorder.copy(alpha = 0.4f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Network",
                            tint = if (ssid.isNotBlank()) EmeraldBeacon else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = if (ssid.isNotBlank()) ssid else if (isPowerOn) "Not Connected" else "Wi-Fi is Off",
                            style = IdroidTypography.bodyLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (ssid.isNotBlank()) "Connected to this network" else if (isPowerOn) "No network connected" else "Turn on Wi-Fi to connect",
                            style = IdroidTypography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = if (ssid.isNotBlank()) EmeraldBeacon else TextMuted
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Transport info
            val linkLabel = remember(connType) {
                val lower = connType.lowercase()
                when {
                    "direct" in lower || "wi-fi" in lower || "wifi" in lower -> "Wireless"
                    "usb" in lower -> "USB Cable"
                    else -> connType.replaceFirstChar { it.uppercase() }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurfaceVariant)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Link to Mac",
                        style = IdroidTypography.bodySmall.copy(fontSize = 12.sp, color = TextSecondary)
                    )
                    Text(
                        text = linkLabel,
                        style = IdroidTypography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SolarOrange
                        )
                    )
                }
            }
        }
    }
}
