package com.idroid.app.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-performance, tactile UI components built with Design-Motion-Principles:
 * - Emil Kowalski: Speed (<180ms), scale(0.96) press feedback, zero UI lag.
 * - Jakub Krehel: Layered ambient shadows, translucent blur surfaces, optical centering.
 */

@Composable
fun FluidCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    backgroundColor: Color = ObsidianSurface,
    borderColor: Color = ObsidianBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1f,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.75f),
        label = "fluid_card_scale"
    )

    Surface(
        color = backgroundColor,
        shape = shape,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = modifier
            .scale(scale)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

@Composable
fun FluidHeroCapsule(
    macName: String,
    connType: String,
    macBattery: Int,
    macIsCharging: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(28.dp),
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
                            ElectricBlue.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(SolarOrange.copy(alpha = 0.25f), SolarOrange.copy(alpha = 0.08f))
                                )
                            )
                            .border(1.dp, SolarOrange.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LaptopMac,
                            contentDescription = null,
                            tint = SolarOrange,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = macName.ifBlank { "MacBook Pro" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldBeacon)
                            )
                            Text(
                                text = "Connected $connType",
                                fontSize = 12.sp,
                                fontFamily = InterFontFamily,
                                color = EmeraldBeacon
                            )
                        }
                    }
                }

                // Battery Pill
                Surface(
                    color = Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "$macBattery%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                        if (macIsCharging) {
                            Text(text = "⚡", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Continuous Interactive Slider Capsule (Volume & Brightness)
 */
@Composable
fun FluidContinuousSlider(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    onMinusClick: () -> Unit,
    onPlusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier.height(64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Half: Dim / Decrease
            val minusInteraction = remember { MutableInteractionSource() }
            val isMinusPressed by minusInteraction.collectIsPressedAsState()
            val minusScale by animateFloatAsState(
                targetValue = if (isMinusPressed) 0.94f else 1f,
                animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
                label = "minus_scale"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .scale(minusScale)
                    .clickable(
                        interactionSource = minusInteraction,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFontFamily,
                        color = PureWhite
                    )
                }
            }

            // Minimal separator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(ObsidianBorder)
            )

            // Right Half: Bright / Increase
            val plusInteraction = remember { MutableInteractionSource() }
            val isPlusPressed by plusInteraction.collectIsPressedAsState()
            val plusScale by animateFloatAsState(
                targetValue = if (isPlusPressed) 0.94f else 1f,
                animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
                label = "plus_scale"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .scale(plusScale)
                    .clickable(
                        interactionSource = plusInteraction,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlusClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = "Increase",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Organic Media Control Island
 */
@Composable
fun FluidMediaCapsule(
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMuteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FluidIconButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous Track",
                tint = PureWhite,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPrev()
                }
            )

            // Primary Play / Pause Action
            val playInteraction = remember { MutableInteractionSource() }
            val isPlayPressed by playInteraction.collectIsPressedAsState()
            val playScale by animateFloatAsState(
                targetValue = if (isPlayPressed) 0.92f else 1f,
                animationSpec = spring(stiffness = 450f, dampingRatio = 0.75f),
                label = "play_scale"
            )

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .scale(playScale)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(SolarOrange, Color(0xFFFF8533))
                        )
                    )
                    .clickable(
                        interactionSource = playInteraction,
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = PureWhite,
                    modifier = Modifier.size(28.dp)
                )
            }

            FluidIconButton(
                icon = Icons.Default.SkipNext,
                contentDescription = "Next Track",
                tint = PureWhite,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNext()
                }
            )

            FluidIconButton(
                icon = Icons.Default.VolumeMute,
                contentDescription = "Mute Audio",
                tint = TextSecondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMuteToggle()
                }
            )
        }
    }
}

/**
 * Tactile Micro-Action Tile with Emil Kowalski 0.96 scale on tap
 */
@Composable
fun FluidShortcutTile(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 450f, dampingRatio = 0.75f),
        label = "tile_scale"
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        interactionSource = interaction,
        color = ObsidianSurface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
            .height(88.dp)
            .scale(scale)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFontFamily,
                color = PureWhite
            )
        }
    }
}

@Composable
fun FluidIconButton(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color = PureWhite,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.75f),
        label = "icon_scale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
