package br.com.ne3d.spbshellmodern.shell3d.animation

interface AnimationTrack { fun value(elapsedMs: Long): Float; val finished: Boolean }
class LinearTrack(private val from: Float, private val to: Float, private val durationMs: Long) : AnimationTrack { private var elapsed=0L; override val finished get()=elapsed>=durationMs; override fun value(elapsedMs:Long):Float { elapsed=elapsedMs; return from+(to-from)*(elapsedMs.toFloat()/durationMs).coerceIn(0f,1f) } }
class SpringTrack(private val target: Float) : AnimationTrack { private var last=0f; override val finished get()=kotlin.math.abs(target-last)<.001f; override fun value(elapsedMs:Long):Float { last += (target-last)*.18f; return last } }
