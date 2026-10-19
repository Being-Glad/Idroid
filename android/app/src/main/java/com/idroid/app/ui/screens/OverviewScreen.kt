package com.idroid.app.ui.screens

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.idroid.app.service.IdroidService
import com.idroid.app.ui.*
import com.idroid.app.ui.widgets.*
import com.idroid.app.utils.BluetoothHelper

/**
 * OverviewScreen - Apple macOS / iOS Control Center Style Command Deck
 * Organized with organic capsules, fluid continuous sliders, and circular action toggles.
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
    val liveMacVolume = com.idroid.app.service.CompanionBridge.macVolume.value
    val liveMacBrightness = com.idroid.app.service.CompanionBridge.macBrightness.value
    var soundLevel by remember(liveMacVolume) { mutableStateOf(liveMacVolume) }
    var displayLevel by remember(liveMacBrightness) { mutableStateOf(liveMacBrightness) }
    var btStatus by remember { mutableStateOf(BluetoothHelper.getStatus(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                btStatus = BluetoothHelper.getStatus(context)
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
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Main 2-Column Grid (Left: 3 Capsules Stack | Right: Media Square + 2 Circles)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Column (3 Stacked Capsules: Direct Wi-Fi, Bluetooth, Mac Device)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Direct Wi-Fi Capsule
                ControlCenterPillToggle(
                    icon = Icons.Default.Wifi,
                    title = "Direct Wi-Fi",
                    subtitle = "Low Latency (< 4ms)",
                    isActive = true,
                    activeColor = SolarOrange,
                    onClick = {
                        Toast.makeText(context, "Direct Wi-Fi link active", Toast.LENGTH_SHORT).show()
                    }
                )

                // Bluetooth Toggle Capsule
                ControlCenterPillToggle(
                    icon = if (btStatus.isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                    title = "Bluetooth",
                    subtitle = btStatus.statusText,
                    isActive = btStatus.isEnabled,
                    activeColor = if (btStatus.isConnected) EmeraldBeacon else ElectricBlue,
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, btStatus.statusText, Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Mac Link / Battery Capsule
                ControlCenterPillToggle(
                    icon = Icons.Default.LaptopMac,
                    title = if (macName.contains("'s")) macName.substringBefore("'s") + "'s Mac" else macName.take(12),
                    subtitle = "$macBattery% ${if (macIsCharging) "⚡ Charging" else "Battery"}",
                    isActive = true,
                    activeColor = EmeraldBeacon,
                    onClick = onOpenSettings
                )
            }

            // Right Column (Media Square + 2 Circular Action Buttons)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Media Player Square
                ControlCenterMediaTile(
                    title = "Now Playing",
                    subtitle = "Mac Audio"
                )

                // 2 Circular Buttons Side-by-Side (Height 64dp, matches 3rd capsule)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlCenterCircleButton(
                        icon = Icons.Default.TouchApp,
                        contentDescription = "Trackpad",
                        isActive = true,
                        activeColor = SolarOrange,
                        onClick = onNavigateToTrackpad
                    )

                    ControlCenterCircleButton(
                        icon = Icons.Default.ContentPaste,
                        contentDescription = "Clipboard",
                        isActive = sharedClipboardText.isNotBlank(),
                        activeColor = ElectricBlue,
                        onClick = onNavigateToClipboard
                    )
                }
            }
        }

        // 3. Middle Row: 2 Balanced Symmetrical Focus Capsules (Sleep Display & Lock Mac)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlCenterFocusPill(
                icon = Icons.Default.PowerSettingsNew,
                title = "Sleep Display",
                isActive = false,
                activeColor = CrimsonDanger,
                onClick = {
                    IdroidService.instance?.sendCommand("sleep_display")
                    Toast.makeText(context, "Display Sleep Triggered", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )

            ControlCenterFocusPill(
                icon = Icons.Default.Lock,
                title = "Lock Mac",
                isActive = false,
                activeColor = SolarOrange,
                onClick = {
                    IdroidService.instance?.sendCommand("lock_screen")
                    Toast.makeText(context, "Mac Screen Locked", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )
        }

        // 4. Row of 4 Balanced Circular Control Center Buttons (Screenshot, Night Shift, Spaces, Apps)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlCenterCircleButton(
                icon = Icons.Default.Screenshot,
                contentDescription = "Screenshot",
                isActive = false,
                onClick = {
                    IdroidService.instance?.sendCommand("screenshot")
                    Toast.makeText(context, "Mac Screenshot Captured", Toast.LENGTH_SHORT).show()
                }
            )

            ControlCenterCircleButton(
                icon = Icons.Default.BrightnessMedium,
                contentDescription = "Night Shift",
                isActive = false,
                onClick = {
                    IdroidService.instance?.sendMediaKey("bright_down")
                    Toast.makeText(context, "Night Shift Toggle", Toast.LENGTH_SHORT).show()
                }
            )

            ControlCenterCircleButton(
                icon = Icons.Default.GridView,
                contentDescription = "Mission Control",
                isActive = false,
                onClick = {
                    IdroidService.instance?.sendCommand("mission_control")
                    Toast.makeText(context, "Mission Control", Toast.LENGTH_SHORT).show()
                }
            )

            ControlCenterCircleButton(
                icon = Icons.Default.Apps,
                contentDescription = "Mac Apps",
                isActive = true,
                activeColor = SolarOrange,
                onClick = onNavigateToAdvanced
            )
        }

        // 5. Sound Continuous Slider Card
        ControlCenterSliderCard(
            title = "Sound",
            value = soundLevel,
            onValueChange = { newLevel ->
                soundLevel = newLevel
                IdroidService.instance?.setVolume((newLevel * 100).toInt())
            },
            minIcon = Icons.Default.VolumeMute,
            maxIcon = Icons.Default.VolumeUp,
            onMinClick = {
                val newL = (soundLevel - 0.05f).coerceAtLeast(0f)
                soundLevel = newL
                IdroidService.instance?.setVolume((newL * 100).toInt())
            },
            onMaxClick = {
                val newL = (soundLevel + 0.05f).coerceAtMost(1f)
                soundLevel = newL
                IdroidService.instance?.setVolume((newL * 100).toInt())
            }
        )

        // 6. Display Continuous Slider Card
        ControlCenterSliderCard(
            title = "Display",
            value = displayLevel,
            onValueChange = { newLevel ->
                displayLevel = newLevel
                IdroidService.instance?.setBrightness(newLevel)
            },
            minIcon = Icons.Default.BrightnessLow,
            maxIcon = Icons.Default.BrightnessHigh,
            onMinClick = {
                val newL = (displayLevel - 0.05f).coerceAtLeast(0.05f)
                displayLevel = newL
                IdroidService.instance?.setBrightness(newL)
            },
            onMaxClick = {
                val newL = (displayLevel + 0.05f).coerceAtMost(1f)
                displayLevel = newL
                IdroidService.instance?.setBrightness(newL)
            }
        )

        // 7. Live Shared Clipboard Mini Card
        ClipboardMiniWidget(
            sharedClipboardText = sharedClipboardText,
            onNavigateToClipboard = onNavigateToClipboard
        )

        // Bottom padding for the floating navigation pill
        Spacer(modifier = Modifier.height(84.dp))
    }
}
