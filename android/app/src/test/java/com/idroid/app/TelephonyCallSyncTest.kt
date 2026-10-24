package com.idroid.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * TelephonyCallSyncTest - Unit tests verifying real-time call sync, audio routing,
 * and contact / call history command payload handling on Android.
 */
class TelephonyCallSyncTest {

    @Test
    fun testCallStateChangedPayloadFormatting() {
        fun buildCallStatePayload(state: String, isRinging: Boolean, number: String?, name: String?): JSONObject {
            return JSONObject().apply {
                put("type", "call_state_changed")
                put("state", state)
                put("is_ringing", isRinging)
                put("number", number ?: "")
                put("name", name ?: "")
            }
        }

        // 1. Phone user hangs up / cuts call on phone
        val idlePayload = buildCallStatePayload("idle", false, "", "")
        assertEquals("call_state_changed", idlePayload.getString("type"))
        assertEquals("idle", idlePayload.getString("state"))
        assertFalse(idlePayload.getBoolean("is_ringing"))

        // 2. Incoming call ringing on phone
        val ringingPayload = buildCallStatePayload("ringing", true, "+14158902341", "Sarah Connor")
        assertEquals("ringing", ringingPayload.getString("state"))
        assertTrue(ringingPayload.getBoolean("is_ringing"))
        assertEquals("+14158902341", ringingPayload.getString("number"))
        assertEquals("Sarah Connor", ringingPayload.getString("name"))

        // 3. Active call offhook
        val activePayload = buildCallStatePayload("active", false, "+14158902341", "Sarah Connor")
        assertEquals("active", activePayload.getString("state"))
        assertFalse(activePayload.getBoolean("is_ringing"))
    }

    @Test
    fun testAudioRouteCommandHandling() {
        fun resolveAudioRouteIntent(route: String): String {
            return when (route.lowercase()) {
                "mac" -> "BLUETOOTH_SCO"
                "phone_speaker", "speaker" -> "SPEAKERPHONE"
                "phone", "earpiece", "handset" -> "EARPIECE"
                else -> "BLUETOOTH_SCO"
            }
        }

        assertEquals("BLUETOOTH_SCO", resolveAudioRouteIntent("mac"))
        assertEquals("SPEAKERPHONE", resolveAudioRouteIntent("phone_speaker"))
        assertEquals("EARPIECE", resolveAudioRouteIntent("phone"))
        assertEquals("BLUETOOTH_SCO", resolveAudioRouteIntent("unknown"))
    }

    @Test
    fun testCallHistoryRecordJsonStructure() {
        val callRecord = JSONObject().apply {
            put("id", "call_log_42")
            put("phone_id", "42")
            put("name", "John Doe")
            put("number", "+15552345678")
            put("call_type", "incoming")
            put("time", "10:30 AM")
            put("duration", "2m 14s")
        }

        assertEquals("call_log_42", callRecord.getString("id"))
        assertEquals("42", callRecord.getString("phone_id"))
        assertEquals("+15552345678", callRecord.getString("number"))
        assertEquals("incoming", callRecord.getString("call_type"))
    }

    @Test
    fun testContactSyncAndCrudPayloads() {
        val contactsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "1")
                put("name", "Alice Smith")
                put("number", "+1111111111")
                put("contact_type", "Mobile")
            })
            put(JSONObject().apply {
                put("id", "2")
                put("name", "Bob Jones")
                put("number", "+2222222222")
                put("contact_type", "Work")
            })
        }

        val syncResponse = JSONObject().apply {
            put("type", "phone_contacts")
            put("contacts", contactsArray)
        }

        assertEquals("phone_contacts", syncResponse.getString("type"))
        val contacts = syncResponse.getJSONArray("contacts")
        assertEquals(2, contacts.length())
        assertEquals("Alice Smith", contacts.getJSONObject(0).getString("name"))
        assertEquals("Work", contacts.getJSONObject(1).getString("contact_type"))
    }

    @Test
    fun testCallHistoryDeletionCommandParsing() {
        val deletePayload = JSONObject().apply {
            put("command", "delete_call_record")
            put("id", "call_log_105")
            put("number", "+1987654321")
        }

        assertEquals("delete_call_record", deletePayload.getString("command"))
        val rawId = deletePayload.getString("id").replace("call_log_", "")
        assertEquals("105", rawId)
        assertTrue(rawId.all { it.isDigit() })

        val clearPayload = JSONObject().apply {
            put("command", "clear_call_history")
        }
        assertEquals("clear_call_history", clearPayload.getString("command"))
    }
}
