package br.com.ne3d.spbshellmodern.shell3d.carousel

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** Mutually-exclusive physical state, owned by ShellEngine's GL thread. */
enum class CarouselMotionState { IDLE, DRAG, FLING, SNAP, AUTO_ROTATE }

private class CarouselSnapTrack {
    private var start = 0f
    private var target = 0f
    private var elapsed = 0f
    private var duration = 0f
    var active = false
        private set

    fun begin(from: Float, to: Float, durationMillis: Long) {
        start = from; target = to; elapsed = 0f; duration = durationMillis / 1_000f; active = true
    }
    fun tick(dt: Float, easing: (Float) -> Float): Float {
        elapsed = (elapsed + dt).coerceAtMost(duration)
        val fraction = if (duration == 0f) 1f else elapsed / duration
        val result = start + (target - start) * easing(fraction)
        if (fraction >= 1f) active = false
        return result
    }
    fun target(): Float = target
    fun cancel() { active = false }
}

class CarouselPhysics(private val spec: CarouselMotionSpec) {
    var angle = 0f; private set
    var velocity = 0f; private set // degrees per second: input pixels/s × dragToAngleRatio.
    var state = CarouselMotionState.IDLE; private set
    val dragging: Boolean get() = state == CarouselMotionState.DRAG
    private val snap = CarouselSnapTrack()
    private var flingDirection = 0f
    private val snapEasing: (Float) -> Float = { cubicBezierEase(it) }
    internal val snapTargetAngle: Float? get() = if (snap.active) snap.target() else null

    fun beginDrag() { snap.cancel(); velocity = 0f; flingDirection = 0f; state = CarouselMotionState.DRAG }
    fun endDrag() { if (state == CarouselMotionState.DRAG) state = CarouselMotionState.SNAP }
    fun autoRotate(degrees: Float) {
        if (state != CarouselMotionState.DRAG) {
            snap.cancel(); velocity = 0f; flingDirection = 0f; state = CarouselMotionState.AUTO_ROTATE; angle = normalized(angle - degrees)
        }
    }
    fun dragBy(pixels: Float) { if (state == CarouselMotionState.DRAG) angle = normalized(angle + pixels * spec.dragToAngleRatio) }
    fun fling(pixelsPerSecond: Float) {
        if (state != CarouselMotionState.DRAG && state != CarouselMotionState.SNAP) return
        val degreesPerSecond = pixelsPerSecond * spec.dragToAngleRatio
        velocity = degreesPerSecond.coerceIn(-spec.maximumFlingVelocity, spec.maximumFlingVelocity)
        state = if (abs(velocity) >= spec.minimumFlingVelocity * spec.dragToAngleRatio) {
            flingDirection = kotlin.math.sign(velocity)
            CarouselMotionState.FLING
        } else {
            flingDirection = 0f
            CarouselMotionState.SNAP
        }
    }

    fun tick(dtSeconds: Float, panelCount: Int): Boolean {
        if (panelCount <= 0) { state = CarouselMotionState.IDLE; return false }
        when (state) {
            CarouselMotionState.IDLE, CarouselMotionState.AUTO_ROTATE -> return false
            CarouselMotionState.DRAG -> return true
            CarouselMotionState.FLING -> {
                angle = normalized(angle + velocity * dtSeconds)
                velocity *= exp(-spec.friction * dtSeconds)
                if (abs(velocity) <= spec.snapVelocityThreshold) beginSnap(panelCount)
                return true
            }
            CarouselMotionState.SNAP -> {
                if (!snap.active) beginSnap(panelCount)
                angle = snap.tick(dtSeconds, snapEasing)
                if (!snap.active) { angle = normalized(snap.target()); velocity = 0f; flingDirection = 0f; state = CarouselMotionState.IDLE; return false }
                return true
            }
        }
    }
    fun setAngle(value: Float) { snap.cancel(); angle = normalized(value); velocity = 0f; flingDirection = 0f; state = CarouselMotionState.IDLE }
    /** Uses the same snap track and easing as a drag release; never teleports the ring. */
    fun snapToIndex(index: Int, panelCount: Int) {
        if (panelCount <= 0) return
        val step = 360f / panelCount
        val base = -index.mod(panelCount) * step
        val target = base + round((angle - base) / 360f) * 360f
        val distance = abs(target - angle)
        val duration = (spec.snapMinDurationMs + ((spec.snapMaxDurationMs - spec.snapMinDurationMs) * (distance / step).coerceIn(0f, 1f))).toLong()
        snap.begin(angle, target, duration); velocity = 0f; flingDirection = 0f; state = CarouselMotionState.SNAP
    }

    private fun beginSnap(panelCount: Int) {
        val step = 360f / panelCount
        val nearest = round(angle / step) * step
        val target = if (flingDirection != 0f && abs(nearest - angle) > step * spec.snapDirectionDeadZoneRatio && kotlin.math.sign(nearest - angle) != flingDirection) {
            if (flingDirection > 0f) ceil(angle / step) * step else floor(angle / step) * step
        } else nearest
        val distance = abs(target - angle)
        val duration = (spec.snapMinDurationMs + ((spec.snapMaxDurationMs - spec.snapMinDurationMs) * (distance / step).coerceIn(0f, 1f))).toLong()
        snap.begin(angle, target, duration)
        velocity = 0f; state = CarouselMotionState.SNAP
    }
    /** CSS cubic-bezier domain solve: x is time, y is progress. No temporary allocations. */
    private fun cubicBezierEase(progress: Float): Float {
        var t = progress.coerceIn(0f, 1f)
        repeat(5) {
            val x = bezier(t, 0f, spec.snapBezierX1, spec.snapBezierX2, 1f) - progress
            val slope = bezierDerivative(t, 0f, spec.snapBezierX1, spec.snapBezierX2, 1f)
            if (abs(slope) > .0001f) t = (t - x / slope).coerceIn(0f, 1f)
        }
        return bezier(t, 0f, spec.snapBezierY1, spec.snapBezierY2, 1f)
    }
    private fun bezier(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float { val u = 1f - t; return u*u*u*p0 + 3f*u*u*t*p1 + 3f*u*t*t*p2 + t*t*t*p3 }
    private fun bezierDerivative(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float { val u = 1f - t; return 3f*u*u*(p1-p0) + 6f*u*t*(p2-p1) + 3f*t*t*(p3-p2) }
    private fun normalized(value: Float): Float = when { !value.isFinite() -> 0f; value > 720f || value < -720f -> value % 360f; else -> value }
}


