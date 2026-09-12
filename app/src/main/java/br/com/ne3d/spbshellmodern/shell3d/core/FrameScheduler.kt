package br.com.ne3d.spbshellmodern.shell3d.core

import android.view.Choreographer

/** Reasons that keep the GLSurfaceView active. More than one may be active at once. */
enum class FrameReason { INPUT, PHYSICS, TRANSITION, AUTO_ROTATE, TEXTURE_UPLOAD, WIDGET_ANIMATION }

/** Allocation-free bit set, deliberately kept separate so its rules can be unit tested. */
class FrameReasons {
    private var mask = 0
    fun activate(reason: FrameReason) { mask = mask or reason.bit }
    fun deactivate(reason: FrameReason) { mask = mask and reason.bit.inv() }
    fun contains(reason: FrameReason): Boolean = mask and reason.bit != 0
    fun any(): Boolean = mask != 0
    fun clear() { mask = 0 }
    private val FrameReason.bit: Int get() = 1 shl ordinal
}

/**
 * Main-thread frame gate. It owns Choreographer registration and is the only component that
 * asks GLSurfaceView for a frame. The GL thread consumes a request; it never owns this state.
 */
class FrameScheduler(
    private val choreographer: Choreographer,
    private val requestRender: () -> Unit,
) {
    private val reasons = FrameReasons()
    private var framePosted = false
    private var renderPending = false
    private var oneShotRequested = false
    private var delayedCallback: Choreographer.FrameCallback? = null
    private val frameCallback = Choreographer.FrameCallback {
        framePosted = false
        if (!renderPending && (oneShotRequested || reasons.any())) {
            oneShotRequested = false
            renderPending = true
            requestRender()
        }
        if (reasons.any()) postFrame()
    }

    fun activate(reason: FrameReason) { reasons.activate(reason); postFrame() }
    fun deactivate(reason: FrameReason) { reasons.deactivate(reason) }
    fun invalidateOnce() { oneShotRequested = true; postFrame() }
    fun onRenderConsumed() { renderPending = false; if (oneShotRequested || reasons.any()) postFrame() }
    fun has(reason: FrameReason): Boolean = reasons.contains(reason)

    /** Wakes the render loop once after an idle interval without drawing while waiting. */
    fun wakeOnceAfter(delayMillis: Long, action: () -> Unit) {
        delayedCallback?.let(choreographer::removeFrameCallback)
        val callback = Choreographer.FrameCallback { delayedCallback = null; action() }
        delayedCallback = callback
        choreographer.postFrameCallbackDelayed(callback, delayMillis)
    }

    fun shutdown() {
        framePosted = false
        choreographer.removeFrameCallback(frameCallback)
        delayedCallback?.let(choreographer::removeFrameCallback)
        delayedCallback = null
        reasons.clear()
        oneShotRequested = false
        renderPending = false
    }

    private fun postFrame() {
        if (!framePosted) { framePosted = true; choreographer.postFrameCallback(frameCallback) }
    }
}
