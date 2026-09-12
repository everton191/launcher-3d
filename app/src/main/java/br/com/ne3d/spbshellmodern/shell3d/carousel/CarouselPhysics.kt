package br.com.ne3d.spbshellmodern.shell3d.carousel

import kotlin.math.*

class CarouselPhysics(private val spec: CarouselMotionSpec) {
    // ShellEngine serializes every mutation on the GL thread.
    var angle = 0f; private set
    private var velocity = 0f
    var dragging = false; private set
    private var autoRotating = false
    fun beginDrag() { dragging = true; autoRotating = false; velocity = 0f }
    fun endDrag() { dragging = false }
    fun autoRotate(degrees: Float) { if (!dragging) { autoRotating = true; angle = normalized(angle - degrees); velocity = 0f } }
    fun dragBy(pixels: Float) { angle = normalized(angle + pixels * spec.dragToAngleRatio); velocity = 0f }
    fun fling(pixelsPerSecond: Float) {
        velocity = (pixelsPerSecond * spec.dragToAngleRatio).coerceIn(-spec.maximumFlingVelocity, spec.maximumFlingVelocity)
        if (abs(velocity) < spec.minimumFlingVelocity * spec.dragToAngleRatio) velocity = 0f
    }
    fun tick(dtSeconds: Float, panelCount: Int): Boolean {
        if (panelCount <= 0) return false
        // Never fight the finger: snapping is strictly a release behavior.
        if (dragging) return false
        // The idle orbit is deliberately constant; snapping resumes only after touch.
        if (autoRotating) return false
        if (abs(velocity) > spec.settleThreshold) {
            angle = normalized(angle + velocity * dtSeconds)
            velocity *= exp(-spec.friction * dtSeconds)
            return true
        }
        val step = 360f / panelCount
        val target = round(angle / step) * step
        val error = target - angle
        if (abs(error) <= spec.settleThreshold) { angle = target; return false }
        angle = normalized(angle + error * min(1f, dtSeconds * 1000f / spec.snapDurationMs))
        return true
    }
    fun setAngle(value: Float) { angle = normalized(value); velocity = 0f }
    private fun normalized(value: Float): Float = when {
        !value.isFinite() -> 0f
        value > 720f || value < -720f -> value % 360f
        else -> value
    }
}
