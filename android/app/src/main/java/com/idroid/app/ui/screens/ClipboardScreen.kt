package com.idroid.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
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
 * ClipboardScreen - Dedicated Universal Clipboard Screen
 * Provides instant two-way clipboard mirroring between Android and macOS.
 */
@Composable
fun ClipboardScreen(
    sharedClipboardText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var inputText by remember { mutableStateOf("") }
    var copiedToPhone by remember { mutableStateOf(false) }

    // Recent local clips history
    val recentClips = remember {
        mutableStateListOf(
            "https://github.com/idroid-ecosystem",
            "ssh dev@macbook-pro.local",
            "npm run tauri dev"
        )
    }

    LaunchedEffect(copiedToPhone) {
        if (copiedToPhone) {
            kotlinx.coroutines.delay(2000L)
            copiedToPhone = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Universal Clipboard",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )
                Text(
                    text = "Live two-way clipboard bridge with Mac",
                    fontSize = 12.sp,
                    fontFamily = InterFontFamily,
                    color = TextSecondary
                )
            }

            Surface(
                color = EmeraldBeacon.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldBeacon.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(EmeraldBeacon)
                    )
                    Text(
                        text = "Real-time Synced",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = EmeraldBeacon
                    )
                }
            }
        }

        // 2. Live Mac Clipboard Viewer Card
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
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
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SolarOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LaptopMac,
                                contentDescription = null,
                                tint = SolarOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Mac Clipboard",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = InterFontFamily,
                                color = PureWhite
                            )
                            Text(
                                text = if (sharedClipboardText.isNotBlank()) "${sharedClipboardText.length} characters" else "Empty",
                                fontSize = 11.sp,
                                fontFamily = InterFontFamily,
                                color = TextMuted
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (sharedClipboardText.isNotBlank()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Idroid Mac Clipboard", sharedClipboardText))
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                copiedToPhone = true
                                Toast.makeText(context, "Copied to phone clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (copiedToPhone) EmeraldBeacon else SolarOrange
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            if (copiedToPhone) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = PureWhite
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (copiedToPhone) "Copied!" else "Copy to Phone",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                    }
                }

                Surface(
                    color = ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = sharedClipboardText.ifBlank { "Nothing copied on your Mac yet. Any text or links you copy on macOS instantly stream here." },
                        fontSize = 13.sp,
                        fontFamily = InterFontFamily,
                        color = if (sharedClipboardText.isNotBlank()) PureWhite else TextMuted,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // 3. Send Text to Mac Input Card
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
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
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElectricBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Send Text to Mac",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                    }

                    // Paste from phone button
                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = cm.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                if (pasteText.isNotBlank()) {
                                    inputText = pasteText
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Paste Phone Clip", fontSize = 11.5.sp, color = SolarOrange, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "Type, paste, or draft text to beam straight into Mac's clipboard...",
                            fontSize = 13.sp,
                            fontFamily = InterFontFamily,
                            color = TextMuted
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedContainerColor = ObsidianSurfaceVariant,
                        unfocusedContainerColor = ObsidianSurfaceVariant,
                        focusedBorderColor = SolarOrange,
                        unfocusedBorderColor = ObsidianBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )

                // Quick Beam Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Meeting Link", "Code Snippet", "Wi-Fi Key").forEach { chip ->
                        Surface(
                            color = ObsidianSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier
                                .clickable {
                                    when (chip) {
                                        "Meeting Link" -> inputText = "https://meet.google.com/xyz-idroid-hub"
                                        "Code Snippet" -> inputText = "git pull && npm run build"
                                        "Wi-Fi Key" -> inputText = "Idroid-Ultra-Mesh-5G"
                                    }
                                }
                        ) {
                            Text(
                                text = "+ $chip",
                                fontSize = 11.sp,
                                fontFamily = InterFontFamily,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            IdroidService.instance?.sendClipboard(inputText)
                            if (!recentClips.contains(inputText)) {
                                recentClips.add(0, inputText)
                            }
                            Toast.makeText(context, "Beamed to Mac clipboard!", Toast.LENGTH_SHORT).show()
                            inputText = ""
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SolarOrange),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Beam to Mac Clipboard",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }

        // 4. Quick Clipboard Snippets
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Recent Snippets",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFontFamily,
                color = PureWhite
            )

            recentClips.take(4).forEach { snippet ->
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            inputText = snippet
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = snippet,
                            fontSize = 12.sp,
                            fontFamily = InterFontFamily,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                IdroidService.instance?.sendClipboard(snippet)
                                Toast.makeText(context, "Beamed snippet to Mac!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                tint = SolarOrange,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Space for floating bottom navigation bar
        Spacer(modifier = Modifier.height(84.dp))
    }
}
