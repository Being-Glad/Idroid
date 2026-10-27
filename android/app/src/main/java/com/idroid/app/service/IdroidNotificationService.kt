package com.idroid.app.service

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.idroid.app.model.IdroidMessage
import com.idroid.app.model.NotificationPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class IdroidNotificationService : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: IdroidNotificationService? = null
            private set

        /**
         * Executes an inline remote input reply to an active notification (e.g. WhatsApp, Messages)
         * without launching the app on the phone screen.
         */
        fun replyToNotification(notifKey: String, text: String): Boolean {
            val service = instance ?: return false
            try {
                val sbns = service.activeNotifications ?: return false
                val targetSbn = sbns.find {
                    it.key == notifKey || it.id.toString() == notifKey || it.packageName == notifKey
                } ?: return false

                val actions = targetSbn.notification.actions ?: return false
                for (action in actions) {
                    val remoteInputs = action.remoteInputs ?: continue
                    if (remoteInputs.isNotEmpty()) {
                        val intent = Intent()
                        val bundle = Bundle()
                        for (input in remoteInputs) {
                            bundle.putCharSequence(input.resultKey, text)
                        }
                        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                        action.actionIntent.send(service, 0, intent)
                        return true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    private fun isCallNotification(sbn: StatusBarNotification?): Boolean {
        if (sbn == null) return false
        val pkg = sbn.packageName ?: ""
        val cat = sbn.notification?.category
        return pkg == "com.samsung.android.incallui" ||
                pkg == "com.google.android.dialer" ||
                pkg == "com.samsung.android.dialer" ||
                pkg == "com.android.phone" ||
                pkg == "com.android.server.telecom" ||
                cat == android.app.Notification.CATEGORY_CALL
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        if (isCallNotification(sbn)) {
            val extras = sbn.notification.extras
            val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                ?: extras?.getCharSequence("android.title")?.toString() ?: ""
            val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
                ?: extras?.getCharSequence("android.text")?.toString() ?: ""
            val whenMs = sbn.notification.`when`
            IdroidService.instance?.updateActiveCallFromNotification(title, text, whenMs)
            return
        }

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
        if (sbn == null) return

        if (isCallNotification(sbn)) {
            IdroidService.instance?.onCallNotificationRemoved()
            return
        }

        val id = sbn.key ?: sbn.id.toString()
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
                notificationId = sbn.key ?: sbn.id.toString(),
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
