package com.idroid.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.idroid.app.service.CompanionBridge
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun InAppQrScanner(
    onQrCodeDetected: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    if (!hasCameraPermission) {
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = modifier.fillMaxWidth().height(320.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(SolarOrange.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = SolarOrange,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Live Scanner Needs Camera Access",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = PureWhite
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Scan the QR code shown on your Mac screen directly inside the app to connect instantly.",
                    fontSize = 12.sp,
                    fontFamily = InterFontFamily,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SolarOrange),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enable Camera", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    } else {
        CameraPreviewWithReticle(
            onQrCodeDetected = onQrCodeDetected,
            modifier = modifier
        )
    }
}

@Composable
fun CameraPreviewWithReticle(
    onQrCodeDetected: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isScanningActive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            isScanningActive.set(false)
            try {
                cameraProviderRef?.unbindAll()
                executor.shutdownNow()
            } catch (_: Exception) {}
        }
    }

    // Animated scan line moving continuously
    val infiniteTransition = rememberInfiniteTransition(label = "scan_laser")
    val laserOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    Surface(
        color = Color.Black,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, ObsidianBorder),
        modifier = modifier.fillMaxWidth().height(320.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // CameraX Viewfinder
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProviderRef = cameraProvider
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val scanner = BarcodeScanning.getClient()

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            if (!isScanningActive.get()) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            processImageProxy(
                                scanner = scanner,
                                imageProxy = imageProxy,
                                onResult = { rawText ->
                                    if (isScanningActive.compareAndSet(true, false)) {
                                        val parsed = parsePairingPayload(rawText)
                                        if (parsed != null) {
                                            try {
                                                imageAnalysis.clearAnalyzer()
                                                cameraProvider.unbindAll()
                                            } catch (_: Exception) {}
                                            vibrateDevice(ctx)
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                onQrCodeDetected(parsed.first, parsed.second)
                                            }
                                        } else {
                                            // Reset if parsing was invalid
                                            isScanningActive.set(true)
                                        }
                                    }
                                }
                            )
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("IdroidScanner", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Subtle dark overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
            )

            // Target Reticle Box (200x200)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, SolarOrange.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            ) {
                // Moving Laser Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .offset(y = (laserOffsetY * 196).dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    FinderBlueBright,
                                    Color(0xFF7DD3FC),
                                    FinderBlueBright,
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Top Status Pill
            Surface(
                color = ObsidianSurface.copy(alpha = 0.88f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SolarOrange.copy(alpha = 0.4f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(SolarOrange)
                    )
                    Text(
                        text = "Point camera at Mac QR code",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFontFamily,
                        color = PureWhite
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onResult: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank()) {
                        onResult(rawValue)
                        break
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

fun parsePairingPayload(rawText: String): Pair<String, Int>? {
    try {
        if (rawText.startsWith("idroid://pair")) {
            val uri = Uri.parse(rawText)
            val ip = uri.getQueryParameter("ip")
            val portStr = uri.getQueryParameter("port")
            val port = portStr?.toIntOrNull() ?: 53100
            val ssid = uri.getQueryParameter("ssid")
            val name = uri.getQueryParameter("name")
            val token = uri.getQueryParameter("token")
            if (!ssid.isNullOrBlank()) {
                CompanionBridge.targetMacSsid.value = ssid
            }
            if (!name.isNullOrBlank()) {
                CompanionBridge.macName.value = name
            }
            if (!token.isNullOrBlank()) {
                CompanionBridge.pairingToken.value = token
            }
            if (!ip.isNullOrBlank()) {
                return Pair(ip, port)
            }
        } else if (rawText.contains(":")) {
            val parts = rawText.trim().split(":")
            if (parts.size == 2 && parts[0].count { it == '.' } == 3) {
                val ip = parts[0]
                val port = parts[1].toIntOrNull() ?: 53100
                return Pair(ip, port)
            }
        } else if (rawText.count { it == '.' } == 3) {
            return Pair(rawText.trim(), 53100)
        }
    } catch (_: Exception) {}
    return null
}

fun vibrateDevice(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(70)
        }
    } catch (_: Exception) {}
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerBottomSheet(
    onDismiss: () -> Unit,
    onQrCodeDetected: (String, Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        scrimColor = ObsidianVoid.copy(alpha = 0.85f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.5.dp)
                    .clip(CircleShape)
                    .background(ObsidianBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = SolarOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Scan Mac Screen QR",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = PureWhite
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurfaceVariant)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = "Hold phone camera up to the QR code on your Mac screen",
                fontSize = 12.5.sp,
                fontFamily = InterFontFamily,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            InAppQrScanner(
                onQrCodeDetected = { ip, port ->
                    onQrCodeDetected(ip, port)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )

            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}
