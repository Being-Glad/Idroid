package com.idroid.app

import com.idroid.app.service.MacBtDevice
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BluetoothWifiCompanionTest {

    @Test
    fun testMacBtDeviceDataClass() {
        val dev = MacBtDevice(
            name = "OnePlus Nord Buds 2",
            address = "48-d8-45-db-65-67",
            connected = true
        )
        assertEquals("OnePlus Nord Buds 2", dev.name)
        assertEquals("48-d8-45-db-65-67", dev.address)
        assertTrue(dev.connected)
    }

    @Test
    fun testMacBtDeviceJsonParsing() {
        val jsonString = """
            {
                "type": "mac_bluetooth_devices",
                "devices": [
                    { "name": "OnePlus Nord Buds 2", "address": "48-d8-45-db-65-67", "connected": true },
                    { "name": "Noise Buds Vector", "address": "43-1a-b4-b6-94-70", "connected": false }
                ]
            }
        """.trimIndent()

        val root = JSONObject(jsonString)
        assertEquals("mac_bluetooth_devices", root.getString("type"))

        val arr = root.getJSONArray("devices")
        assertEquals(2, arr.length())

        val list = mutableListOf<MacBtDevice>()
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            list.add(
                MacBtDevice(
                    name = d.getString("name"),
                    address = d.getString("address"),
                    connected = d.getBoolean("connected")
                )
            )
        }

        assertEquals(2, list.size)
        assertEquals("OnePlus Nord Buds 2", list[0].name)
        assertTrue(list[0].connected)
        assertEquals("Noise Buds Vector", list[1].name)
        assertFalse(list[1].connected)
    }

    @Test
    fun testMacBtResultPayloadParsing() {
        val successJson = """
            {
                "type": "mac_bt_result",
                "action": "connect",
                "address": "48-d8-45-db-65-67",
                "success": true,
                "message": "Connected to OnePlus Nord Buds 2",
                "devices": [
                    { "name": "OnePlus Nord Buds 2", "address": "48-d8-45-db-65-67", "connected": true }
                ]
            }
        """.trimIndent()

        val obj = JSONObject(successJson)
        assertTrue(obj.getBoolean("success"))
        assertEquals("Connected to OnePlus Nord Buds 2", obj.getString("message"))
        assertEquals("connect", obj.getString("action"))

        val devices = obj.getJSONArray("devices")
        assertEquals(1, devices.length())
        assertTrue(devices.getJSONObject(0).getBoolean("connected"))
    }

    @Test
    fun testMacWifiStatusParsing() {
        val wifiJson = """
            {
                "type": "mac_wifi_status",
                "power": true,
                "ssid": "Airtel_Pandey",
                "interface": "en0"
            }
        """.trimIndent()

        val obj = JSONObject(wifiJson)
        assertTrue(obj.getBoolean("power"))
        assertEquals("Airtel_Pandey", obj.getString("ssid"))
        assertEquals("en0", obj.getString("interface"))
    }

    @Test
    fun testAudioDeviceClassification() {
        fun isAudio(name: String): Boolean {
            return name.contains("Buds", ignoreCase = true) ||
                    name.contains("AirPods", ignoreCase = true) ||
                    name.contains("Headset", ignoreCase = true) ||
                    name.contains("Audio", ignoreCase = true) ||
                    name.contains("Noise", ignoreCase = true) ||
                    name.contains("OnePlus", ignoreCase = true)
        }

        assertTrue(isAudio("OnePlus Nord Buds 2"))
        assertTrue(isAudio("Noise Buds Vector"))
        assertTrue(isAudio("AirPods Pro"))
        assertTrue(isAudio("Sony WH-1000XM4 Headset"))
        assertFalse(isAudio("Logitech MX Master 3S"))
        assertFalse(isAudio("Magic Keyboard"))
    }

    @Test
    fun testBluetoothAddressFormatting() {
        val rawAddr = "48:d8:45:db:65:67"
        val normalized = rawAddr.replace(':', '-').lowercase()
        assertEquals("48-d8-45-db-65-67", normalized)
    }
}
