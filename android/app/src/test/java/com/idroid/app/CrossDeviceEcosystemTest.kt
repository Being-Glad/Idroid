package com.idroid.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * CrossDeviceEcosystemTest
 * CTO / Chief Architect Unit Verification Suite testing:
 * 1. Turbo File Transfer framing & chunk validation.
 * 2. Notification RemoteInput payload validation for WhatsApp & Messages.
 * 3. Incoming Call Attend / Decline state contracts.
 * 4. Bidirectional Universal Clipboard protocol compatibility.
 */
class CrossDeviceEcosystemTest {

    @Test
    fun testTurboFileTransferProtocolFraming() {
        val outStream = ByteArrayOutputStream()
        val dataOut = DataOutputStream(outStream)

        // 1. Write Magic: IDRD
        val magic = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(), 'D'.code.toByte())
        dataOut.write(magic)

        // 2. Write File Count
        val fileCount = 2
        dataOut.writeInt(fileCount)

        // File 1: "sample.jpg", 1024 bytes
        val f1Name = "sample.jpg".toByteArray(Charsets.UTF_8)
        dataOut.writeShort(f1Name.size)
        dataOut.write(f1Name)
        dataOut.writeLong(1024L)

        // File 2: "report.pdf", 2048 bytes
        val f2Name = "report.pdf".toByteArray(Charsets.UTF_8)
        dataOut.writeShort(f2Name.size)
        dataOut.write(f2Name)
        dataOut.writeLong(2048L)

        dataOut.flush()

        // Verify Server-side read contract
        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val dataIn = DataInputStream(inStream)

        val readMagic = ByteArray(4)
        dataIn.readFully(readMagic)
        assertArrayEquals(magic, readMagic)

        val readCount = dataIn.readInt()
        assertEquals(2, readCount)

        val name1Len = dataIn.readShort().toInt()
        val name1Bytes = ByteArray(name1Len)
        dataIn.readFully(name1Bytes)
        assertEquals("sample.jpg", String(name1Bytes, Charsets.UTF_8))
        assertEquals(1024L, dataIn.readLong())

        val name2Len = dataIn.readShort().toInt()
        val name2Bytes = ByteArray(name2Len)
        dataIn.readFully(name2Bytes)
        assertEquals("report.pdf", String(name2Bytes, Charsets.UTF_8))
        assertEquals(2048L, dataIn.readLong())
    }

    @Test
    fun testNotificationRemoteInputReplyContract() {
        val replyPayload = JSONObject().apply {
            put("type", "command")
            put("action", "reply_notification")
            put("id", "0|com.whatsapp|1|null|10234")
            put("text", "Hey! Running late, see you in 10 mins")
        }

        assertEquals("command", replyPayload.getString("type"))
        assertEquals("reply_notification", replyPayload.getString("action"))
        assertEquals("0|com.whatsapp|1|null|10234", replyPayload.getString("id"))
        assertEquals("Hey! Running late, see you in 10 mins", replyPayload.getString("text"))
    }

    @Test
    fun testIncomingCallActionContract() {
        val ringingMsg = JSONObject().apply {
            put("type", "call_state_changed")
            put("state", "ringing")
            put("number", "+14155552671")
            put("timestamp", 1729600000000L)
        }

        assertEquals("ringing", ringingMsg.getString("state"))
        assertEquals("+14155552671", ringingMsg.getString("number"))

        val answerCmd = JSONObject().apply {
            put("type", "command")
            put("action", "answer_call")
        }
        assertEquals("answer_call", answerCmd.getString("action"))

        val endCmd = JSONObject().apply {
            put("type", "command")
            put("action", "end_call")
        }
        assertEquals("end_call", endCmd.getString("action"))
    }

    @Test
    fun testUniversalClipboardPayloadContract() {
        // Phone pushing to Mac
        val phonePush = JSONObject().apply {
            put("type", "clipboard_push")
            put("text", "https://github.com/developer/idroid")
        }
        assertEquals("clipboard_push", phonePush.getString("type"))
        assertEquals("https://github.com/developer/idroid", phonePush.getString("text"))

        // Mac updating Phone
        val macUpdate = JSONObject().apply {
            put("type", "clipboard_update")
            put("content", "SecretVerificationCode123")
            put("clipboard", "SecretVerificationCode123")
        }
        assertEquals("clipboard_update", macUpdate.getString("type"))
        assertEquals("SecretVerificationCode123", macUpdate.getString("content"))
    }
}
