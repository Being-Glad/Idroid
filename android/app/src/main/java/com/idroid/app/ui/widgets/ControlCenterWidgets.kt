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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.graphicsLayer
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlCenterPillToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    activeColor: Color = SolarOrange,
    onLongClick: (() -> Unit)? = null
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
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = onLongClick?.let {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Circular badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else ObsidianSurfaceVariant)
                    .border(1.dp, if (isActive) activeColor else ObsidianBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = PureWhite,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    fontFamily = InterFontFamily,
                    color = if (isActive) activeColor else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Expand",
                tint = TextMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// MARK: - 3. Square / Horizontal Media Player Tile (Matches 2-capsule stack or full-width)
@Composable
fun ControlCenterMediaTile(
    title: String = "Now Playing",
    subtitle: String = "Mac Audio",
    modifier: Modifier = Modifier,
    isCompactBar: Boolean = false
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    if (isCompactBar) {
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SolarOrange.copy(alpha = 0.14f))
                            .border(1.dp, SolarOrange.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = SolarOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendMediaKey("prev")
                            Toast.makeText(context, "Previous Track", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = PureWhite, modifier = Modifier.size(20.dp))
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendMediaKey("play_pause")
                        },
                        color = SolarOrange,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = PureWhite, modifier = Modifier.size(22.dp))
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendMediaKey("next")
                            Toast.makeText(context, "Next Track", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = PureWhite, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        return
    }

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
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        IdroidService.instance?.sendMediaKey("play_pause")
                    },
                    color = SolarOrange,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
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

// MARK: - 4. Circular Tactile Control Center Button (macOS native circular trigger)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlCenterCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeColor: Color = SolarOrange,
    isEditMode: Boolean = false,
    jiggleAngle: Float = 0f,
    onLongClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    size: Int = 54
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isEditMode) 0.88f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f),
        label = "circle_btn_scale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(scale)
                .graphicsLayer {
                    if (isEditMode) {
                        rotationZ = jiggleAngle * 1.1f
                        translationY = jiggleAngle * 0.4f
                    }
                }
        ) {
            Surface(
                color = if (isActive) activeColor else ObsidianSurface,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    if (isEditMode) SolarOrange.copy(alpha = 0.8f)
                    else if (isActive) activeColor.copy(alpha = 0.85f)
                    else ObsidianBorder
                ),
                modifier = Modifier
                    .size(size.dp)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            if (isEditMode) return@combinedClickable
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        },
                        onLongClick = onLongClick?.let {
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                it()
                            }
                        }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = PureWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Top-left (-) removal badge in edit mode (Apple style)
            if (isEditMode && onRemove != null) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRemove()
                    },
                    color = Color(0xFFE53935),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, ObsidianVoid),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-3).dp, y = (-3).dp)
                        .size(19.dp)
                        .clip(CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Remove",
                            tint = PureWhite,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }

        // Label underneath circle
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = InterFontFamily,
            color = if (isActive) PureWhite else TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// MARK: - 4b. macOS Control Center Horizontal Capsule Bar (Authentic macOS design)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlCenterMacCapsule(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isActive: Boolean = false,
    activeColor: Color = SolarOrange,
    isEditMode: Boolean = false,
    jiggleAngle: Float = 0f,
    onLongClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isEditMode) 0.95f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.75f),
        label = "mac_capsule_scale"
    )

    val surfaceColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.16f) else ObsidianSurface,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f),
        label = "mac_capsule_surface"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isEditMode -> SolarOrange.copy(alpha = 0.7f)
            isActive -> activeColor.copy(alpha = 0.65f)
            else -> ObsidianBorder
        },
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f),
        label = "mac_capsule_border"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .graphicsLayer {
                if (isEditMode) {
                    rotationZ = jiggleAngle * 0.8f
                    translationY = jiggleAngle * 0.4f
                }
            }
    ) {
        Surface(
            color = surfaceColor,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.2.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (isEditMode) return@combinedClickable
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                    onLongClick = onLongClick?.let {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            it()
                        }
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // macOS circular icon badge
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isActive) activeColor else ObsidianSurfaceVariant)
                        .border(1.dp, if (isActive) activeColor else ObsidianBorder.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFontFamily,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = 10.sp,
                            fontFamily = InterFontFamily,
                            color = if (isActive) activeColor else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Top-left (-) removal badge in edit mode (Apple style)
        if (isEditMode && onRemove != null) {
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRemove()
                },
                color = Color(0xFFE53935),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.2.dp, ObsidianVoid),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-3).dp, y = (-3).dp)
                    .size(19.dp)
                    .clip(CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Remove",
                        tint = PureWhite,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

// Deprecated alias for backwards compatibility
@Composable
fun ControlCenterSquircleTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeColor: Color = SolarOrange,
    isEditMode: Boolean = false,
    jiggleAngle: Float = 0f,
    onLongClick: (() -> Unit)? = null
) {
    ControlCenterMacCapsule(
        icon = icon,
        label = label,
        onClick = onClick,
        modifier = modifier,
        isActive = isActive,
        activeColor = activeColor,
        isEditMode = isEditMode,
        jiggleAngle = jiggleAngle,
        onLongClick = onLongClick
    )
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
                        .size(24.dp)
                        .clip(CircleShape)
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
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onMaxClick()
                        }
                )
            }
        }
    }
}

// MARK: - 7. macOS Vertical Control Center Slider Card (Sound / Display)
@Composable
fun ControlCenterVerticalSliderCard(
    title: String,
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit,
    icon: ImageVector,
    accentColor: Color = SolarOrange,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null
) {
    val haptic = LocalHapticFeedback.current
    var sliderHeightPx by remember { mutableStateOf(0f) }

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(154.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Label & Status Percentage
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

            // Vertical Capsule Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                    .onSizeChanged { sliderHeightPx = it.height.toFloat() }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (sliderHeightPx > 0f) {
                                val newFrac = (1f - (offset.y / sliderHeightPx)).coerceIn(0f, 1f)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onValueChange(newFrac)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, _ ->
                            change.consume()
                            if (sliderHeightPx > 0f) {
                                val newFrac = (1f - (change.position.y / sliderHeightPx)).coerceIn(0f, 1f)
                                onValueChange(newFrac)
                            }
                        }
                    }
            ) {
                // Bottom-up filled level
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(value.coerceIn(0.01f, 1f))
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    accentColor,
                                    accentColor.copy(alpha = 0.82f)
                                )
                            )
                        )
                )

                // Bottom Anchor Icon inside capsule
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val isIconOverFill = value > 0.18f
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isIconOverFill) Color(0xFF16171D) else PureWhite.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// MARK: - 8. Modern macOS Horizontal Control Center Sound Slider Card
@Composable
fun ControlCenterModernSoundCard(
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit,
    onAirPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var trackWidthPx by remember { mutableStateOf(0f) }

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: "Sound"
            Text(
                text = "Sound",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFontFamily,
                color = PureWhite
            )

            // Slider Row: Headphone Icon + Fluid White Slider + Circular AirPlay Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (value <= 0.02f) Icons.Default.VolumeMute else Icons.Default.Headphones,
                    contentDescription = "Sound Output",
                    tint = PureWhite.copy(alpha = 0.85f),
                    modifier = Modifier.size(19.dp)
                )

                // Fluid macOS Apple-style track
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .onSizeChanged { trackWidthPx = it.width.toFloat() }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (trackWidthPx > 0f) {
                                    val newFrac = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onValueChange(newFrac)
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                if (trackWidthPx > 0f) {
                                    val newFrac = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                                    onValueChange(newFrac)
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Inactive track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.5.dp)
                            .clip(RoundedCornerShape(3.25.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                    )

                    // Active pure white filled bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.coerceIn(0.01f, 1f))
                            .height(6.5.dp)
                            .clip(RoundedCornerShape(3.25.dp))
                            .background(PureWhite)
                    )
                }

                // AirPlay / Audio Route Circular Button (matches macOS Control Center)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PureWhite)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAirPlayClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Airplay,
                        contentDescription = "Audio Routing",
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

// MARK: - 9. Modern macOS Horizontal Control Center Display Slider Card
@Composable
fun ControlCenterModernDisplayCard(
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var trackWidthPx by remember { mutableStateOf(0f) }

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: "Display"
            Text(
                text = "Display",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFontFamily,
                color = PureWhite
            )

            // Slider Row: Min Sun + Fluid White Slider + Max Sun
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BrightnessLow,
                    contentDescription = "Min Brightness",
                    tint = PureWhite.copy(alpha = 0.85f),
                    modifier = Modifier.size(19.dp)
                )

                // Fluid macOS Apple-style track
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .onSizeChanged { trackWidthPx = it.width.toFloat() }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (trackWidthPx > 0f) {
                                    val newFrac = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onValueChange(newFrac)
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                if (trackWidthPx > 0f) {
                                    val newFrac = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                                    onValueChange(newFrac)
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Inactive track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.5.dp)
                            .clip(RoundedCornerShape(3.25.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                    )

                    // Active pure white filled bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.coerceIn(0.01f, 1f))
                            .height(6.5.dp)
                            .clip(RoundedCornerShape(3.25.dp))
                            .background(PureWhite)
                    )
                }

                Icon(
                    imageVector = Icons.Default.BrightnessHigh,
                    contentDescription = "Max Brightness",
                    tint = PureWhite.copy(alpha = 0.85f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

// MARK: - 10. Reels & Shorts Remote Control Card
@Composable
fun ControlCenterReelsCard(
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onLike: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isLikedAnim by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (isLikedAnim) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "heartPulse"
    )

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Subtle, minimal header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "REELS & SHORTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        fontFamily = InterFontFamily,
                        color = TextMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldBeacon)
                    )
                    Text(
                        text = "Mac Browser",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = InterFontFamily,
                        color = TextMuted.copy(alpha = 0.75f)
                    )
                }
            }

            // Balanced, unified 4-segment control bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Prev Reel
                val reelShape = RoundedCornerShape(12.dp)
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPrev()
                    },
                    color = ObsidianSurfaceVariant,
                    shape = reelShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(reelShape)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Prev Reel",
                            tint = PureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "Prev",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PureWhite,
                            fontFamily = InterFontFamily
                        )
                    }
                }

                // 2. Next Reel (Primary navigation target)
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNext()
                    },
                    color = Color(0xFF26262A),
                    shape = reelShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(44.dp)
                        .clip(reelShape)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Next Reel",
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "Next",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PureWhite,
                            fontFamily = InterFontFamily
                        )
                    }
                }

                // 3. Like (Heart)
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isLikedAnim = true
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(180)
                            isLikedAnim = false
                        }
                        onLike()
                    },
                    color = CrimsonDanger.copy(alpha = 0.12f),
                    shape = reelShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(reelShape)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Like",
                            tint = CrimsonDanger,
                            modifier = Modifier
                                .size(16.dp)
                                .scale(heartScale)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Like",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CrimsonDanger,
                            fontFamily = InterFontFamily
                        )
                    }
                }

                // 4. Play / Pause
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    },
                    color = ObsidianSurfaceVariant,
                    shape = reelShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .weight(0.9f)
                        .height(44.dp)
                        .clip(reelShape)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = PureWhite.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}



