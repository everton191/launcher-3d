package br.com.ne3d.spbshellmodern.shell3d.widgets.system

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class ShellNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() = publish(activeNotifications ?: emptyArray())
    override fun onListenerDisconnected() = NotificationBridge.publish(false, emptyList())
    override fun onNotificationPosted(sbn: StatusBarNotification) = publish(activeNotifications ?: emptyArray())
    override fun onNotificationRemoved(sbn: StatusBarNotification) = publish(activeNotifications ?: emptyArray())
    private fun publish(items: Array<StatusBarNotification>) {
        val values = items.take(5).map { item ->
            val notification = item.notification
            NotificationValue(item.key, notification.extras.getCharSequence("android.title")?.toString().orEmpty(), notification.extras.getCharSequence("android.text")?.toString().orEmpty()) to notification.contentIntent
        }
        NotificationBridge.publish(true, values)
    }
}
