package br.com.ne3d.spbshellmodern.shell3d.animation

import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselMotionSpec

/** Reverses the entry camera track while retaining the selected texture until the host returns to Compose. */
class CarouselExitTransition(private val spec: CarouselMotionSpec) {
    private var elapsedMs = 0f
    var active = false
        private set
    var completed = false
        private set
    val progress: Float get() {
        val t = (elapsedMs / spec.exitDurationMs).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    val cameraZ: Float get() = lerp(spec.cameraZ, spec.entryCameraZ, progress)
    val cameraY: Float get() = lerp(spec.cameraY, spec.entryCameraY, progress)
    val fov: Float get() = lerp(spec.cameraFov, spec.entryCameraFov, progress)
    val radius: Float get() = lerp(spec.radius, 0f, progress)
    val panelSpread: Float get() = 1f - progress
    fun begin() { elapsedMs = 0f; active = true; completed = false }
    fun tick(deltaSeconds: Float): Boolean {
        if (!active) return false
        elapsedMs += deltaSeconds * 1_000f
        if (elapsedMs >= spec.exitDurationMs) { active = false; completed = true }
        return active
    }
    fun consumeCompleted(): Boolean = completed.also { completed = false }
    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t
}
