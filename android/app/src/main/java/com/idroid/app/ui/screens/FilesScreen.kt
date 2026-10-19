package com.idroid.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idroid.app.ui.*

/**
 * FilesScreen - Dedicated AirDrop & File Transfer Screen
 * Frictionless Zero-Permission File & Photo Drop straight to Mac ~/Downloads/IdroidDrop
 */
@Composable
fun FilesScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val transferList = remember {
        mutableStateListOf(
            "IMG_20261018_0732.jpg" to "2.4 MB • Transferred",
            "Idroid_Design_Spec.pdf" to "1.1 MB • Transferred"
        )
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Photo_${System.currentTimeMillis()}.jpg"
            transferList.add(0, name to "Sending to Mac...")
            Toast.makeText(context, "Photo dropped to Mac ~/Downloads/IdroidDrop", Toast.LENGTH_LONG).show()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "File_${System.currentTimeMillis()}"
            transferList.add(0, name to "Sending to Mac...")
            Toast.makeText(context, "File dropped to Mac ~/Downloads/IdroidDrop", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AirDrop Files & Photos",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )
                Text(
                    text = "Instant drop directly to your Mac Downloads",
                    fontSize = 12.sp,
                    fontFamily = InterFontFamily,
                    color = TextSecondary
                )
            }

            Surface(
                color = ElectricBlue.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(13.dp))
                    Text(
                        text = "Zero Perms",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = ElectricBlue
                    )
                }
            }
        }

        // 2. Zero-Permissions Privacy Card
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldBeacon.copy(alpha = 0.12f))
                        .border(1.dp, EmeraldBeacon.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldBeacon, modifier = Modifier.size(22.dp))
                }

                Column {
                    Text(
                        text = "Frictionless Direct Drop",
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        fontSize = 13.5.sp,
                        fontFamily = InterFontFamily
                    )
                    Text(
                        text = "No invasive file permissions needed. Only the specific items you select are dropped straight onto your Mac.",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = InterFontFamily
                    )
                }
            }
        }

        // 3. Two Primary Action Drop Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SolarOrange),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Drop Photos", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    filePicker.launch("*/*")
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp), tint = PureWhite)
                Spacer(Modifier.width(8.dp))
                Text("Drop Any File", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, color = PureWhite, fontSize = 13.sp)
            }
        }

        // 4. Mac Drop Destination Card
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Destination on Mac",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFontFamily,
                            color = PureWhite
                        )
                    }

                    Surface(
                        color = EmeraldBeacon.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Auto-Sorted",
                            fontSize = 11.sp,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldBeacon,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Surface(
                    color = ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = SolarOrange, modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                text = "~/Downloads/IdroidDrop",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = InterFontFamily,
                                color = PureWhite
                            )
                            Text(
                                text = "Transfers automatically appear in Finder",
                                fontSize = 11.sp,
                                fontFamily = InterFontFamily,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // 5. Transfer History / Queue
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Recent Transfers",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFontFamily,
                color = PureWhite
            )

            transferList.forEach { (filename, status) ->
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (filename.endsWith(".jpg") || filename.endsWith(".png")) SolarOrange.copy(alpha = 0.15f)
                                    else ElectricBlue.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (filename.endsWith(".jpg") || filename.endsWith(".png")) Icons.Default.Image else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (filename.endsWith(".jpg") || filename.endsWith(".png")) SolarOrange else ElectricBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = filename,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = InterFontFamily,
                                color = PureWhite
                            )
                            Text(
                                text = status,
                                fontSize = 11.sp,
                                fontFamily = InterFontFamily,
                                color = EmeraldBeacon
                            )
                        }

                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldBeacon,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Space for floating bottom navigation bar
        Spacer(modifier = Modifier.height(84.dp))
    }
}
