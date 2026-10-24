package com.idroid.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.idroid.app.service.CompanionBridge
import com.idroid.app.service.IdroidService
import com.idroid.app.service.TurboFileTransfer
import com.idroid.app.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * System Share Sheet Target for Android.
 * Appears when sharing links, text, images, videos, or files from any app (Chrome, Gallery, Files, WhatsApp).
 */
class SendToMacShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.action
        val type = intent.type

        // 1. Text or Link Share
        if (Intent.ACTION_SEND == action && type != null && type.startsWith("text/")) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                handleSharedText(sharedText)
                return
            }
        }

        // 2. File / Photo Share (Single or Multiple)
        val fileUris = mutableListOf<Uri>()
        if (Intent.ACTION_SEND == action) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) fileUris.add(uri)
        } else if (Intent.ACTION_SEND_MULTIPLE == action) {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            if (uris != null) fileUris.addAll(uris)
        }

        if (fileUris.isEmpty()) {
            Toast.makeText(this, "No content to share to Mac", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ShareTransferScreen(
                fileUris = fileUris,
                onDismiss = { finish() }
            )
        }
    }

    private fun handleSharedText(text: String) {
        IdroidService.instance?.sendClipboard(text)
        Toast.makeText(this, "📋 Copied to Mac Clipboard", Toast.LENGTH_SHORT).show()
        finish()
    }
}

@Composable
private fun ShareTransferScreen(
    fileUris: List<Uri>,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    var currentFile by remember { mutableStateOf("Preparing files...") }
    var fileIndex by remember { mutableIntStateOf(1) }
    var totalFiles by remember { mutableIntStateOf(fileUris.size) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var transferSpeed by remember { mutableDoubleStateOf(0.0) }
    var isComplete by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("idroid_prefs", android.content.Context.MODE_PRIVATE)
        val lastIp = prefs.getString("last_connected_ip", "127.0.0.1") ?: "127.0.0.1"
        val targetIp = if (lastIp == "127.0.0.1" || lastIp.isBlank()) "192.168.1.100" else lastIp

        val result = TurboFileTransfer.sendFiles(
            context = context,
            hostIp = targetIp,
            port = 53108,
            uris = fileUris,
            onProgress = { update ->
                currentFile = update.fileName
                fileIndex = update.fileIndex
                totalFiles = update.totalFiles
                progressFraction = if (update.totalBytes > 0) {
                    (update.bytesSent.toFloat() / update.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f
                transferSpeed = update.speedMbPerSec
            }
        )

        if (result.isSuccess) {
            isComplete = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(1200)
            onDismiss()
        } else {
            errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Failed to transfer to Mac"
            delay(2500)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isComplete) EmeraldBeacon.copy(alpha = 0.15f) else SolarOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Laptop,
                        contentDescription = null,
                        tint = if (isComplete) EmeraldBeacon else SolarOrange,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Title
                Text(
                    text = if (isComplete) "Sent to Mac ~/Downloads" else "Sending to Mac...",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )

                // Current File & Count
                Text(
                    text = if (isComplete) "${fileUris.size} file(s) transferred"
                           else "[$fileIndex/$totalFiles] $currentFile",
                    fontSize = 12.sp,
                    fontFamily = InterFontFamily,
                    color = TextSecondary,
                    maxLines = 1
                )

                // Progress Bar
                if (!isComplete) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = SolarOrange,
                        trackColor = ObsidianSurfaceVariant
                    )

                    // Transfer Speed indicator
                    if (transferSpeed > 0.0) {
                        Text(
                            text = String.format("%.1f MB/s • Wi-Fi Turbo", transferSpeed),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFontFamily,
                            color = SolarOrange
                        )
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        fontSize = 11.sp,
                        color = Color(0xFFEF5350),
                        maxLines = 2
                    )
                }
            }
        }
    }
}
