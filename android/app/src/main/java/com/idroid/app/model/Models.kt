package com.idroid.app.model

import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class IdroidMessage<T>(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: String,
    val timestamp: Long = System.currentTimeMillis() / 1000,
    val payload: T
)

@Serializable
data class ClipboardPayload(
    val content: String,
    val contentType: String = "text",
    val sourceDeviceId: String,
    val hash: String = computeHash(content)
) {
    companion object {
        fun computeHash(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

@Serializable
data class NotificationPayload(
    val notificationId: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis() / 1000,
    val hasReplyAction: Boolean = false
)

@Serializable
data class DeviceInfoPayload(
    val deviceId: String,
    val deviceName: String,
    val platform: String = "android",
    val osVersion: String = android.os.Build.VERSION.RELEASE ?: "",
    val appVersion: String = "1.0.0",
    val ipAddress: String,
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null
)

@Serializable
data class MacAppItem(
    val name: String,
    val bundle_id: String = "",
    val path: String = "",
    val category: String = "",
    val icon: String = ""
)
