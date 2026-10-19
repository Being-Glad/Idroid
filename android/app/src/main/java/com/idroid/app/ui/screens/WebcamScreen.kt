package com.idroid.app.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.model.MacAppItem
import com.idroid.app.service.CompanionBridge
import com.idroid.app.service.IdroidService
import com.idroid.app.camera.CameraStreamManager
import com.idroid.app.ui.*

enum class StudioTab {
    CAMERA,
    MAC_APPS
}

/**
 * WebcamScreen - Advanced Continuity Camera Studio & Mac Apps Hub
 * Features:
 * - Thumb-zone Segmented Switcher (Camera Stream & Mac Apps) positioned directly above the bottom nav bar.
 * - Clean & neat UI: camera options/settings are ONLY displayed when the camera stream is enabled.
 * - Full Mac App launcher & remote controller accessible with one touch.
 */
@Composable
fun WebcamScreen(
    isStreaming: Boolean,
    selectedLens: String,
    onToggleStream: () -> Unit,
    onSelectLens: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val liveFps by CameraStreamManager.liveFps
    val liveBitrate by CameraStreamManager.liveBitrateMbps
    val statusMessage by CameraStreamManager.statusMessage

    var activeTab by remember { mutableStateOf(StudioTab.CAMERA) }
    var selectedQuality by remember { mutableStateOf("1080p 60fps") }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var isMicMuted by remember { mutableStateOf(false) }
    var isFocusLocked by remember { mutableStateOf(false) }

    val macApps = CompanionBridge.macApps
    val isConnected by CompanionBridge.isConnected
    var appSearchQuery by remember { mutableStateOf("") }

    val allApps = macApps.toList()
    val displayApps = remember(allApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) {
            allApps
        } else {
            allApps.filter { it.name.contains(appSearchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (activeTab == StudioTab.CAMERA) {
                        if (isStreaming) "Continuity Camera" else "Advanced Studio"
                    } else "Mac Applications",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )
                Text(
                    text = if (activeTab == StudioTab.CAMERA) {
                        if (isStreaming) "Streaming live to macOS display" else "Camera feed & Mac app controller"
                    } else "Launch apps on Mac display with one touch",
                    fontSize = 12.sp,
                    fontFamily = InterFontFamily,
                    color = if (isStreaming && activeTab == StudioTab.CAMERA) EmeraldBeacon else TextSecondary
                )
            }

            if (activeTab == StudioTab.CAMERA) {
                // Stream Status Badge
                Surface(
                    color = if (isStreaming) EmeraldBeacon.copy(alpha = 0.15f) else ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isStreaming) EmeraldBeacon.copy(alpha = 0.45f) else ObsidianBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isStreaming) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldBeacon)
                            )
                        }
                        Text(
                            text = if (isStreaming) {
                                if (liveFps > 0) "LIVE • ${liveFps} FPS" else "CONNECTING..."
                            } else "Standby",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = if (isStreaming) EmeraldBeacon else TextMuted
                        )
                    }
                }
            } else {
                Surface(
                    color = ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        IdroidService.instance?.sendRaw("""{"type":"command","action":"get_apps"}""")
                        Toast.makeText(context, "Scanning installed Mac apps...", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(14.dp))
                        Text("Refresh", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFontFamily, color = PureWhite)
                    }
                }
            }
        }

        // Pinned Segmented Switcher (Camera Stream & Mac Apps) - TOP PLACED!
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StudioSegmentButton(
                    title = "Camera Stream",
                    icon = Icons.Default.Videocam,
                    badge = if (isStreaming) "LIVE" else null,
                    badgeColor = EmeraldBeacon,
                    isSelected = activeTab == StudioTab.CAMERA,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        activeTab = StudioTab.CAMERA
                    },
                    modifier = Modifier.weight(1f)
                )

                StudioSegmentButton(
                    title = "Mac Apps",
                    icon = Icons.Default.Apps,
                    badge = if (displayApps.isNotEmpty()) "${displayApps.size}" else null,
                    badgeColor = SolarOrange,
                    isSelected = activeTab == StudioTab.MAC_APPS,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        activeTab = StudioTab.MAC_APPS
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Main Tab Content Area
        Crossfade(
            targetState = activeTab,
            label = "studio_tab_content",
            modifier = Modifier.fillMaxSize()
        ) { tab ->
            when (tab) {
                StudioTab.CAMERA -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {

                    // Viewfinder Surface
                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isStreaming) EmeraldBeacon.copy(alpha = 0.7f) else ObsidianBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isStreaming) EmeraldBeacon.copy(alpha = 0.16f)
                                            else Color.White.copy(alpha = 0.05f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = if (isStreaming) EmeraldBeacon else TextMuted,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Text(
                                    text = if (isStreaming) {
                                        if (liveFps > 0) "STREAMING TO MAC • ${liveFps} FPS" else statusMessage
                                    } else "CAMERA IN STANDBY",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    fontSize = 12.sp,
                                    color = if (isStreaming) EmeraldBeacon else TextMuted
                                )

                                Text(
                                    text = if (isStreaming) {
                                        if (liveBitrate > 0) "${String.format("%.1f", liveBitrate)} Mbps • Lens: $selectedLens • $selectedQuality"
                                        else "Hardware H.264 Encoder Active • $selectedQuality"
                                    } else "Broadcast into FaceTime, Zoom, OBS, and Meet",
                                    fontFamily = InterFontFamily,
                                    fontSize = 11.5.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    // Primary Stream Toggle Button
                    val buttonColor by animateColorAsState(
                        targetValue = if (isStreaming) CrimsonDanger else SolarOrange,
                        label = "webcam_btn_color"
                    )

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleStream()
                            Toast.makeText(
                                context,
                                if (!isStreaming) "Wireless camera stream started!" else "Camera stream stopped.",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isStreaming) "Stop Camera Stream" else "Start Wireless Camera",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // ADVANCED CAMERA SETTINGS: ONLY REVEALED WHEN CAMERA IS ACTIVE
                    AnimatedVisibility(
                        visible = isStreaming,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Section: Camera Optics
                            Surface(
                                color = ObsidianSurface,
                                shape = RoundedCornerShape(18.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Camera Optics",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = InterFontFamily,
                                            color = PureWhite
                                        )
                                        Text(
                                            text = selectedLens,
                                            fontSize = 11.sp,
                                            fontFamily = InterFontFamily,
                                            color = SolarOrange,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("0.6x UltraWide", "1.0x Primary", "Front Selfie").forEach { lens ->
                                            val isSelected = selectedLens.contains(lens.take(4)) || selectedLens == lens
                                            StudioPillOption(
                                                title = lens,
                                                isSelected = isSelected,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onSelectLens(lens)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Section: Stream Quality & Resolution
                            Surface(
                                color = ObsidianSurface,
                                shape = RoundedCornerShape(18.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Stream Quality",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = InterFontFamily,
                                        color = PureWhite
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("1080p 60fps", "4K Ultra", "Low Latency").forEach { quality ->
                                            StudioPillOption(
                                                title = quality,
                                                isSelected = selectedQuality == quality,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedQuality = quality
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Section: Studio Live Controls
                            Surface(
                                color = ObsidianSurface,
                                shape = RoundedCornerShape(18.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Studio Live Controls",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = InterFontFamily,
                                        color = PureWhite
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StudioToggleAction(
                                            title = if (isTorchEnabled) "Torch On" else "Torch Off",
                                            icon = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                            isActive = isTorchEnabled,
                                            onClick = {
                                                isTorchEnabled = !isTorchEnabled
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )

                                        StudioToggleAction(
                                            title = if (isMicMuted) "Mic Muted" else "Mic Live",
                                            icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                            isActive = !isMicMuted,
                                            activeColor = EmeraldBeacon,
                                            onClick = {
                                                isMicMuted = !isMicMuted
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )

                                        StudioToggleAction(
                                            title = if (isFocusLocked) "Lock AE/AF" else "Auto Focus",
                                            icon = Icons.Default.CenterFocusStrong,
                                            isActive = isFocusLocked,
                                            onClick = {
                                                isFocusLocked = !isFocusLocked
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Space so the content scrolls fully above the thumb-zone buttons
                    Spacer(modifier = Modifier.height(110.dp))
                }
            }

                StudioTab.MAC_APPS -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                    // Modern Search Box
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        placeholder = {
                            Text(
                                "Search ${displayApps.size} Mac applications...",
                                fontSize = 13.sp,
                                fontFamily = InterFontFamily,
                                color = TextMuted
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (appSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { appSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite,
                            focusedContainerColor = ObsidianSurface,
                            unfocusedContainerColor = ObsidianSurface,
                            focusedBorderColor = SolarOrange,
                            unfocusedBorderColor = ObsidianBorder
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    // Count
                    Text(
                        text = "${displayApps.size} Mac application${if (displayApps.size != 1) "s" else ""} ready to launch",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted,
                        fontFamily = InterFontFamily
                    )

                    // Applications Grid (with padding for the thumb-zone switcher above nav bar)
                    if (displayApps.isEmpty()) {
                        Surface(
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = SolarOrange,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if (isConnected) "No matching applications found" else "Connect over Wi-Fi to load Mac apps",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite,
                                    fontFamily = InterFontFamily
                                )
                                Text(
                                    text = if (isConnected) "Tap Refresh to rescan your Mac's /Applications folder." else "Connect to your Mac to browse and launch its authentic software.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontFamily = InterFontFamily
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 110.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayApps, key = { it.name }) { app ->
                                StudioMacAppCard(
                                    app = app,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        IdroidService.instance?.sendCommand("launch_app", "app", app.name)
                                        Toast.makeText(context, "Opening ${app.name} on Mac...", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun StudioSegmentButton(
    title: String,
    icon: ImageVector,
    badge: String?,
    badgeColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) SolarOrange.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) SolarOrange.copy(alpha = 0.5f) else Color.Transparent
        ),
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) SolarOrange else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.5.sp,
                color = if (isSelected) PureWhite else TextSecondary
            )
            if (badge != null) {
                Spacer(Modifier.width(5.dp))
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioPillOption(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f),
        label = "studio_pill_scale"
    )

    Surface(
        color = if (isSelected) SolarOrange.copy(alpha = 0.18f) else ObsidianSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) SolarOrange else ObsidianBorder
        ),
        modifier = modifier
            .scale(scale)
            .height(42.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontFamily = InterFontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) PureWhite else TextSecondary
            )
        }
    }
}

@Composable
private fun StudioToggleAction(
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color = SolarOrange,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isActive) activeColor.copy(alpha = 0.16f) else ObsidianSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) activeColor.copy(alpha = 0.5f) else ObsidianBorder
        ),
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isActive) activeColor else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontFamily = InterFontFamily,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) PureWhite else TextMuted
            )
        }
    }
}

@Composable
private fun StudioMacAppCard(
    app: MacAppItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f),
        label = "studio_app_card_scale"
    )

    val iconBitmap = remember(app.icon) {
        if (app.icon.isNotBlank()) {
            try {
                val clean = if (app.icon.contains("base64,")) app.icon.substringAfter("base64,") else app.icon
                val decoded = Base64.decode(clean, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .height(78.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.name,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SolarOrange.copy(alpha = 0.12f))
                        .border(1.dp, SolarOrange.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.name.take(1),
                        fontWeight = FontWeight.Bold,
                        color = SolarOrange,
                        fontSize = 17.sp,
                        fontFamily = InterFontFamily
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = app.name,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFontFamily,
                    color = PureWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }

            Icon(
                Icons.Default.Launch,
                contentDescription = null,
                tint = TextMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
