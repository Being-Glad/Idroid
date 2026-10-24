package com.idroid.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class DashboardCategory(val label: String, val icon: ImageVector) {
    CALLS_AUDIO("Calls & Audio", Icons.Default.Mic),
    SYSTEM_POWER("System & Power", Icons.Default.PowerSettingsNew),
    PRODUCTIVITY("Productivity & Mac", Icons.Default.GridView),
    HARDWARE_SLIDERS("Sliders & Widgets", Icons.Default.Tune)
}

enum class DashboardControlStyle {
    HEADER_CAPSULE,  // Direct Wi-Fi, Bluetooth, Mac Device (Stacked left)
    FOCUS_PILL,      // Wide 2-column action pills (Sleep, Lock, Mic, Desktop)
    CIRCLE_BUTTON,   // Compact quick-action circular triggers (Screenshot, Dark Mode, Mission Control, etc.)
    SLIDER_CARD,     // Sound, Display
    WIDGET_CARD      // Now Playing, Shared Clipboard
}

data class DashboardControl(
    val id: String,
    val title: String,
    val description: String,
    val category: DashboardCategory,
    val style: DashboardControlStyle,
    val icon: ImageVector,
    val defaultEnabled: Boolean = true
)

object DashboardRegistry {
    // Top Section Cards & Capsules
    val DIRECT_WIFI = DashboardControl(
        id = "direct_wifi",
        title = "Wi-Fi",
        description = "View and toggle Mac Wi-Fi network connection",
        category = DashboardCategory.SYSTEM_POWER,
        style = DashboardControlStyle.HEADER_CAPSULE,
        icon = Icons.Default.Wifi,
        defaultEnabled = true
    )

    val BLUETOOTH = DashboardControl(
        id = "bluetooth",
        title = "Bluetooth",
        description = "Phone Bluetooth status and quick settings toggle",
        category = DashboardCategory.SYSTEM_POWER,
        style = DashboardControlStyle.HEADER_CAPSULE,
        icon = Icons.Default.Bluetooth,
        defaultEnabled = true
    )

    val MAC_BATTERY = DashboardControl(
        id = "mac_battery",
        title = "Mac Status",
        description = "Connected Mac model, battery level, and charging state",
        category = DashboardCategory.SYSTEM_POWER,
        style = DashboardControlStyle.HEADER_CAPSULE,
        icon = Icons.Default.LaptopMac,
        defaultEnabled = false
    )

    val NOW_PLAYING = DashboardControl(
        id = "now_playing",
        title = "Now Playing",
        description = "Media player tile with Mac Play/Pause, Next & Previous controls",
        category = DashboardCategory.CALLS_AUDIO,
        style = DashboardControlStyle.WIDGET_CARD,
        icon = Icons.Default.PlayCircle,
        defaultEnabled = true
    )

    // Focus Pills (Wide 2-Column Actions)
    val MIC_MUTE = DashboardControl(
        id = "mic_mute",
        title = "Mute Mac Mic",
        description = "Instantly mute or unmute Mac microphone for Zoom/Meet/Slack",
        category = DashboardCategory.CALLS_AUDIO,
        style = DashboardControlStyle.FOCUS_PILL,
        icon = Icons.Default.MicOff,
        defaultEnabled = true
    )

    val AUDIO_MUTE = DashboardControl(
        id = "audio_mute",
        title = "Mute Mac Audio",
        description = "Toggle Mac master speaker volume mute/unmute",
        category = DashboardCategory.CALLS_AUDIO,
        style = DashboardControlStyle.FOCUS_PILL,
        icon = Icons.Default.VolumeMute,
        defaultEnabled = false
    )

    val LOCK_MAC = DashboardControl(
        id = "lock_mac",
        title = "Lock Mac",
        description = "Instantly lock Mac screen and suspend active session",
        category = DashboardCategory.SYSTEM_POWER,
        style = DashboardControlStyle.FOCUS_PILL,
        icon = Icons.Default.Lock,
        defaultEnabled = true
    )

    val SLEEP_DISPLAY = DashboardControl(
        id = "sleep_display",
        title = "Sleep Display",
        description = "Put Mac display to sleep immediately",
        category = DashboardCategory.SYSTEM_POWER,
        style = DashboardControlStyle.FOCUS_PILL,
        icon = Icons.Default.PowerSettingsNew,
        defaultEnabled = true
    )

    val SHOW_DESKTOP = DashboardControl(
        id = "show_desktop",
        title = "Show Desktop",
        description = "Minimize all open Mac application windows at once",
        category = DashboardCategory.PRODUCTIVITY,
        style = DashboardControlStyle.FOCUS_PILL,
        icon = Icons.Default.DesktopWindows,
        defaultEnabled = false
    )

    val PHONE_LOCATION = DashboardControl(
        id = "phone_location",
        title = "Location",
        description = "One-click instant GPS enable/disable without opening Settings",
        category = DashboardCategory.SYSTEM_POWER,
        style = DashboardControlStyle.FOCUS_PILL,
        icon = Icons.Default.LocationOn,
        defaultEnabled = false
    )

    // Circular Compact Buttons
    val TRACKPAD = DashboardControl(
        id = "trackpad",
        title = "Trackpad",
        description = "Open wireless precision multitouch trackpad",
        category = DashboardCategory.PRODUCTIVITY,
        style = DashboardControlStyle.CIRCLE_BUTTON,
        icon = Icons.Default.TouchApp,
        defaultEnabled = false
    )

    val CLIPBOARD_BUTTON = DashboardControl(
        id = "clipboard_button",
        title = "Clipboard",
        description = "Open shared bidirectional clipboard manager",
        category = DashboardCategory.PRODUCTIVITY,
        style = DashboardControlStyle.CIRCLE_BUTTON,
        icon = Icons.Default.ContentPaste,
        defaultEnabled = false
    )

    val SCREENSHOT = DashboardControl(
        id = "screenshot",
        title = "Screenshot",
        description = "Capture high-resolution Mac screen to Desktop",
        category = DashboardCategory.PRODUCTIVITY,
        style = DashboardControlStyle.CIRCLE_BUTTON,
        icon = Icons.Default.Screenshot,
        defaultEnabled = false
    )

    val DARK_MODE = DashboardControl(
        id = "dark_mode",
        title = "Dark Mode",
        description = "Toggle macOS Light / Dark Appearance",
        category = DashboardCategory.PRODUCTIVITY,
        style = DashboardControlStyle.CIRCLE_BUTTON,
        icon = Icons.Default.DarkMode,
        defaultEnabled = true
    )

    val MISSION_CONTROL = DashboardControl(
        id = "mission_control",
        title = "Mission Control",
        description = "Exposé view of all open spaces and application windows",
        category = DashboardCategory.PRODUCTIVITY,
        style = DashboardControlStyle.CIRCLE_BUTTON,
        icon = Icons.Default.GridView,
        defaultEnabled = false
    )

    val MAC_APPS = DashboardControl(
        id = "mac_apps",
        title = "Mac Apps",
        description = "Browse and remotely launch applications installed on Mac",
        category = DashboardCategory.PRODUCTIVITY,
        style = DashboardControlStyle.CIRCLE_BUTTON,
        icon = Icons.Default.Apps,
        defaultEnabled = false
    )

    val PRESENTATION_CLICKER = DashboardControl(
        id = "presentation_clicker",
        title = "Slide Next",
        description = "Advance Keynote or PowerPoint slide on Mac",
        category = DashboardCategory.PRODUCTIVITY,
        style = DashboardControlStyle.CIRCLE_BUTTON,
        icon = Icons.Default.ArrowForward,
        defaultEnabled = false
    )

    val SPOTLIGHT = DashboardControl(
        id = "spotlight",
        title = "Spotlight",
        description = "Open Spotlight Search on Mac (Cmd + Space)",
        category = DashboardCategory.PRODUCTIVITY,
        style = DashboardControlStyle.CIRCLE_BUTTON,
        icon = Icons.Default.Search,
        defaultEnabled = false
    )

    // Sliders & Widgets
    val SOUND_SLIDER = DashboardControl(
        id = "sound_slider",
        title = "Sound Slider",
        description = "Continuous Mac master volume adjustment card",
        category = DashboardCategory.HARDWARE_SLIDERS,
        style = DashboardControlStyle.SLIDER_CARD,
        icon = Icons.Default.VolumeUp,
        defaultEnabled = true
    )

    val DISPLAY_SLIDER = DashboardControl(
        id = "display_slider",
        title = "Display Slider",
        description = "Continuous Mac display brightness adjustment card",
        category = DashboardCategory.HARDWARE_SLIDERS,
        style = DashboardControlStyle.SLIDER_CARD,
        icon = Icons.Default.BrightnessMedium,
        defaultEnabled = true
    )

    val CLIPBOARD_CARD = DashboardControl(
        id = "clipboard_card",
        title = "Clipboard Card",
        description = "Live preview of shared Mac-Android clipboard snippet",
        category = DashboardCategory.HARDWARE_SLIDERS,
        style = DashboardControlStyle.WIDGET_CARD,
        icon = Icons.Default.Assignment,
        defaultEnabled = false
    )

    val ALL_CONTROLS = listOf(
        DIRECT_WIFI,
        BLUETOOTH,
        MAC_BATTERY,
        NOW_PLAYING,
        MIC_MUTE,
        AUDIO_MUTE,
        LOCK_MAC,
        SLEEP_DISPLAY,
        SHOW_DESKTOP,
        PHONE_LOCATION,
        TRACKPAD,
        CLIPBOARD_BUTTON,
        SCREENSHOT,
        DARK_MODE,
        MISSION_CONTROL,
        MAC_APPS,
        PRESENTATION_CLICKER,
        SPOTLIGHT,
        SOUND_SLIDER,
        DISPLAY_SLIDER,
        CLIPBOARD_CARD
    )

    val DEFAULT_ENABLED_IDS = ALL_CONTROLS.filter { it.defaultEnabled }.map { it.id }.toSet()

    val DEFAULT_PILL_ORDER = listOf(
        "mic_mute",
        "lock_mac",
        "sleep_display",
        "show_desktop",
        "audio_mute",
        "phone_location"
    )

    val DEFAULT_CIRCLE_ORDER = listOf(
        "dark_mode",
        "lock_mac",
        "sleep_display",
        "mic_mute",
        "screenshot",
        "mission_control",
        "trackpad",
        "clipboard_button",
        "mac_apps",
        "presentation_clicker",
        "spotlight",
        "show_desktop",
        "phone_location"
    )

    val DEFAULT_UTILITY_ORDER = DEFAULT_PILL_ORDER + DEFAULT_CIRCLE_ORDER
}
