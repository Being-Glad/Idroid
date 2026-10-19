package com.idroid.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.idroid.app.model.IdroidMessage
import com.idroid.app.model.NotificationPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class IdroidNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        guardValidNotification(sbn) { notification ->
            val message = IdroidMessage(
                type = "notification_posted",
                payload = notification
            )
            val json = Json.encodeToString(message)
            IdroidService.instance?.sendRaw(json)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val id = sbn?.id?.toString() ?: return
        val message = IdroidMessage(
            type = "notification_removed",
            payload = mapOf("notificationId" to id)
        )
        val json = Json.encodeToString(message)
        IdroidService.instance?.sendRaw(json)
    }

    private fun guardValidNotification(sbn: StatusBarNotification?, onValid: (NotificationPayload) -> Unit) {
        if (sbn == null || sbn.isOngoing) return
        val extras = sbn.notification.extras ?: return
        
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        
        if (title.isBlank() && text.isBlank()) return

        val pm = packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val hasReply = sbn.notification.actions?.any { action ->
            action.remoteInputs != null && action.remoteInputs.isNotEmpty()
        } ?: false

        onValid(
            NotificationPayload(
                notificationId = sbn.id.toString(),
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                text = text,
                timestamp = sbn.postTime / 1000,
                hasReplyAction = hasReply
            )
        )
    }
}
