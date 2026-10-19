package com.idroid.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.idroid.app.service.IdroidService
import com.idroid.app.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TrackpadScreen - Ultra low-latency (< 5ms) virtual precision glass trackpad
 * Features:
 * - 60Hz-120Hz fluid multi-touch cursor glide with ballistic acceleration
 * - Natural 2-finger scroll gesture + dedicated right-edge scroll rail
 * - Tap to click & double tap
 * - Tactile Left, Double, and Right mouse click buttons
 * - Quick Mission Control and Spotlight shortcuts
 * - Integrated Live Keyboard with Enter/Search to Mac
 */
@Composable
fun TrackpadScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var sensitivity by remember { mutableStateOf(1.0f) }
    var keyboardText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Virtual Precision Trackpad",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )
                Text(
                    text = "Glide to steer • Tap to click • 2-finger scroll",
                    fontSize = 12.sp,
                    fontFamily = InterFontFamily,
                    color = TextSecondary
                )
            }

            Surface(
                color = SolarOrange.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SolarOrange.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(13.dp))
                    Text(
                        text = "120 FPS",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = SolarOrange
                    )
                }
            }
        }

        // Adjustable Trackpad Sensitivity Card
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(15.dp))
                        Text(
                            text = "Cursor Speed",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                    }
                    Text(
                        text = when {
                            sensitivity <= 0.75f -> "Fine Precision (${String.format("%.1f", sensitivity)}x)"
                            sensitivity >= 1.35f -> "Fast Glide (${String.format("%.1f", sensitivity)}x)"
                            else -> "Natural (${String.format("%.1f", sensitivity)}x)"
                        },
                        fontSize = 11.5.sp,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = SolarOrange
                    )
                }
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    valueRange = 0.4f..2.0f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = SolarOrange,
                        activeTrackColor = SolarOrange,
                        inactiveTrackColor = ObsidianBorder
                    ),
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                )
            }
        }

        // Glass Trackpad Surface with Natural 1:1 Physics
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .pointerInput(sensitivity) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lastX = down.position.x
                        var lastY = down.position.y
                        var totalDragDist = 0f
                        val startTime = System.currentTimeMillis()

                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes
                            if (pointers.isEmpty()) break

                            if (pointers.size >= 2) {
                                // Natural 2-finger smooth scrolling
                                val p1 = pointers[0]
                                val p2 = pointers[1]
                                if (p1.pressed && p2.pressed) {
                                    val avgDy = ((p1.position.y - p1.previousPosition.y) + (p2.position.y - p2.previousPosition.y)) / 2f
                                    if (Math.abs(avgDy) > 1.2f) {
                                        totalDragDist += Math.abs(avgDy)
                                        val scrollLines = if (avgDy > 0) -2 else 2
                                        IdroidService.instance?.sendMouseScroll(scrollLines)
                                    }
                                    p1.consume()
                                    p2.consume()
                                }
                            } else {
                                val pointer = pointers[0]
                                if (pointer.pressed) {
                                    val dx = pointer.position.x - lastX
                                    val dy = pointer.position.y - lastY
                                    val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

                                    if (dist > 0.25f) {
                                        totalDragDist += dist
                                        // Natural Apple Trackpad Physics:
                                        // Gentle organic curve: direct 1:1 at calm speeds, smooth ramp at fast flicks
                                        val accel = 1.0f + (dist * 0.032f).coerceAtMost(0.85f)
                                        val finalDx = dx * sensitivity * accel
                                        val finalDy = dy * sensitivity * accel
                                        IdroidService.instance?.sendMouseMove(finalDx, finalDy)
                                        pointer.consume()
                                    }
                                    lastX = pointer.position.x
                                    lastY = pointer.position.y
                                }
                            }
                        } while (pointers.any { it.pressed })

                        // Reliable tap-to-click: slight finger jitter (< 7.5px) within 260ms triggers click
                        val duration = System.currentTimeMillis() - startTime
                        if (totalDragDist < 7.5f && duration < 260) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendMouseClick(false)
                        }
                    }
                }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Trackpad Watermark Centerpiece
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = TextMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = "TOUCH TO STEER CURSOR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontFamily = InterFontFamily,
                        color = TextMuted.copy(alpha = 0.4f)
                    )
                }

                // Scroll Rail on Right Edge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(48.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.03f))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val scrollStep = if (dragAmount.y > 0) -3 else 3
                                IdroidService.instance?.sendMouseScroll(scrollStep)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = "Scroll Rail",
                            tint = SolarOrange.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "SCROLL",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = SolarOrange.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Tactile Left, Double, and Right Click Mouse Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TrackpadMouseButton(
                title = "Left Click",
                isPrimary = true,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendMouseClick(false)
                },
                modifier = Modifier.weight(1.1f)
            )

            TrackpadMouseButton(
                title = "Double",
                isPrimary = false,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    coroutineScope.launch {
                        IdroidService.instance?.sendMouseClick(false)
                        delay(50)
                        IdroidService.instance?.sendMouseClick(false)
                    }
                },
                modifier = Modifier.weight(0.9f)
            )

            TrackpadMouseButton(
                title = "Right Click",
                isPrimary = false,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendMouseClick(true)
                },
                modifier = Modifier.weight(1.1f)
            )
        }

        // Mission Control & Spotlight Shortcut Keys
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrackpadShortcutButton(
                icon = Icons.Default.GridView,
                title = "Mission Control",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendCommand("mission_control")
                    Toast.makeText(context, "Mission Control", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )

            TrackpadShortcutButton(
                icon = Icons.Default.Search,
                title = "Spotlight Search",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    IdroidService.instance?.sendCommand("spotlight")
                    coroutineScope.launch {
                        delay(250)
                        try {
                            focusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                    Toast.makeText(context, "Spotlight Search (Type on Keyboard)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Live Keyboard to Mac Card
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Keyboard, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Live Mac Keyboard",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                    }
                    Text(
                        text = "Types directly to active cursor",
                        fontSize = 11.sp,
                        fontFamily = InterFontFamily,
                        color = SolarOrange
                    )
                }

                // Live Real-Time Remote Input Field
                OutlinedTextField(
                    value = keyboardText,
                    onValueChange = { newText ->
                        val oldText = keyboardText
                        if (newText.length > oldText.length && newText.startsWith(oldText)) {
                            // User typed new characters: stream immediately to Mac
                            val added = newText.substring(oldText.length)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendTypeText(added)
                            keyboardText = newText
                        } else if (newText.length < oldText.length && oldText.startsWith(newText)) {
                            // User pressed backspace: send backspaces to Mac
                            val diff = oldText.length - newText.length
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            repeat(diff) {
                                IdroidService.instance?.sendKeyPress("backspace")
                            }
                            keyboardText = newText
                        } else if (newText != oldText) {
                            // Replacement, paste, or autocomplete
                            if (newText.isEmpty()) {
                                keyboardText = ""
                            } else {
                                repeat(oldText.length) {
                                    IdroidService.instance?.sendKeyPress("backspace")
                                }
                                IdroidService.instance?.sendTypeText(newText)
                                keyboardText = newText
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            text = "Type here → appears on Mac active cursor...",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = InterFontFamily
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (keyboardText.isNotEmpty()) {
                                IconButton(onClick = { keyboardText = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    IdroidService.instance?.sendKeyPress("return")
                                    keyboardText = ""
                                }
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Send Return",
                                    tint = SolarOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendKeyPress("return")
                            keyboardText = ""
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SolarOrange,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        cursorColor = SolarOrange
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                // Quick Keys Row (Return, Backspace, Space, Esc, Tab)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrackpadKeyPill(
                        label = "↵ Return",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendKeyPress("return")
                            keyboardText = ""
                        },
                        modifier = Modifier.weight(1.1f)
                    )
                    TrackpadKeyPill(
                        label = "⌫ Del",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendKeyPress("backspace")
                            if (keyboardText.isNotEmpty()) {
                                keyboardText = keyboardText.dropLast(1)
                            }
                        },
                        modifier = Modifier.weight(0.9f)
                    )
                    TrackpadKeyPill(
                        label = "␣ Space",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendKeyPress("space")
                            keyboardText += " "
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TrackpadKeyPill(
                        label = "⎋ Esc",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendKeyPress("escape")
                        },
                        modifier = Modifier.weight(0.9f)
                    )
                    TrackpadKeyPill(
                        label = "⇥ Tab",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendKeyPress("tab")
                        },
                        modifier = Modifier.weight(0.9f)
                    )
                }
            }
        }

        // Bottom padding for floating navigation bar
        Spacer(modifier = Modifier.height(84.dp))
    }
}

@Composable
private fun TrackpadMouseButton(
    title: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f),
        label = "click_btn_scale"
    )

    Surface(
        color = if (isPrimary) ObsidianSurfaceVariant else ObsidianSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPrimary) SolarOrange.copy(alpha = 0.35f) else ObsidianBorder
        ),
        modifier = modifier
            .scale(scale)
            .height(52.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isPrimary) SolarOrange else PureWhite
            )
        }
    }
}

@Composable
private fun TrackpadShortcutButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f),
        label = "shortcut_btn_scale"
    )

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
            .scale(scale)
            .height(46.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp,
                color = PureWhite
            )
        }
    }
}

@Composable
private fun TrackpadKeyPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(stiffness = 600f, dampingRatio = 0.7f),
        label = "key_pill_scale"
    )

    Surface(
        color = if (isPressed) SolarOrange.copy(alpha = 0.18f) else ObsidianSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPressed) SolarOrange.copy(alpha = 0.5f) else ObsidianBorder
        ),
        modifier = modifier
            .scale(scale)
            .height(38.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = PureWhite
            )
        }
    }
}

