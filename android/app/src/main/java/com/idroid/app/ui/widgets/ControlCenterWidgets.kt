package com.idroid.app.ui.widgets

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
 * ControlCenterWidgets - Apple macOS / iOS Control Center aesthetic
 * adapted for Idroid's Obsidian Void & Solar Orange visual identity.
 * Replaces repetitive square boxes with organic capsules, fluid sliders,
 * and tactile circular buttons.
 */

// MARK: - 1. Top Status Chip (like "System Services recently")
@Composable
fun TopStatusChip(
    text: String = "Direct Mesh • Latency < 4ms",
    icon: ImageVector = Icons.Default.NearMe,
    iconColor: Color = ElectricBlue,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianSurfaceVariant.copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.8f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                fontSize = 11.5.sp,
                color = PureWhite,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// MARK: - 2. Control Center Capsule Pill Toggle (Wi-Fi, Bluetooth, AirDrop style)
@Composable
fun ControlCenterPillToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    activeColor: Color = SolarOrange
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f),
        label = "pill_toggle_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.7f) else ObsidianBorder,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f),
        label = "pill_border"
    )

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, borderColor),
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .height(64.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Circular badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else ObsidianSurfaceVariant)
                    .border(1.dp, if (isActive) activeColor else ObsidianBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    fontFamily = InterFontFamily,
                    color = if (isActive) activeColor else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// MARK: - 3. Square Media Player Tile (Matches 2-capsule stack height)
@Composable
fun ControlCenterMediaTile(
    title: String = "Now Playing",
    subtitle: String = "Mac Audio",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(138.dp) // Exactly 64 + 10 + 64 dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Album art / Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SolarOrange.copy(alpha = 0.14f))
                        .border(1.dp, SolarOrange.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SolarOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        fontFamily = InterFontFamily,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom: Controls Row (Prev, Play, Next)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        IdroidService.instance?.sendMediaKey("prev")
                        Toast.makeText(context, "Previous Track", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = PureWhite, modifier = Modifier.size(22.dp))
                }

                Surface(
                    color = SolarOrange,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(42.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendMediaKey("play_pause")
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = PureWhite, modifier = Modifier.size(24.dp))
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        IdroidService.instance?.sendMediaKey("next")
                        Toast.makeText(context, "Next Track", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = PureWhite, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

// MARK: - 4. Circular Tactile Control Center Button (56dp circle)
@Composable
fun ControlCenterCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeColor: Color = SolarOrange,
    size: Int = 58
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f),
        label = "circle_btn_scale"
    )

    Surface(
        color = if (isActive) activeColor else ObsidianSurface,
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) activeColor.copy(alpha = 0.8f) else ObsidianBorder
        ),
        modifier = modifier
            .scale(scale)
            .size(size.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (isActive) PureWhite else PureWhite,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// MARK: - 5. Wide Focus Pill Toggle (iOS Style)
@Composable
fun ControlCenterFocusPill(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeColor: Color = ElectricBlue
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f),
        label = "focus_pill_scale"
    )

    Surface(
        color = if (isActive) activeColor.copy(alpha = 0.15f) else ObsidianSurface,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.2.dp,
            if (isActive) activeColor else ObsidianBorder
        ),
        modifier = modifier
            .scale(scale)
            .height(58.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else ObsidianSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = PureWhite,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFontFamily,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// MARK: - 6. Continuous Control Center Slider Capsule (Sound / Display)
@Composable
fun ControlCenterSliderCard(
    title: String,
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit,
    minIcon: ImageVector,
    maxIcon: ImageVector,
    onMinClick: () -> Unit,
    onMaxClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Label & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )

                Text(
                    text = trailingLabel ?: "${(value * 100).toInt()}%",
                    fontSize = 11.5.sp,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
            }

            // Slider Track with Min / Max Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    minIcon,
                    contentDescription = "Decrease",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMinClick()
                        }
                )

                Slider(
                    value = value,
                    onValueChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onValueChange(it)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = PureWhite,
                        activeTrackColor = SolarOrange,
                        inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                )

                Icon(
                    maxIcon,
                    contentDescription = "Increase",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMaxClick()
                        }
                )
            }
        }
    }
}
