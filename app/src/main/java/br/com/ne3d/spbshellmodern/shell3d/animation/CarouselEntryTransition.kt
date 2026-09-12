package br.com.ne3d.spbshellmodern.shell3d.animation

import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselMotionSpec

/** Camera and panel track used before touch physics becomes available. */
class CarouselEntryTransition(private val spec: CarouselMotionSpec) {
    private var elapsedMs = 0f
    var active = true
        private set
    val progress: Float get() {
        val t = (elapsedMs / spec.entryDurationMs).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    private val cameraProgress: Float get() = progress
    private val radiusProgress: Float get() = stagedProgress(spec.entryRadiusDelayMs)
    private val spreadProgress: Float get() = stagedProgress(spec.entrySpreadDelayMs)
    val cameraZ: Float get() = lerp(spec.entryCameraZ, spec.cameraZ, cameraProgress)
    val cameraY: Float get() = lerp(spec.entryCameraY, spec.cameraY, cameraProgress)
    val fov: Float get() = lerp(spec.entryCameraFov, spec.cameraFov, cameraProgress)
    // The selected face stays at the front.  The remaining faces start hidden behind it and
    // unfold sideways into the fixed ring, matching the original SPB opening.
    val radius: Float get() = spec.radius
    val panelSpread: Float get() = spreadProgress
    val sideAlpha: Float get() = stagedProgress(spec.entrySideAlphaDelayMs)
    fun tick(deltaSeconds: Float): Boolean {
        if (!active) return false
        elapsedMs += deltaSeconds * 1_000f
        if (elapsedMs >= spec.entryDurationMs) active = false
        return active
    }
    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t
    private fun stagedProgress(delayMs: Long): Float {
        val t = ((elapsedMs - delayMs) / (spec.entryDurationMs - delayMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
