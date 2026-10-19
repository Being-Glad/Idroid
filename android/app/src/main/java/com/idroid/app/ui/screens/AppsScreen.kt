package com.idroid.app.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
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
import com.idroid.app.ui.*

/**
 * AppsScreen - macOS App Launcher & Vice-Versa Controller
 * Browse, search, and trigger Mac applications directly from your phone.
 */
@Composable
fun AppsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val macApps = CompanionBridge.macApps
    val isConnected by CompanionBridge.isConnected
    var searchQuery by remember { mutableStateOf("") }

    val allApps = macApps.toList()
    val displayApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) {
            allApps
        } else {
            allApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Mac Applications",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )
                Text(
                    text = "Launch apps on Mac display with one touch",
                    fontSize = 12.sp,
                    fontFamily = InterFontFamily,
                    color = TextSecondary
                )
            }

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

        // Modern Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text("Search ${displayApps.size} Mac applications...", fontSize = 13.sp, fontFamily = InterFontFamily, color = TextMuted)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PureWhite,
                unfocusedTextColor = PureWhite,
                focusedContainerColor = ObsidianSurface,
                unfocusedContainerColor = ObsidianSurface,
                focusedBorderColor = SolarOrange,
                unfocusedBorderColor = ObsidianBorder
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        )

        // Count / Filter Indicator
        Text(
            text = "${displayApps.size} application${if (displayApps.size != 1) "s" else ""} available",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            fontFamily = InterFontFamily
        )

        // Applications Grid
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
                contentPadding = PaddingValues(bottom = 90.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayApps, key = { it.name }) { app ->
                    MacAppCard(
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

@Composable
private fun MacAppCard(
    app: MacAppItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.7f),
        label = "app_card_scale"
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
