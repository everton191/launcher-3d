package br.com.ne3d.spbshellmodern.shell3d.widgets.system

import android.app.PendingIntent
import java.util.concurrent.CopyOnWriteArraySet

/** Process-local live notification handoff. It stores only the current bounded summaries. */
object NotificationBridge {
    private data class Entry(val value: NotificationValue, val pendingIntent: PendingIntent?)
    private val sources = CopyOnWriteArraySet<NotificationDataSource>()
    @Volatile private var available = false
    @Volatile private var entries: List<Entry> = emptyList()
    fun register(source: NotificationDataSource) { sources += source; source.publishNotifications(available, entries.map { it.value }) }
    fun unregister(source: NotificationDataSource) { sources -= source }
    fun publish(authorized: Boolean, values: List<Pair<NotificationValue, PendingIntent?>>) {
        available = authorized; entries = values.take(5).map { Entry(it.first, it.second) }
        sources.forEach { it.publishNotifications(available, entries.map { entry -> entry.value }) }
    }
    fun open(key: String) { runCatching { entries.firstOrNull { it.value.key == key }?.pendingIntent?.send() } }
}
