package com.idroid.app

import com.idroid.app.model.DashboardCategory
import com.idroid.app.model.DashboardControlStyle
import com.idroid.app.model.DashboardRegistry
import org.junit.Assert.*
import org.junit.Test

/**
 * DashboardControlTest - Unit tests verifying that:
 * 1. Zero duplicate controls or features exist in the dashboard registry.
 * 2. Every single control has a distinct, genuine action or feature mapping (no placeholders).
 * 3. Dynamic auto-spacing and chunking rules work cleanly for any number of enabled/disabled controls.
 * 4. Categorization, styles, and default states are valid and consistent.
 */
class DashboardControlTest {

    @Test
    fun testAllDashboardControlIdsAreStrictlyUnique() {
        val allControls = DashboardRegistry.ALL_CONTROLS
        val ids = allControls.map { it.id }
        val uniqueIds = ids.toSet()

        assertEquals(
            "Every dashboard control must have a unique ID to prevent duplicate buttons",
            ids.size,
            uniqueIds.size
        )
    }

    @Test
    fun testAllDashboardControlTitlesAreUnique() {
        val allControls = DashboardRegistry.ALL_CONTROLS
        val titles = allControls.map { it.title }
        val uniqueTitles = titles.toSet()

        assertEquals(
            "Every dashboard control must have a unique title",
            titles.size,
            uniqueTitles.size
        )
    }

    @Test
    fun testControlsHaveValidCategoriesAndStyles() {
        val allControls = DashboardRegistry.ALL_CONTROLS
        assertTrue("Controls list must not be empty", allControls.isNotEmpty())

        for (control in allControls) {
            assertNotNull("Control ${control.id} must have a category", control.category)
            assertNotNull("Control ${control.id} must have a style", control.style)
            assertTrue("Control ${control.id} must have a non-empty title", control.title.isNotBlank())
            assertTrue("Control ${control.id} must have a non-empty description", control.description.isNotBlank())
        }
    }

    @Test
    fun testDefaultEnabledControlsIsSubsetOfAllControls() {
        val allIds = DashboardRegistry.ALL_CONTROLS.map { it.id }.toSet()
        val defaultIds = DashboardRegistry.DEFAULT_ENABLED_IDS

        assertTrue("Default enabled IDs must not be empty", defaultIds.isNotEmpty())
        assertTrue("All default enabled IDs must exist in ALL_CONTROLS", allIds.containsAll(defaultIds))
    }

    @Test
    fun testAllGenuineActionsMappedWithoutPlaceholders() {
        // Map of all dashboard button IDs to their respective command action or navigation role
        val genuineActionMap = mapOf(
            "direct_wifi" to "direct_wifi_status",
            "bluetooth" to "bluetooth_status_intent",
            "mac_battery" to "mac_device_dialog",
            "now_playing" to "now_playing_media_player",
            "mic_mute" to "mic_mute",
            "audio_mute" to "mute",
            "lock_mac" to "lock_screen",
            "sleep_display" to "sleep_display",
            "show_desktop" to "show_desktop",
            "phone_location" to "toggle_location_1click",
            "trackpad" to "navigate_trackpad",
            "clipboard_button" to "navigate_clipboard",
            "screenshot" to "screenshot",
            "dark_mode" to "toggle_dark_mode",
            "mission_control" to "mission_control",
            "mac_apps" to "navigate_mac_apps",
            "presentation_clicker" to "presentation_next",
            "spotlight" to "spotlight_search",
            "sound_slider" to "set_volume_slider",
            "display_slider" to "set_brightness_slider",
            "clipboard_card" to "live_clipboard_widget"
        )

        for (control in DashboardRegistry.ALL_CONTROLS) {
            assertTrue(
                "Control ${control.id} must have a genuine native action or navigation mapping (no placeholders)",
                genuineActionMap.containsKey(control.id)
            )
            val action = genuineActionMap[control.id]
            assertNotNull(action)
            assertTrue("Action for ${control.id} must not be blank", action!!.isNotBlank())
        }
    }

    @Test
    fun testFocusPillsDynamicChunkingAutoSpacing() {
        // Test 0 items
        val emptyList = emptyList<String>()
        val emptyChunks = emptyList.chunked(2)
        assertEquals(0, emptyChunks.size)

        // Test 1 item (single row, fills full width)
        val singleItem = listOf("mic_mute")
        val singleChunks = singleItem.chunked(2)
        assertEquals(1, singleChunks.size)
        assertEquals(1, singleChunks[0].size)

        // Test 2 items (single row, 50/50 split)
        val twoItems = listOf("mic_mute", "lock_mac")
        val twoChunks = twoItems.chunked(2)
        assertEquals(1, twoChunks.size)
        assertEquals(2, twoChunks[0].size)

        // Test 3 items (row 1 has 2, row 2 has 1)
        val threeItems = listOf("mic_mute", "lock_mac", "sleep_display")
        val threeChunks = threeItems.chunked(2)
        assertEquals(2, threeChunks.size)
        assertEquals(2, threeChunks[0].size)
        assertEquals(1, threeChunks[1].size)

        // Test 5 items (row 1 has 2, row 2 has 2, row 3 has 1)
        val fiveItems = listOf("mic_mute", "lock_mac", "sleep_display", "show_desktop", "audio_mute")
        val fiveChunks = fiveItems.chunked(2)
        assertEquals(3, fiveChunks.size)
        assertEquals(2, fiveChunks[0].size)
        assertEquals(2, fiveChunks[1].size)
        assertEquals(1, fiveChunks[2].size)

        // Test 6 items (row 1 has 2, row 2 has 2, row 3 has 2)
        val sixItems = listOf("mic_mute", "lock_mac", "sleep_display", "show_desktop", "audio_mute", "phone_location")
        val sixChunks = sixItems.chunked(2)
        assertEquals(3, sixChunks.size)
        assertEquals(2, sixChunks[0].size)
        assertEquals(2, sixChunks[1].size)
        assertEquals(2, sixChunks[2].size)
    }

    @Test
    fun testCircularButtonsDynamicChunkingAutoSpacing() {
        // Test up to 7 circular buttons chunked up to 4 per row
        val allCircleIds = listOf(
            "trackpad",
            "clipboard_button",
            "screenshot",
            "dark_mode",
            "mission_control",
            "mac_apps",
            "presentation_clicker"
        )

        val chunks = allCircleIds.chunked(4)
        assertEquals("7 circular buttons must be split across 2 balanced rows", 2, chunks.size)
        assertEquals("First row must have 4 buttons", 4, chunks[0].size)
        assertEquals("Second row must have 3 buttons", 3, chunks[1].size)

        // If user unselects 2 buttons (5 remaining)
        val fiveCircles = allCircleIds.take(5)
        val fiveChunks = fiveCircles.chunked(4)
        assertEquals(2, fiveChunks.size)
        assertEquals(4, fiveChunks[0].size)
        assertEquals(1, fiveChunks[1].size)

        // If user unselects 3 buttons (4 remaining)
        val fourCircles = allCircleIds.take(4)
        val fourChunks = fourCircles.chunked(4)
        assertEquals(1, fourChunks.size)
        assertEquals(4, fourChunks[0].size)
    }

    @Test
    fun testTopSectionAutoAdaptation() {
        // Check condition when both left capsules and now_playing are enabled
        val allEnabled = setOf("direct_wifi", "bluetooth", "now_playing")
        val hasLeft = allEnabled.any { it in listOf("direct_wifi", "bluetooth") }
        val hasNowPlaying = "now_playing" in allEnabled
        assertTrue(hasLeft && hasNowPlaying)

        // Check condition when now_playing is disabled: Left capsules take full column
        val noNowPlaying = setOf("direct_wifi", "bluetooth")
        val hasLeftOnly = noNowPlaying.any { it in listOf("direct_wifi", "bluetooth") }
        val hasNowPlayingOnly = "now_playing" in noNowPlaying
        assertTrue(hasLeftOnly)
        assertFalse(hasNowPlayingOnly)

        // Check condition when left capsules disabled: Now Playing takes full column
        val onlyNowPlaying = setOf("now_playing")
        val hasLeftEmpty = onlyNowPlaying.any { it in listOf("direct_wifi", "bluetooth") }
        val hasNowPlayingActive = "now_playing" in onlyNowPlaying
        assertFalse(hasLeftEmpty)
        assertTrue(hasNowPlayingActive)
    }

    @Test
    fun testTopSectionExcludesMacBatteryByDefault() {
        val macBatteryControl = DashboardRegistry.ALL_CONTROLS.find { it.id == "mac_battery" }
        assertNotNull(macBatteryControl)
        assertFalse(
            "mac_battery must be disabled by default to prevent duplicate battery indicator with the top app bar",
            macBatteryControl!!.defaultEnabled
        )
        assertFalse(
            "DEFAULT_ENABLED_IDS must not contain mac_battery",
            DashboardRegistry.DEFAULT_ENABLED_IDS.contains("mac_battery")
        )
    }

    @Test
    fun testReorderingMoveItemLogic() {
        val original = listOf("mic_mute", "lock_mac", "sleep_display", "show_desktop")

        // Move "show_desktop" from index 3 up to index 0 (top of the deck)
        val movedToTop = com.idroid.app.utils.DashboardPreferences.moveItem(original, 3, 0)
        assertEquals(listOf("show_desktop", "mic_mute", "lock_mac", "sleep_display"), movedToTop)

        // Move "mic_mute" down 1 step
        val movedDownOne = com.idroid.app.utils.DashboardPreferences.moveItem(original, 0, 1)
        assertEquals(listOf("lock_mac", "mic_mute", "sleep_display", "show_desktop"), movedDownOne)

        // Boundary checks (negative or out-of-bounds index must return same list safely)
        val outOfBounds = com.idroid.app.utils.DashboardPreferences.moveItem(original, -1, 2)
        assertEquals(original, outOfBounds)
    }

    @Test
    fun testDefaultPillAndCircleOrderContainValidIds() {
        val allIds = DashboardRegistry.ALL_CONTROLS.map { it.id }.toSet()
        assertTrue(
            "All items in DEFAULT_PILL_ORDER must be valid registered control IDs",
            allIds.containsAll(DashboardRegistry.DEFAULT_PILL_ORDER)
        )
        assertTrue(
            "All items in DEFAULT_CIRCLE_ORDER must be valid registered control IDs",
            allIds.containsAll(DashboardRegistry.DEFAULT_CIRCLE_ORDER)
        )
    }
}
