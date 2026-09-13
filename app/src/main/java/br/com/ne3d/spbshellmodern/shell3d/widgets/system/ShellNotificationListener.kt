package br.com.ne3d.spbshellmodern.shell3d.widgets.system

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** System-owned notification bridge. It keeps only a small live summary and never persists it. */
class ShellNotificationListener : NotificationListenerService() {
    private val source = NotificationDataSource()
    override fun onListenerConnected() = publish(activeNotifications ?: emptyArray())
    override fun onNotificationPosted(sbn: StatusBarNotification) = publish(activeNotifications ?: emptyArray())
    override fun onNotificationRemoved(sbn: StatusBarNotification) = publish(activeNotifications ?: emptyArray())
    private fun publish(items: Array<StatusBarNotification>) {
        val values=ArrayList<NotificationValue>(5); var i=0
        while(i<items.size && values.size<5){val n=items[i].notification;values+=NotificationValue(items[i].key,n.extras.getCharSequence("android.title")?.toString().orEmpty(),n.extras.getCharSequence("android.text")?.toString().orEmpty());i++}
        source.publishNotifications(true,values)
    }
}
