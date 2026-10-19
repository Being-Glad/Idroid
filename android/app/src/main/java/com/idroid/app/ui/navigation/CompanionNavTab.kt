package com.idroid.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.material.icons.filled.Tune

enum class CompanionNavTab(
    val title: String,
    val icon: ImageVector,
    val badge: String? = null
) {
    OVERVIEW("Overview", Icons.Default.Dashboard),
    TRACKPAD("Trackpad", Icons.Default.TouchApp),
    CLIPBOARD("Clipboard", Icons.Default.ContentPaste),
    FILES("Files", Icons.Default.FolderShared),
    ADVANCED("Advanced", Icons.Default.Tune);
}

