package br.com.ne3d.spbshellmodern.shell3d.widgets

import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import java.util.concurrent.ConcurrentLinkedQueue

/** Main thread may enqueue commands; this controller applies them and mutates scenes only on GL. */
class WidgetSceneController(val scene: WidgetScene, private val source: WidgetDataSource<out WidgetSnapshot>, private val invalidateOnce: () -> Unit, private val onAnimationChanged: (Boolean) -> Unit) {
    private val commands = ConcurrentLinkedQueue<() -> Unit>(); private var lastRevision = Long.MIN_VALUE; private var released=false; private var animationActive=false
    fun enqueue(command: () -> Unit) { if(!released) commands.add(command) }
    fun prepare(context: WidgetSceneContext) { check(!released); scene.prepare(context) }
    fun onSnapshotPublished() { if(!released) invalidateOnce() }
    fun updateProjection(projection: WidgetProjection) { if(!released) scene.updateProjection(projection) }
    fun tick(dtSeconds: Float): Boolean { if(released)return false; while(true){commands.poll()?.invoke() ?: break}; source.latest()?.takeIf{it.revision!=lastRevision}?.let{lastRevision=it.revision;scene.update(it)}; val active=scene.tick(dtSeconds); if(active!=animationActive){animationActive=active;onAnimationChanged(active)}; return active }
    fun pause(){if(!released)scene.pause()}; fun resume(){if(!released)scene.resume()}
    fun release(){if(!released){released=true;scene.release();if(animationActive)onAnimationChanged(false);animationActive=false}}
}
