package br.com.ne3d.spbshellmodern.shell3d.widgets.data

/** Immutable, View-free payload exchanged from UI/background producers to the GL owner. */
interface WidgetSnapshot { val revision: Long }

interface WidgetDataSource<S : WidgetSnapshot> { fun latest(): S? }

/** Keeps only the most recent state: producers never queue unbounded work for the GL thread. */
open class LatestWidgetDataSource<S : WidgetSnapshot> : WidgetDataSource<S> {
    private val latest = java.util.concurrent.atomic.AtomicReference<S?>(null)
    fun publish(snapshot: S) { latest.set(snapshot) }
    override fun latest(): S? = latest.get()
}
