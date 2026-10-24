package com.idroid.app.ui.screens

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.idroid.app.service.IdroidService
import com.idroid.app.ui.*
import com.idroid.app.ui.sheets.MacBluetoothBottomSheet
import com.idroid.app.ui.sheets.MacWifiBottomSheet
import com.idroid.app.ui.widgets.*
import com.idroid.app.utils.BluetoothHelper
import com.idroid.app.utils.DashboardPreferences
import com.idroid.app.utils.LocationHelper

/**
 * Metadata holder for a single control — built once per item in the grid,
 * then rendered as either a circle or a pill depending on row position.
 */
private data class ControlMeta(
    val icon: ImageVector,
    val label: String,
    val subtitle: String,
    val isActive: Boolean,
    val activeColor: androidx.compose.ui.graphics.Color,
    val onClick: () -> Unit
)

/**
 * OverviewScreen — macOS Control Center style with dynamic fluid drag & drop reordering.
 */
@Composable
fun OverviewScreen(
    macName: String,
    macBattery: Int,
    macIsCharging: Boolean,
    connType: String,
    sharedClipboardText: String,
    onOpenSettings: () -> Unit,
    onNavigateToTrackpad: () -> Unit,
    onNavigateToClipboard: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density  = LocalDensity.current
    val haptic   = LocalHapticFeedback.current

    // Mac state
    val liveMacVolume     = com.idroid.app.service.CompanionBridge.macVolume.value
    val liveMacBrightness = com.idroid.app.service.CompanionBridge.macBrightness.value
    var soundLevel    by remember(liveMacVolume)     { mutableStateOf(liveMacVolume) }
    var displayLevel  by remember(liveMacBrightness) { mutableStateOf(liveMacBrightness) }
    var btStatus      by remember { mutableStateOf(BluetoothHelper.getStatus(context)) }
    var isLocationActive by remember { mutableStateOf(LocationHelper.isLocationEnabled(context)) }
    var isMicMuted    by remember { mutableStateOf(false) }
    var isAudioMuted  by remember { mutableStateOf(false) }

    // Dashboard state
    var enabledIds       by remember { mutableStateOf(DashboardPreferences.getEnabledControlIds(context)) }
    var allControlOrder  by remember { mutableStateOf(DashboardPreferences.getUtilityOrder(context, enabledIds)) }
    var sectionOrder     by remember { mutableStateOf(DashboardPreferences.getSectionOrder(context)) }

    var showCustomizeModal by remember { mutableStateOf(false) }
    var showMacBtSheet     by remember { mutableStateOf(false) }
    var showMacWifiSheet   by remember { mutableStateOf(false) }
    var isEditMode         by remember { mutableStateOf(false) }

    val isConnectedToMac by com.idroid.app.service.CompanionBridge.isConnected
    val macWifiSsid      by com.idroid.app.service.CompanionBridge.macWifiSsid
    val macWifiPower     by com.idroid.app.service.CompanionBridge.macWifiPower

    val wifiTileSubtitle = remember(isConnectedToMac, macWifiPower, macWifiSsid) {
        if (!isConnectedToMac) "Not Connected"
        else if (!macWifiPower) "Off"
        else if (macWifiSsid.isNotBlank()) macWifiSsid
        else "On"
    }

    // Drag-to-reorder state
    var draggedId         by remember { mutableStateOf<String?>(null) }
    var dragFingerPos     by remember { mutableStateOf(Offset.Zero) }
    var hoveredTargetId   by remember { mutableStateOf<String?>(null) }
    var totalGridWidth    by remember { mutableFloatStateOf(0f) }

    val colGapPx  = with(density) { 10.dp.toPx() }
    val rowGapPx  = with(density) { 14.dp.toPx() }
    val hCirclePx = with(density) { 82.dp.toPx() }
    val hPillPx   = with(density) { 54.dp.toPx() }

    BackHandler(enabled = isEditMode) {
        isEditMode = false
        draggedId  = null
        hoveredTargetId = null
        DashboardPreferences.setUtilityOrder(context, allControlOrder)
        DashboardPreferences.setSectionOrder(context, sectionOrder)
    }

    // Dynamic dual-phase jiggle animation for authentic living feel in edit mode
    val infiniteTransition = rememberInfiniteTransition(label = "jiggle")
    val jiggleAngle1 by infiniteTransition.animateFloat(
        initialValue = -1.8f,
        targetValue  =  1.8f,
        animationSpec = infiniteRepeatable(
            animation  = tween(130, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jiggle_angle1"
    )
    val jiggleAngle2 by infiniteTransition.animateFloat(
        initialValue =  1.8f,
        targetValue  = -1.8f,
        animationSpec = infiniteRepeatable(
            animation  = tween(145, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jiggle_angle2"
    )

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                btStatus         = BluetoothHelper.getStatus(context)
                isLocationActive = LocationHelper.isLocationEnabled(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            addAction(android.location.LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction("android.location.MODE_CHANGED")
        }
        context.registerReceiver(receiver, filter)
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    if (showCustomizeModal) {
        CustomizeDashboardModal(
            enabledIds = enabledIds,
            onEnabledIdsChanged = { updated ->
                enabledIds      = updated
                allControlOrder = DashboardPreferences.getUtilityOrder(context, updated)
                sectionOrder    = DashboardPreferences.getSectionOrder(context)
            },
            onDismiss = { showCustomizeModal = false }
        )
    }

    // ── Main scroll container ───────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── Header ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isEditMode) "Rearrange Controls" else "Control Center",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = if (isEditMode) SolarOrange else PureWhite
                    )
                    if (isEditMode) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SolarOrange)
                        )
                    }
                }
                val cleanConnType = remember(connType) {
                    val lower = connType.lowercase()
                    when {
                        "direct" in lower || "wi-fi" in lower || "wifi" in lower -> "Wireless"
                        "usb" in lower -> "USB"
                        else -> connType
                    }
                }
                Text(
                    text = if (isEditMode) "Hold & drag to move  •  Tap (−) to remove"
                           else "$cleanConnType • Low Latency",
                    fontSize = 11.5.sp,
                    fontFamily = InterFontFamily,
                    color = TextSecondary
                )
            }

            if (isEditMode) {
                val doneShape = RoundedCornerShape(14.dp)
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isEditMode = false
                        draggedId  = null
                        hoveredTargetId = null
                        DashboardPreferences.setUtilityOrder(context, allControlOrder)
                        DashboardPreferences.setSectionOrder(context, sectionOrder)
                    },
                    color = SolarOrange,
                    shape = doneShape,
                    modifier = Modifier.clip(doneShape)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Icon(Icons.Default.Check, null, tint = PureWhite, modifier = Modifier.size(14.dp))
                        Text("Done", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily, color = PureWhite)
                    }
                }
            } else {
                val addShape = RoundedCornerShape(12.dp)
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showCustomizeModal = true
                    },
                    color = ObsidianSurfaceVariant.copy(alpha = 0.8f),
                    shape = addShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.clip(addShape)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = SolarOrange, modifier = Modifier.size(14.dp))
                        Text("Add Controls", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFontFamily, color = PureWhite)
                    }
                }
            }
        }

        // ── Render Dashboard Sections in Dynamic User-Defined Order ─────────────
        sectionOrder.forEachIndexed { secIdx, sectionKey ->
            val canMoveUp   = isEditMode && secIdx > 0
            val canMoveDown = isEditMode && secIdx < sectionOrder.size - 1
            val onMoveUp: () -> Unit = {
                val newOrder = sectionOrder.toMutableList()
                val temp = newOrder[secIdx]
                newOrder[secIdx] = newOrder[secIdx - 1]
                newOrder[secIdx - 1] = temp
                sectionOrder = newOrder
                DashboardPreferences.setSectionOrder(context, newOrder)
            }
            val onMoveDown: () -> Unit = {
                val newOrder = sectionOrder.toMutableList()
                val temp = newOrder[secIdx]
                newOrder[secIdx] = newOrder[secIdx + 1]
                newOrder[secIdx + 1] = temp
                sectionOrder = newOrder
                DashboardPreferences.setSectionOrder(context, newOrder)
            }

            when (sectionKey) {
                // 1. Connectivity & Media Bento Pod
                "connectivity_media" -> {
                    val hasWifi        = enabledIds.contains("direct_wifi")
                    val hasBluetooth   = enabledIds.contains("bluetooth")
                    val hasNowPlaying  = enabledIds.contains("now_playing")
                    val numPills       = (if (hasWifi) 1 else 0) + (if (hasBluetooth) 1 else 0)

                    if (numPills > 0 || hasNowPlaying) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isEditMode) {
                                SectionEditHeader(
                                    title = "Connectivity & Media",
                                    canMoveUp = canMoveUp,
                                    canMoveDown = canMoveDown,
                                    onMoveUp = onMoveUp,
                                    onMoveDown = onMoveDown
                                )
                            }

                            when {
                                numPills == 2 && hasNowPlaying -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            EditableModuleContainer(
                                                isEditMode = isEditMode,
                                                jiggleAngle = jiggleAngle1,
                                                onRemove = {
                                                    enabledIds = enabledIds - "direct_wifi"
                                                    DashboardPreferences.setControlEnabled(context, "direct_wifi", false)
                                                }
                                            ) {
                                                ControlCenterPillToggle(
                                                    icon        = Icons.Default.Wifi,
                                                    title       = "Wi-Fi",
                                                    subtitle    = wifiTileSubtitle,
                                                    isActive    = isConnectedToMac && macWifiPower,
                                                    activeColor = SolarOrange,
                                                    onClick     = { if (!isEditMode) showMacWifiSheet = true },
                                                    onLongClick = { isEditMode = true }
                                                )
                                            }
                                            EditableModuleContainer(
                                                isEditMode = isEditMode,
                                                jiggleAngle = jiggleAngle2,
                                                onRemove = {
                                                    enabledIds = enabledIds - "bluetooth"
                                                    DashboardPreferences.setControlEnabled(context, "bluetooth", false)
                                                }
                                            ) {
                                                ControlCenterPillToggle(
                                                    icon = if (btStatus.isConnected) Icons.Default.BluetoothConnected
                                                           else Icons.Default.Bluetooth,
                                                    title    = "Bluetooth",
                                                    subtitle = btStatus.statusText,
                                                    isActive = btStatus.isEnabled,
                                                    activeColor = if (btStatus.isConnected) EmeraldBeacon else ElectricBlue,
                                                    onClick  = { if (!isEditMode) showMacBtSheet = true },
                                                    onLongClick = { isEditMode = true }
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            EditableModuleContainer(
                                                isEditMode = isEditMode,
                                                jiggleAngle = jiggleAngle1,
                                                onRemove = {
                                                    enabledIds = enabledIds - "now_playing"
                                                    DashboardPreferences.setControlEnabled(context, "now_playing", false)
                                                }
                                            ) {
                                                ControlCenterMediaTile(title = "Now Playing", subtitle = "Mac Audio")
                                            }
                                        }
                                    }
                                }

                                numPills == 2 && !hasNowPlaying -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            EditableModuleContainer(
                                                isEditMode = isEditMode,
                                                jiggleAngle = jiggleAngle1,
                                                onRemove = {
                                                    enabledIds = enabledIds - "direct_wifi"
                                                    DashboardPreferences.setControlEnabled(context, "direct_wifi", false)
                                                }
                                            ) {
                                                ControlCenterPillToggle(
                                                    icon        = Icons.Default.Wifi,
                                                    title       = "Wi-Fi",
                                                    subtitle    = wifiTileSubtitle,
                                                    isActive    = isConnectedToMac && macWifiPower,
                                                    activeColor = SolarOrange,
                                                    onClick     = { if (!isEditMode) showMacWifiSheet = true },
                                                    onLongClick = { isEditMode = true }
                                                )
                                            }
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            EditableModuleContainer(
                                                isEditMode = isEditMode,
                                                jiggleAngle = jiggleAngle2,
                                                onRemove = {
                                                    enabledIds = enabledIds - "bluetooth"
                                                    DashboardPreferences.setControlEnabled(context, "bluetooth", false)
                                                }
                                            ) {
                                                ControlCenterPillToggle(
                                                    icon = if (btStatus.isConnected) Icons.Default.BluetoothConnected
                                                           else Icons.Default.Bluetooth,
                                                    title    = "Bluetooth",
                                                    subtitle = btStatus.statusText,
                                                    isActive = btStatus.isEnabled,
                                                    activeColor = if (btStatus.isConnected) EmeraldBeacon else ElectricBlue,
                                                    onClick  = { if (!isEditMode) showMacBtSheet = true },
                                                    onLongClick = { isEditMode = true }
                                                )
                                            }
                                        }
                                    }
                                }

                                numPills == 1 && hasNowPlaying -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val isWifi = hasWifi
                                        EditableModuleContainer(
                                            isEditMode = isEditMode,
                                            jiggleAngle = if (isWifi) jiggleAngle1 else jiggleAngle2,
                                            onRemove = {
                                                val id = if (isWifi) "direct_wifi" else "bluetooth"
                                                enabledIds = enabledIds - id
                                                DashboardPreferences.setControlEnabled(context, id, false)
                                            }
                                        ) {
                                            if (isWifi) {
                                                ControlCenterPillToggle(
                                                    icon        = Icons.Default.Wifi,
                                                    title       = "Wi-Fi",
                                                    subtitle    = wifiTileSubtitle,
                                                    isActive    = isConnectedToMac && macWifiPower,
                                                    activeColor = SolarOrange,
                                                    onClick     = { if (!isEditMode) showMacWifiSheet = true },
                                                    onLongClick = { isEditMode = true }
                                                )
                                            } else {
                                                ControlCenterPillToggle(
                                                    icon = if (btStatus.isConnected) Icons.Default.BluetoothConnected
                                                           else Icons.Default.Bluetooth,
                                                    title    = "Bluetooth",
                                                    subtitle = btStatus.statusText,
                                                    isActive = btStatus.isEnabled,
                                                    activeColor = if (btStatus.isConnected) EmeraldBeacon else ElectricBlue,
                                                    onClick  = { if (!isEditMode) showMacBtSheet = true },
                                                    onLongClick = { isEditMode = true }
                                                )
                                            }
                                        }
                                        EditableModuleContainer(
                                            isEditMode = isEditMode,
                                            jiggleAngle = jiggleAngle1,
                                            onRemove = {
                                                enabledIds = enabledIds - "now_playing"
                                                DashboardPreferences.setControlEnabled(context, "now_playing", false)
                                            }
                                        ) {
                                            ControlCenterMediaTile(
                                                title = "Now Playing",
                                                subtitle = "Mac Audio",
                                                isCompactBar = true
                                            )
                                        }
                                    }
                                }

                                numPills == 1 && !hasNowPlaying -> {
                                    val isWifi = hasWifi
                                    EditableModuleContainer(
                                        isEditMode = isEditMode,
                                        jiggleAngle = if (isWifi) jiggleAngle1 else jiggleAngle2,
                                        onRemove = {
                                            val id = if (isWifi) "direct_wifi" else "bluetooth"
                                            enabledIds = enabledIds - id
                                            DashboardPreferences.setControlEnabled(context, id, false)
                                        }
                                    ) {
                                        if (isWifi) {
                                            ControlCenterPillToggle(
                                                icon        = Icons.Default.Wifi,
                                                title       = "Wi-Fi",
                                                subtitle    = wifiTileSubtitle,
                                                isActive    = isConnectedToMac && macWifiPower,
                                                activeColor = SolarOrange,
                                                onClick     = { if (!isEditMode) showMacWifiSheet = true },
                                                onLongClick = { isEditMode = true }
                                            )
                                        } else {
                                            ControlCenterPillToggle(
                                                icon = if (btStatus.isConnected) Icons.Default.BluetoothConnected
                                                       else Icons.Default.Bluetooth,
                                                title    = "Bluetooth",
                                                subtitle = btStatus.statusText,
                                                isActive = btStatus.isEnabled,
                                                activeColor = if (btStatus.isConnected) EmeraldBeacon else ElectricBlue,
                                                onClick  = { if (!isEditMode) showMacBtSheet = true },
                                                onLongClick = { isEditMode = true }
                                            )
                                        }
                                    }
                                }

                                numPills == 0 && hasNowPlaying -> {
                                    EditableModuleContainer(
                                        isEditMode = isEditMode,
                                        jiggleAngle = jiggleAngle1,
                                        onRemove = {
                                            enabledIds = enabledIds - "now_playing"
                                            DashboardPreferences.setControlEnabled(context, "now_playing", false)
                                        }
                                    ) {
                                        ControlCenterMediaTile(
                                            title = "Now Playing",
                                            subtitle = "Mac Audio",
                                            isCompactBar = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Quick Action Controls Grid (Circles & One UI 9 Pills)
                "quick_controls" -> {
                    val allActiveOrder = allControlOrder.filter { enabledIds.contains(it) }
                    val currentActiveOrder by rememberUpdatedState(allActiveOrder)
                    val currentControlOrder by rememberUpdatedState(allControlOrder)
                    val currentGridWidth by rememberUpdatedState(totalGridWidth)

                    if (allActiveOrder.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isEditMode) {
                                SectionEditHeader(
                                    title = "Quick Actions Grid",
                                    canMoveUp = canMoveUp,
                                    canMoveDown = canMoveDown,
                                    onMoveUp = onMoveUp,
                                    onMoveDown = onMoveDown
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coords ->
                                        totalGridWidth = coords.size.width.toFloat()
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { localOffset ->
                                                val active = currentActiveOrder
                                                val width = currentGridWidth
                                                val startIdx = findSlotAtPosition(
                                                    localOffset, active, width,
                                                    colGapPx, rowGapPx, hCirclePx, hPillPx
                                                )
                                                if (startIdx != null && startIdx in active.indices) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    isEditMode    = true
                                                    draggedId     = active[startIdx]
                                                    dragFingerPos = localOffset
                                                    hoveredTargetId = null
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val did = draggedId ?: return@detectDragGesturesAfterLongPress
                                                dragFingerPos += dragAmount

                                                val active = currentActiveOrder
                                                val width = currentGridWidth
                                                val targetIdx = findSlotAtPosition(
                                                    dragFingerPos, active, width,
                                                    colGapPx, rowGapPx, hCirclePx, hPillPx
                                                )
                                                if (targetIdx != null && targetIdx in active.indices) {
                                                    val targetId = active[targetIdx]
                                                    if (targetId != did) {
                                                        hoveredTargetId = targetId
                                                        val allList = currentControlOrder.toMutableList()
                                                        val fromIdx = allList.indexOf(did)
                                                        val toIdx   = allList.indexOf(targetId)
                                                        if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                                                            allList.removeAt(fromIdx)
                                                            allList.add(toIdx, did)
                                                            allControlOrder = allList
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggedId       = null
                                                hoveredTargetId = null
                                                DashboardPreferences.setUtilityOrder(context, allControlOrder)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                            onDragCancel = {
                                                draggedId       = null
                                                hoveredTargetId = null
                                            }
                                        )
                                    }
                            ) {
                                val activeChunks = allActiveOrder.chunked(4)
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    activeChunks.forEachIndexed { rowIdx, rowIds ->
                                        val isLastRow = rowIdx == activeChunks.size - 1
                                        val usePill   = isLastRow && rowIds.size <= 2

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = if (usePill) Alignment.CenterVertically else Alignment.Top
                                        ) {
                                            rowIds.forEachIndexed { colIdx, id ->
                                                key(id) {
                                                    val globalIdx  = allActiveOrder.indexOf(id)
                                                    val isDragging = draggedId == id
                                                    val isHovered  = hoveredTargetId == id && !isDragging
                                                    val jiggle     = if (globalIdx % 2 == 0) jiggleAngle1 else jiggleAngle2
                                                    val macLoc     = com.idroid.app.service.CompanionBridge.macLocationEnabled.value

                                                    val (slotCenter, _, _) = getSlotMetrics(
                                                        globalIdx, allActiveOrder, totalGridWidth,
                                                        colGapPx, rowGapPx, hCirclePx, hPillPx
                                                    )

                                                    val meta: ControlMeta? = when (id) {
                                                        "mic_mute" -> ControlMeta(
                                                            icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                                            label = "Mute Mic",
                                                            subtitle = if (isMicMuted) "Muted" else "Active",
                                                            isActive = isMicMuted,
                                                            activeColor = CrimsonDanger,
                                                            onClick = {
                                                                isMicMuted = !isMicMuted
                                                                IdroidService.instance?.sendCommand("mic_mute")
                                                                Toast.makeText(context, if (isMicMuted) "Mic Muted" else "Mic Active", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        "lock_mac" -> ControlMeta(
                                                            icon = Icons.Default.Lock,
                                                            label = "Lock Mac",
                                                            subtitle = "Lock screen",
                                                            isActive = false,
                                                            activeColor = SolarOrange,
                                                            onClick = {
                                                                IdroidService.instance?.sendCommand("lock_screen")
                                                                Toast.makeText(context, "Mac Screen Locked", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        "sleep_display" -> ControlMeta(
                                                            icon = Icons.Default.PowerSettingsNew,
                                                            label = "Sleep",
                                                            subtitle = "Display sleep",
                                                            isActive = false,
                                                            activeColor = CrimsonDanger,
                                                            onClick = {
                                                                IdroidService.instance?.sendCommand("sleep_display")
                                                                Toast.makeText(context, "Display Sleep", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        "show_desktop" -> ControlMeta(
                                                            icon = Icons.Default.DesktopWindows,
                                                            label = "Desktop",
                                                            subtitle = "Hide windows",
                                                            isActive = false,
                                                            activeColor = ElectricBlue,
                                                            onClick = {
                                                                IdroidService.instance?.sendCommand("show_desktop")
                                                                Toast.makeText(context, "Show Desktop", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        "phone_location" -> ControlMeta(
                                                            icon = if (isLocationActive || macLoc) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                                            label = "Location",
                                                            subtitle = if (isLocationActive || macLoc) "Active" else "Disabled",
                                                            isActive = isLocationActive || macLoc,
                                                            activeColor = EmeraldBeacon,
                                                            onClick = {
                                                                LocationHelper.toggleLocation(context)
                                                                isLocationActive = LocationHelper.isLocationEnabled(context)
                                                                IdroidService.instance?.sendCommand("toggle_mac_location")
                                                                Toast.makeText(context, "Location toggled", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        "audio_mute" -> ControlMeta(
                                                            icon = if (isAudioMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                                            label = "Mute",
                                                            subtitle = if (isAudioMuted) "Muted" else "Unmuted",
                                                            isActive = isAudioMuted,
                                                            activeColor = SolarOrange,
                                                            onClick = {
                                                                isAudioMuted = !isAudioMuted
                                                                IdroidService.instance?.sendCommand("mute")
                                                                Toast.makeText(context, if (isAudioMuted) "Audio Muted" else "Audio Active", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        "dark_mode" -> ControlMeta(
                                                            icon = Icons.Default.DarkMode,
                                                            label = "Dark Mode",
                                                            subtitle = "Toggle appearance",
                                                            isActive = false,
                                                            activeColor = SolarOrange,
                                                            onClick = {
                                                                IdroidService.instance?.sendCommand("toggle_dark_mode")
                                                                Toast.makeText(context, "Dark Mode Toggled", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        "screenshot" -> ControlMeta(
                                                            icon = Icons.Default.Screenshot,
                                                            label = "Screenshot",
                                                            subtitle = "Capture screen",
                                                            isActive = false,
                                                            activeColor = ElectricBlue,
                                                            onClick = {
                                                                IdroidService.instance?.sendCommand("screenshot")
                                                                Toast.makeText(context, "Screenshot Captured", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        "mission_control" -> ControlMeta(
                                                            icon = Icons.Default.GridView,
                                                            label = "Mission Ctrl",
                                                            subtitle = "All spaces",
                                                            isActive = false,
                                                            activeColor = ElectricBlue,
                                                            onClick = {
                                                                IdroidService.instance?.sendCommand("mission_control")
                                                                Toast.makeText(context, "Mission Control", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        "trackpad" -> ControlMeta(
                                                            icon = Icons.Default.TouchApp,
                                                            label = "Trackpad",
                                                            subtitle = "Remote trackpad",
                                                            isActive = true,
                                                            activeColor = SolarOrange,
                                                            onClick = { if (!isEditMode) onNavigateToTrackpad() }
                                                        )
                                                        "clipboard_button" -> ControlMeta(
                                                            icon = Icons.Default.ContentPaste,
                                                            label = "Clipboard",
                                                            subtitle = "Mac clipboard",
                                                            isActive = sharedClipboardText.isNotBlank(),
                                                            activeColor = ElectricBlue,
                                                            onClick = { if (!isEditMode) onNavigateToClipboard() }
                                                        )
                                                        "mac_apps" -> ControlMeta(
                                                            icon = Icons.Default.Apps,
                                                            label = "Apps",
                                                            subtitle = "App launcher",
                                                            isActive = true,
                                                            activeColor = SolarOrange,
                                                            onClick = { if (!isEditMode) onNavigateToAdvanced() }
                                                        )
                                                        "presentation_clicker" -> ControlMeta(
                                                            icon = Icons.Default.ArrowForward,
                                                            label = "Next Slide",
                                                            subtitle = "Presentation",
                                                            isActive = false,
                                                            activeColor = SolarOrange,
                                                            onClick = {
                                                                IdroidService.instance?.sendCommand("presentation_next")
                                                                Toast.makeText(context, "Slide Advanced", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        "spotlight" -> ControlMeta(
                                                            icon = Icons.Default.Search,
                                                            label = "Spotlight",
                                                            subtitle = "Mac search",
                                                            isActive = false,
                                                            activeColor = ElectricBlue,
                                                            onClick = {
                                                                IdroidService.instance?.sendCommand("spotlight")
                                                                Toast.makeText(context, "Spotlight Search", Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        else -> null
                                                    }

                                                    val onRemove: () -> Unit = {
                                                        val updated = enabledIds - id
                                                        enabledIds      = updated
                                                        allControlOrder = allControlOrder.filter { it != id }
                                                        DashboardPreferences.setControlEnabled(context, id, false)
                                                    }

                                                    if (meta != null) {
                                                        Box(
                                                            modifier = Modifier.weight(1f),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isDragging) {
                                                                val silhouetteShape = if (usePill) RoundedCornerShape(18.dp) else CircleShape
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(if (usePill) 54.dp else 54.dp)
                                                                        .then(if (usePill) Modifier.fillMaxWidth().height(54.dp) else Modifier)
                                                                        .clip(silhouetteShape)
                                                                        .background(androidx.compose.ui.graphics.Color(0xFF141416))
                                                                        .border(
                                                                            1.dp,
                                                                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f),
                                                                            silhouetteShape
                                                                        )
                                                                )
                                                            }

                                                            ReorderableGridItem(
                                                                id            = id,
                                                                slotCenter    = slotCenter,
                                                                isDragging    = isDragging,
                                                                isHovered     = isHovered,
                                                                dragFingerPos = dragFingerPos,
                                                                isEditMode    = isEditMode,
                                                                jiggleAngle   = jiggle,
                                                                usePill       = usePill,
                                                                modifier      = Modifier.fillMaxWidth()
                                                            ) {
                                                                if (usePill) {
                                                                    ControlCenterMacCapsule(
                                                                        icon        = meta.icon,
                                                                        label       = meta.label,
                                                                        subtitle    = meta.subtitle,
                                                                        isActive    = meta.isActive,
                                                                        activeColor = meta.activeColor,
                                                                        isEditMode  = isEditMode,
                                                                        jiggleAngle = 0f,
                                                                        onRemove    = onRemove,
                                                                        onClick     = meta.onClick
                                                                    )
                                                                } else {
                                                                    ControlCenterCircleButton(
                                                                        icon        = meta.icon,
                                                                        label       = meta.label,
                                                                        isActive    = meta.isActive,
                                                                        activeColor = meta.activeColor,
                                                                        isEditMode  = isEditMode,
                                                                        jiggleAngle = 0f,
                                                                        size        = 54,
                                                                        onRemove    = onRemove,
                                                                        onClick     = meta.onClick
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
                            }
                        }
                    }
                }

                // 3. Hardware Bento Platter (Sound & Display Twin Sliders)
                "hardware_sliders" -> {
                    val hasSoundSlider   = enabledIds.contains("sound_slider")
                    val hasDisplaySlider = enabledIds.contains("display_slider")

                    if (hasSoundSlider || hasDisplaySlider) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isEditMode) {
                                SectionEditHeader(
                                    title = "Sound & Display",
                                    canMoveUp = canMoveUp,
                                    canMoveDown = canMoveDown,
                                    onMoveUp = onMoveUp,
                                    onMoveDown = onMoveDown
                                )
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (hasSoundSlider) {
                                    EditableModuleContainer(
                                        isEditMode = isEditMode,
                                        jiggleAngle = jiggleAngle1,
                                        onRemove = {
                                            enabledIds = enabledIds - "sound_slider"
                                            DashboardPreferences.setControlEnabled(context, "sound_slider", false)
                                        }
                                    ) {
                                        ControlCenterModernSoundCard(
                                            value = soundLevel,
                                            onValueChange = { newLevel ->
                                                soundLevel = newLevel
                                                IdroidService.instance?.setVolume((newLevel * 100).toInt())
                                            },
                                            onAirPlayClick = {
                                                showMacBtSheet = true
                                            }
                                        )
                                    }
                                }

                                if (hasDisplaySlider) {
                                    EditableModuleContainer(
                                        isEditMode = isEditMode,
                                        jiggleAngle = jiggleAngle2,
                                        onRemove = {
                                            enabledIds = enabledIds - "display_slider"
                                            DashboardPreferences.setControlEnabled(context, "display_slider", false)
                                        }
                                    ) {
                                        ControlCenterModernDisplayCard(
                                            value = displayLevel,
                                            onValueChange = { newLevel ->
                                                displayLevel = newLevel
                                                IdroidService.instance?.setBrightness(newLevel)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


                // 4. Mac Live Clipboard Mini Card
                "clipboard_card" -> {
                    if (enabledIds.contains("clipboard_card")) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isEditMode) {
                                SectionEditHeader(
                                    title = "Live Clipboard",
                                    canMoveUp = canMoveUp,
                                    canMoveDown = canMoveDown,
                                    onMoveUp = onMoveUp,
                                    onMoveDown = onMoveDown
                                )
                            }

                            EditableModuleContainer(
                                isEditMode = isEditMode,
                                jiggleAngle = jiggleAngle1,
                                onRemove = {
                                    enabledIds = enabledIds - "clipboard_card"
                                    DashboardPreferences.setControlEnabled(context, "clipboard_card", false)
                                }
                            ) {
                                ClipboardMiniWidget(
                                    sharedClipboardText  = sharedClipboardText,
                                    onNavigateToClipboard = onNavigateToClipboard
                                )
                            }
                        }
                    }
                }
            }
        }

        // Space for floating nav pill
        Spacer(modifier = Modifier.height(84.dp))
    }

    if (showMacBtSheet) {
        MacBluetoothBottomSheet(onDismiss = { showMacBtSheet = false })
    }
    if (showMacWifiSheet) {
        MacWifiBottomSheet(onDismiss = { showMacWifiSheet = false })
    }
}

/**
 * Universal container for non-grid dashboard modules (Direct Wi-Fi, Bluetooth, Now Playing, Sliders, Clipboard)
 * to support Apple-style (-) removal badges and jiggle motion during Edit Mode.
 */
@Composable
private fun EditableModuleContainer(
    isEditMode: Boolean,
    jiggleAngle: Float,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier.graphicsLayer {
            if (isEditMode) {
                rotationZ = jiggleAngle
                translationY = kotlin.math.abs(jiggleAngle) * 0.3f
            }
        }
    ) {
        content()

        if (isEditMode && onRemove != null) {
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRemove()
                },
                color = androidx.compose.ui.graphics.Color(0xFFE53935),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.2.dp, ObsidianVoid),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .size(21.dp)
                    .zIndex(10f)
                    .clip(CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Remove",
                        tint = PureWhite,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Header displayed above each section in Edit Mode allowing the user to move
 * the entire section up or down in the Dashboard layout.
 */
@Composable
private fun SectionEditHeader(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(SolarOrange)
            )
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFontFamily,
                letterSpacing = 0.8.sp,
                color = SolarOrange
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Move Up button
            val arrowShape = RoundedCornerShape(8.dp)
            Surface(
                onClick = {
                    if (canMoveUp) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMoveUp()
                    }
                },
                enabled = canMoveUp,
                color = if (canMoveUp) ObsidianSurfaceVariant else ObsidianSurfaceVariant.copy(alpha = 0.3f),
                shape = arrowShape,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (canMoveUp) ObsidianBorder else ObsidianBorder.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .size(width = 34.dp, height = 26.dp)
                    .clip(arrowShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Section Up",
                        tint = if (canMoveUp) PureWhite else TextSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Move Down button
            Surface(
                onClick = {
                    if (canMoveDown) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMoveDown()
                    }
                },
                enabled = canMoveDown,
                color = if (canMoveDown) ObsidianSurfaceVariant else ObsidianSurfaceVariant.copy(alpha = 0.3f),
                shape = arrowShape,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (canMoveDown) ObsidianBorder else ObsidianBorder.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .size(width = 34.dp, height = 26.dp)
                    .clip(arrowShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Section Down",
                        tint = if (canMoveDown) PureWhite else TextSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dynamic Fluid Motion & Physics Engine for Control Grid Reordering
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReorderableGridItem(
    id: String,
    slotCenter: Offset,
    isDragging: Boolean,
    isHovered: Boolean,
    dragFingerPos: Offset,
    isEditMode: Boolean,
    jiggleAngle: Float,
    usePill: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var prevCenter by remember { mutableStateOf<Offset?>(null) }
    val animDx = remember { Animatable(0f) }
    val animDy = remember { Animatable(0f) }

    // Smooth gliding spring animation when displaced by another button
    LaunchedEffect(slotCenter) {
        if (!isDragging && prevCenter != null && prevCenter != slotCenter && slotCenter != Offset.Zero && prevCenter != Offset.Zero) {
            val deltaX = prevCenter!!.x - slotCenter.x
            val deltaY = prevCenter!!.y - slotCenter.y
            animDx.snapTo(deltaX)
            animDy.snapTo(deltaY)
            launch {
                animDx.animateTo(
                    0f,
                    animationSpec = spring(
                        dampingRatio = 0.70f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                animDy.animateTo(
                    0f,
                    animationSpec = spring(
                        dampingRatio = 0.70f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
        if (slotCenter != Offset.Zero) {
            prevCenter = slotCenter
        }
    }

    // Apple-style refined lift: 1.08x scale, 0.94x subtle hover dodge
    val dragScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.08f
            isHovered  -> 0.94f
            else       -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "drag_scale"
    )

    // Soft diffused Apple shadow on lift
    val shadowElevation by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "drag_shadow"
    )

    Box(
        modifier = modifier
            .zIndex(if (isDragging) 999f else if (isHovered) 50f else 1f)
            .graphicsLayer {
                if (isDragging) {
                    translationX = if (slotCenter != Offset.Zero) dragFingerPos.x - slotCenter.x else 0f
                    translationY = if (slotCenter != Offset.Zero) dragFingerPos.y - slotCenter.y else 0f
                    scaleX = dragScale
                    scaleY = dragScale
                    this.shadowElevation = shadowElevation.toPx()
                    alpha = 1.0f
                } else {
                    translationX = animDx.value
                    translationY = animDy.value
                    scaleX = dragScale
                    scaleY = dragScale
                    if (isEditMode) {
                        rotationZ = jiggleAngle
                        translationY += kotlin.math.abs(jiggleAngle) * 0.3f
                    }
                }
            }
    ) {
        content()
    }
}

/**
 * Calculates deterministic visual center and bounding dimensions of slot [idx]
 */
private fun getSlotMetrics(
    idx: Int,
    activeOrder: List<String>,
    gridWidth: Float,
    colGap: Float,
    rowGap: Float,
    hCircle: Float,
    hPill: Float
): Triple<Offset, Float, Float> {
    if (idx !in activeOrder.indices || gridWidth <= 0f) {
        return Triple(Offset.Zero, 0f, 0f)
    }
    val chunks = activeOrder.chunked(4)
    val totalRows = chunks.size

    var row = 0
    var remaining = idx
    for (r in chunks.indices) {
        if (remaining < chunks[r].size) {
            row = r
            break
        }
        remaining -= chunks[r].size
    }
    val col = remaining
    val rowItems = chunks[row]
    val isLastRow = row == totalRows - 1
    val usePill = isLastRow && rowItems.size <= 2

    val cellW = if (usePill) {
        (gridWidth - (rowItems.size - 1) * colGap) / rowItems.size.coerceAtLeast(1)
    } else {
        (gridWidth - 3 * colGap) / 4f
    }
    val cellH = if (usePill) hPill else hCircle

    var topY = 0f
    for (r in 0 until row) {
        val rIsLast = r == totalRows - 1
        val rUsePill = rIsLast && chunks[r].size <= 2
        topY += (if (rUsePill) hPill else hCircle) + rowGap
    }

    val leftX = col * (cellW + colGap)
    val centerX = leftX + cellW / 2f
    val centerY = topY + cellH / 2f
    return Triple(Offset(centerX, centerY), cellW, cellH)
}

/**
 * Maps screen coordinate [pos] to the targeted slot index in [activeOrder]
 */
private fun findSlotAtPosition(
    pos: Offset,
    activeOrder: List<String>,
    gridWidth: Float,
    colGap: Float,
    rowGap: Float,
    hCircle: Float,
    hPill: Float
): Int? {
    if (activeOrder.isEmpty() || gridWidth <= 0f) return null
    val chunks = activeOrder.chunked(4)
    val totalRows = chunks.size

    var curTop = 0f
    var targetRow = -1
    for (r in chunks.indices) {
        val rIsLast = r == totalRows - 1
        val rUsePill = rIsLast && chunks[r].size <= 2
        val rH = if (rUsePill) hPill else hCircle
        val rBottom = curTop + rH + rowGap
        if (pos.y <= rBottom || r == totalRows - 1) {
            targetRow = r
            break
        }
        curTop = rBottom
    }
    if (targetRow == -1) targetRow = totalRows - 1

    val rowItems = chunks[targetRow]
    val isLastRow = targetRow == totalRows - 1
    val usePill = isLastRow && rowItems.size <= 2

    val cellW = if (usePill) {
        (gridWidth - (rowItems.size - 1) * colGap) / rowItems.size.coerceAtLeast(1)
    } else {
        (gridWidth - 3 * colGap) / 4f
    }

    val col = (pos.x / (cellW + colGap).coerceAtLeast(1f)).toInt().coerceIn(0, rowItems.size - 1)
    val foundIdx = chunks.take(targetRow).sumOf { it.size } + col
    return if (foundIdx in activeOrder.indices) foundIdx else null
}
