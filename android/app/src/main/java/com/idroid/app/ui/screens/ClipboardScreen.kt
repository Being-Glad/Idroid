package com.idroid.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.service.ClipboardItem
import com.idroid.app.service.CompanionBridge
import com.idroid.app.service.IdroidService
import com.idroid.app.ui.*

/**
 * ClipboardScreen - Minimal, High-Density Universal Clipboard
 * Real-time two-way synchronization between Mac and Android.
 * Supports pinning, instant 1-tap copy, search, filtering, and rapid send.
 */
@Composable
fun ClipboardScreen(
    sharedClipboardText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var inputText by remember { mutableStateOf("") }
    var copiedId by remember { mutableStateOf<String?>(null) }

    // Auto-reset copied indicator after 1.5 seconds
    LaunchedEffect(copiedId) {
        if (copiedId != null) {
            kotlinx.coroutines.delay(1500L)
            copiedId = null
        }
    }

    // Refresh history from Mac on initial entrance
    LaunchedEffect(Unit) {
        IdroidService.instance?.requestClipboardHistory()
    }

    LaunchedEffect(sharedClipboardText) {
        if (sharedClipboardText.isNotBlank() && CompanionBridge.clipboardHistory.none { it.text == sharedClipboardText }) {
            val insertIdx = CompanionBridge.clipboardHistory.indexOfFirst { !it.pinned }.let { if (it == -1) CompanionBridge.clipboardHistory.size else it }
            CompanionBridge.clipboardHistory.add(
                insertIdx,
                ClipboardItem(
                    id = "mac-live-${System.currentTimeMillis()}",
                    text = sharedClipboardText,
                    source = "Mac",
                    time = "Just now",
                    pinned = false
                )
            )
        }
    }

    val history = CompanionBridge.clipboardHistory
    val historyList = history.toList()

    // Filter clips based on search query and category
    val filteredClips = remember(historyList, searchQuery, selectedFilter) {
        historyList.filter { item ->
            val matchesQuery = searchQuery.isBlank() || item.text.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "Pinned" -> item.pinned
                "Images" -> !item.imageBase64.isNullOrBlank()
                "Mac" -> item.source.contains("Mac", ignoreCase = true)
                "Phone" -> item.source.contains("Phone", ignoreCase = true) || item.source.contains("Android", ignoreCase = true)
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    val pinnedClips = remember(filteredClips) { filteredClips.filter { it.pinned } }
    val recentClips = remember(filteredClips) { filteredClips.filter { !it.pinned } }

    val pinnedTotalCount = remember(historyList) { historyList.count { it.pinned } }
    val imagesTotalCount = remember(historyList) { historyList.count { !it.imageBase64.isNullOrBlank() } }
    val macTotalCount = remember(historyList) { historyList.count { it.source.contains("Mac", ignoreCase = true) } }
    val phoneTotalCount = remember(historyList) { historyList.count { it.source.contains("Phone", ignoreCase = true) || it.source.contains("Android", ignoreCase = true) } }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 1. Top Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Clipboard",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = PureWhite
                    )

                    Surface(
                        color = EmeraldBeacon.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBeacon.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                                text = "Live",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = InterFontFamily,
                                color = EmeraldBeacon
                            )
                        }
                    }
                }

                // Action Icons (Clear unpinned & Sync)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (history.any { !it.pinned }) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                IdroidService.instance?.clearClipboardHistory()
                                Toast.makeText(context, "Cleared unpinned history", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Clear History",
                                tint = TextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.requestClipboardHistory()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = SolarOrange,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // 2. Search Field
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(17.dp)
                    )

                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = PureWhite,
                            fontSize = 13.sp,
                            fontFamily = InterFontFamily
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(SolarOrange),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search clips...",
                                        fontSize = 13.sp,
                                        fontFamily = InterFontFamily,
                                        color = TextMuted
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 3. Compact Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "All" to history.size,
                    "Pinned" to pinnedTotalCount,
                    "Images" to imagesTotalCount,
                    "Mac" to macTotalCount,
                    "Phone" to phoneTotalCount
                )

                filters.forEach { (label, count) ->
                    val isSelected = selectedFilter == label
                    val filterShape = RoundedCornerShape(10.dp)
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedFilter = label
                        },
                        color = if (isSelected) SolarOrange.copy(alpha = 0.15f) else ObsidianSurface,
                        shape = filterShape,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) SolarOrange else ObsidianBorder
                        ),
                        modifier = Modifier.clip(filterShape)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (label == "Pinned") {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = if (isSelected) SolarOrange else TextMuted,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = "$label ($count)",
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = InterFontFamily,
                                color = if (isSelected) SolarOrange else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 4. High-Density Scrollable Clips List (LazyColumn for 100+ items at 120fps)
            if (filteredClips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = TextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching clips" else "No clipboard history yet",
                            fontSize = 13.5.sp,
                            fontFamily = InterFontFamily,
                            color = TextMuted
                        )
                        Text(
                            text = "Any text copied on Mac or Phone instantly syncs here",
                            fontSize = 11.5.sp,
                            fontFamily = InterFontFamily,
                            color = TextMuted.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Pinned Section
                    if (pinnedClips.isNotEmpty()) {
                        item(key = "header_pinned") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = SolarOrange,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "PINNED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = InterFontFamily,
                                    color = SolarOrange,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "(${pinnedClips.size})",
                                    fontSize = 11.sp,
                                    fontFamily = InterFontFamily,
                                    color = TextMuted
                                )
                            }
                        }

                        items(pinnedClips, key = { "pinned_${it.id}" }) { clip ->
                            ClipboardItemRow(
                                item = clip,
                                isCopied = copiedId == clip.id,
                                onCopy = {
                                    if (!clip.imageBase64.isNullOrBlank()) {
                                        IdroidService.instance?.setAndroidClipboardImage(clip.imageBase64)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        copiedId = clip.id
                                        Toast.makeText(context, "Image copied to clipboard", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Idroid Clipboard", clip.text))
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        copiedId = clip.id
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onTogglePin = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    IdroidService.instance?.togglePinClipboard(clip.id)
                                },
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    IdroidService.instance?.deleteClipboardItem(clip.id)
                                }
                            )
                        }

                        item(key = "spacer_after_pinned") {
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // Recent History Section
                    if (recentClips.isNotEmpty()) {
                        item(key = "header_recent") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "RECENT HISTORY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = InterFontFamily,
                                    color = TextMuted,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "(${recentClips.size})",
                                    fontSize = 11.sp,
                                    fontFamily = InterFontFamily,
                                    color = TextMuted
                                )
                            }
                        }

                        items(recentClips, key = { "recent_${it.id}" }) { clip ->
                            ClipboardItemRow(
                                item = clip,
                                isCopied = copiedId == clip.id,
                                onCopy = {
                                    if (!clip.imageBase64.isNullOrBlank()) {
                                        IdroidService.instance?.setAndroidClipboardImage(clip.imageBase64)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        copiedId = clip.id
                                        Toast.makeText(context, "Image copied to clipboard", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Idroid Clipboard", clip.text))
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        copiedId = clip.id
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onTogglePin = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    IdroidService.instance?.togglePinClipboard(clip.id)
                                },
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    IdroidService.instance?.deleteClipboardItem(clip.id)
                                }
                            )
                        }
                    }

                    // Spacer for Quick Send pill and Bottom Navigation bar
                    item(key = "bottom_space") {
                        Spacer(Modifier.height(130.dp))
                    }
                }
            }
        }

        // 5. Sleek Quick Send Pill (Docked right above bottom navigation bar)
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 76.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = PureWhite,
                        fontSize = 13.sp,
                        fontFamily = InterFontFamily
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(SolarOrange),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (inputText.isEmpty()) {
                                Text(
                                    "Type or paste to send to Mac...",
                                    fontSize = 13.sp,
                                    fontFamily = InterFontFamily,
                                    color = TextMuted
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendClipboard(inputText)
                            Toast.makeText(context, "Sent to Mac clipboard", Toast.LENGTH_SHORT).show()
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank()) SolarOrange else ObsidianSurfaceVariant)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) PureWhite else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact, High-Density Clipboard Row
 * Apple/Raycast style: information-dense, sleek, with 1-tap copy & pin toggle
 */
@Composable
private fun ClipboardItemRow(
    item: ClipboardItem,
    isCopied: Boolean,
    onCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMac = item.source.contains("Mac", ignoreCase = true)
    val isImage = !item.imageBase64.isNullOrBlank()

    val bitmap = remember(item.imageBase64) {
        if (isImage) {
            try {
                val bytes = android.util.Base64.decode(item.imageBase64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else null
    }

    val cardShape = RoundedCornerShape(12.dp)
    Surface(
        onClick = { onCopy() },
        color = if (item.pinned) SolarOrange.copy(alpha = 0.05f) else ObsidianSurface,
        shape = cardShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.pinned) SolarOrange.copy(alpha = 0.35f) else ObsidianBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Source Badge Icon or Image Thumbnail Preview
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Image snippet",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, SolarOrange.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isMac) SolarOrange.copy(alpha = 0.15f) else ElectricBlue.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isMac) Icons.Default.LaptopMac else Icons.Default.PhoneAndroid,
                        contentDescription = item.source,
                        tint = if (isMac) SolarOrange else ElectricBlue,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Main Content: Snippet Text & Metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isImage) {
                    Text(
                        text = "Screenshot / Image",
                        fontSize = 12.5.sp,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = item.text.replace("\n", " ").trim(),
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val displaySource = if (isMac) "Mac" else "Phone"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = displaySource,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFontFamily,
                        color = if (isMac) SolarOrange else ElectricBlue,
                        maxLines = 1
                    )
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    Text(
                        text = if (item.time.isNotBlank()) item.time else "Just now",
                        fontSize = 10.5.sp,
                        fontFamily = InterFontFamily,
                        color = TextMuted,
                        maxLines = 1
                    )
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    Text(
                        text = if (isImage) "Image" else "${item.text.length} chars",
                        fontSize = 10.5.sp,
                        fontFamily = InterFontFamily,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }

            // Action Buttons: Pin, Copy, Delete
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Pin Toggle Button
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = if (item.pinned) "Unpin" else "Pin",
                        tint = if (item.pinned) SolarOrange else TextMuted.copy(alpha = 0.6f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Copy Button (with instant checkmark feedback)
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = if (isCopied) EmeraldBeacon else SolarOrange,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete",
                        tint = TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
