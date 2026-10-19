package com.idroid.app.ui.widgets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.service.IdroidService
import com.idroid.app.ui.*

/**
 * Interactive Widgets for Idroid Android Companion App:
 * 1. Live Telemetry Continuity Card (Battery gauge, pulse beacon, direct latency)
 * 2. Tactile Split Volume & Brightness Pill Sliders
 * 3. Media Remote Island (Play/Pause, Next, Prev, Mute)
 * 4. Quick macOS Command Deck (Mission Control, Launchpad, Lock, Sleep, Screenshot)
 * 5. Live Shared Clipboard Sync Widget
 */

// MARK: - 1. Live Telemetry Continuity Card
@Composable
fun HeroTelemetryWidget(
    macName: String,
    connType: String,
    macBattery: Int,
    macIsCharging: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_beacon")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_alpha"
    )

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            SolarOrange.copy(alpha = 0.08f),
                            Color.Transparent,
                            ElectricBlue.copy(alpha = 0.06f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Top Row: Mac Details & Beacon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SolarOrange.copy(alpha = 0.14f))
                                .border(1.dp, SolarOrange.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LaptopMac,
                                contentDescription = "Mac",
                                tint = SolarOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = macName.ifBlank { "Apple Mac" },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = InterFontFamily,
                                    color = PureWhite
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldBeacon.copy(alpha = beaconAlpha))
                                )
                            }
                            Text(
                                text = "Direct Mesh • $connType",
                                fontSize = 12.sp,
                                fontFamily = InterFontFamily,
                                color = TextSecondary
                            )
                        }
                    }

                    // Direct Latency Pill
                    Surface(
                        color = EmeraldBeacon.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBeacon.copy(alpha = 0.35f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = EmeraldBeacon,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "< 4ms",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = InterFontFamily,
                                color = EmeraldBeacon
                            )
                        }
                    }
                }

                // Battery Progress Bar & Status
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (macIsCharging) "Charging on AC Power" else "MacBook Battery",
                            fontSize = 12.sp,
                            fontFamily = InterFontFamily,
                            color = TextMuted
                        )
                        Text(
                            text = "$macBattery% ${if (macIsCharging) "⚡" else ""}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = if (macBattery > 20) PureWhite else CrimsonDanger
                        )
                    }

                    // Meter track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (macBattery.coerceIn(0, 100) / 100f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(SolarOrange, EmeraldBeacon)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

// MARK: - 2. Tactile Split Control Pills
@Composable
fun SplitControlPill(
    icon: ImageVector,
    title: String,
    minusLabel: String,
    plusLabel: String,
    onMinusClick: () -> Unit,
    onPlusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Half: [-]
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMinusClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Decrease",
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = minusLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFontFamily,
                        color = PureWhite
                    )
                }
            }

            // Center Label & Divider
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                        Text(title, fontSize = 10.5.sp, color = TextMuted, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Right Half: [+]
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlusClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = SolarOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = plusLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = SolarOrange
                    )
                }
            }
        }
    }
}

// MARK: - 3. Media Remote Island
@Composable
fun MediaControllerIsland(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendMediaKey("prev")
                    Toast.makeText(context, "Previous Track", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = PureWhite, modifier = Modifier.size(24.dp))
            }

            // Play / Pause (Solar Orange Hero Button)
            Surface(
                color = SolarOrange,
                shape = CircleShape,
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        IdroidService.instance?.sendMediaKey("play_pause")
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = PureWhite, modifier = Modifier.size(26.dp))
                }
            }

            // Next
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendMediaKey("next")
                    Toast.makeText(context, "Next Track", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = PureWhite, modifier = Modifier.size(24.dp))
            }

            // Mute
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendMediaKey("mute")
                    Toast.makeText(context, "Mute Toggled", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.VolumeMute, contentDescription = "Mute", tint = TextSecondary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// MARK: - 4. Quick macOS Commands Grid
@Composable
fun QuickActionsDeck(
    onNavigateToTrackpad: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Ecosystem Quick Controls",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = InterFontFamily,
            color = PureWhite
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickDeckButton(
                icon = Icons.Default.GridView,
                title = "Mission Control",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendCommand("mission_control")
                    Toast.makeText(context, "Mission Control", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )

            QuickDeckButton(
                icon = Icons.Default.Apps,
                title = "Launchpad",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendCommand("launch_app", "app", "Launchpad")
                },
                modifier = Modifier.weight(1f)
            )

            QuickDeckButton(
                icon = Icons.Default.Lock,
                title = "Lock Mac",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendCommand("lock_screen")
                    Toast.makeText(context, "Mac Screen Locked", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickDeckButton(
                icon = Icons.Default.PowerSettingsNew,
                title = "Sleep Display",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendCommand("sleep_display")
                    Toast.makeText(context, "Display Sleep", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )

            QuickDeckButton(
                icon = Icons.Default.Screenshot,
                title = "Mac Screenshot",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendCommand("screenshot")
                    Toast.makeText(context, "Screenshot captured on Mac", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )

            QuickDeckButton(
                icon = Icons.Default.TouchApp,
                title = "Trackpad",
                highlighted = true,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToTrackpad()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun QuickDeckButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f),
        label = "btn_scale"
    )

    Surface(
        color = if (highlighted) SolarOrange.copy(alpha = 0.12f) else ObsidianSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (highlighted) SolarOrange.copy(alpha = 0.4f) else ObsidianBorder
        ),
        modifier = modifier
            .scale(scale)
            .height(72.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = if (highlighted) SolarOrange else PureWhite,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFontFamily,
                color = if (highlighted) SolarOrange else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// MARK: - 5. Live Shared Clipboard Widget
@Composable
fun ClipboardMiniWidget(
    sharedClipboardText: String,
    onNavigateToClipboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var copiedState by remember { mutableStateOf(false) }

    LaunchedEffect(copiedState) {
        if (copiedState) {
            kotlinx.coroutines.delay(2000L)
            copiedState = false
        }
    }

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = SolarOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Mac Clipboard Live",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = PureWhite
                    )
                }

                Surface(
                    color = if (copiedState) EmeraldBeacon.copy(alpha = 0.2f) else SolarOrange.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (copiedState) EmeraldBeacon else SolarOrange.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.clickable {
                        if (sharedClipboardText.isNotBlank()) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Idroid Mac Clipboard", sharedClipboardText))
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            copiedState = true
                            Toast.makeText(context, "Copied to phone!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (copiedState) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = if (copiedState) EmeraldBeacon else SolarOrange,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (copiedState) "Copied!" else "Copy to Phone",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = if (copiedState) EmeraldBeacon else SolarOrange
                        )
                    }
                }
            }

            // Clipboard preview content
            Surface(
                color = ObsidianSurfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = sharedClipboardText.ifBlank { "Clipboard is empty on Mac..." },
                    fontSize = 12.5.sp,
                    fontFamily = InterFontFamily,
                    color = if (sharedClipboardText.isNotBlank()) PureWhite else TextMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
