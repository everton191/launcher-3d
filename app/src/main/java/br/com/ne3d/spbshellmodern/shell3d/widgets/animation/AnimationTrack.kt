package br.com.ne3d.spbshellmodern.shell3d.widgets.animation

fun interface Easing { fun apply(value: Float): Float }
object Easings { val Linear = Easing { it }; val EaseOut = Easing { 1f - (1f-it)*(1f-it) } }

interface AnimationTrack { val active: Boolean; fun tick(dtSeconds: Float): Boolean; fun reset() }

class FloatTrack(private val apply: (Float) -> Unit, private val from: Float, private val to: Float, private val durationSeconds: Float, private val easing: Easing = Easings.Linear, private val loop: Boolean = false) : AnimationTrack {
    private var elapsed = 0f; override var active = true; private set
    override fun tick(dtSeconds: Float): Boolean { if(!active)return false; elapsed += dtSeconds.coerceAtLeast(0f); val progress=(elapsed/durationSeconds.coerceAtLeast(.0001f)).coerceAtMost(1f); apply(from+(to-from)*easing.apply(progress)); if(progress>=1f){ if(loop) elapsed=0f else active=false }; return active }
    override fun reset() { elapsed=0f; active=true; apply(from) }
}

data class Vec3(val x: Float, val y: Float, val z: Float)
class Vec3Track(private val apply: (Float, Float, Float) -> Unit, private val from: Vec3, private val to: Vec3, private val durationSeconds: Float, private val easing: Easing = Easings.Linear, private val loop: Boolean = false) : AnimationTrack {
    private var elapsed = 0f; override var active = true; private set
    override fun tick(dtSeconds: Float): Boolean { if(!active)return false; elapsed += dtSeconds.coerceAtLeast(0f); val p=easing.apply((elapsed/durationSeconds.coerceAtLeast(.0001f)).coerceAtMost(1f)); apply(from.x+(to.x-from.x)*p,from.y+(to.y-from.y)*p,from.z+(to.z-from.z)*p); if(p>=1f){if(loop)elapsed=0f else active=false}; return active }
    override fun reset() { elapsed=0f; active=true; apply(from.x,from.y,from.z) }
}

/** GL-thread owner for local widget animations. */
class WidgetAnimator {
    private val tracks = ArrayList<AnimationTrack>()
    val active: Boolean get() = tracks.isNotEmpty()
    fun play(track: AnimationTrack) { tracks.add(track) }
    fun tick(dtSeconds: Float): Boolean { var i=tracks.size-1; while(i>=0){ if(!tracks[i].tick(dtSeconds)) tracks.removeAt(i); i-- }; return active }
    fun clear() { tracks.clear() }
}
