package br.com.ne3d.spbshellmodern.shell3d.core

import android.view.Choreographer
import java.util.IdentityHashMap

enum class FrameReason { INPUT, PHYSICS, TRANSITION, AUTO_ROTATE, TEXTURE_UPLOAD, WIDGET_ANIMATION }

class FrameReasons {
    private var mask = 0
    fun activate(reason: FrameReason) { mask = mask or reason.bit }
    fun deactivate(reason: FrameReason) { mask = mask and reason.bit.inv() }
    fun contains(reason: FrameReason): Boolean = mask and reason.bit != 0
    fun any(): Boolean = mask != 0
    fun clear() { mask = 0 }
    private val FrameReason.bit: Int get() = 1 shl ordinal
}

/** Small clock boundary so the scheduler state machine can be unit tested without Android. */
interface FrameClock {
    fun postFrame(callback: () -> Unit)
    fun postFrameDelayed(delayMillis: Long, callback: () -> Unit)
    fun remove(callback: () -> Unit)
}

class ChoreographerFrameClock(private val choreographer: Choreographer) : FrameClock {
    private val callbacks = IdentityHashMap<() -> Unit, Choreographer.FrameCallback>()
    override fun postFrame(callback: () -> Unit) = post(callback, 0L)
    override fun postFrameDelayed(delayMillis: Long, callback: () -> Unit) = post(callback, delayMillis)
    override fun remove(callback: () -> Unit) { callbacks.remove(callback)?.let(choreographer::removeFrameCallback) }
    private fun post(callback: () -> Unit, delayMillis: Long) {
        val frameCallback = callbacks.getOrPut(callback) { Choreographer.FrameCallback { callbacks.remove(callback); callback() } }
        if (delayMillis == 0L) choreographer.postFrameCallback(frameCallback) else choreographer.postFrameCallbackDelayed(frameCallback, delayMillis)
    }
}

class FrameScheduler(
    private val clock: FrameClock,
    private val requestRender: () -> Unit,
) {
    constructor(choreographer: Choreographer, requestRender: () -> Unit) : this(ChoreographerFrameClock(choreographer), requestRender)
    private val reasons = FrameReasons()
    private var framePosted = false
    private var renderPending = false
    private var oneShotRequested = false
    private var delayedWake: (() -> Unit)? = null
    private var stopped = false
    private val frameCallback: () -> Unit = {
        framePosted = false
        if (!stopped && !renderPending && (oneShotRequested || reasons.any())) {
            oneShotRequested = false; renderPending = true; requestRender()
        }
        if (!stopped && reasons.any()) postFrame()
    }

    fun activate(reason: FrameReason) { if (!stopped) { reasons.activate(reason); postFrame() } }
    fun deactivate(reason: FrameReason) { reasons.deactivate(reason) }
    fun invalidateOnce() { if (!stopped) { oneShotRequested = true; postFrame() } }
    fun onRenderConsumed() { renderPending = false; if (!stopped && (oneShotRequested || reasons.any())) postFrame() }
    fun has(reason: FrameReason): Boolean = reasons.contains(reason)
    fun cancelDelayedWake() { delayedWake?.let(clock::remove); delayedWake = null }
    fun wakeOnceAfter(delayMillis: Long, action: () -> Unit) {
        if (stopped) return
        cancelDelayedWake()
        val callback: () -> Unit = { delayedWake = null; if (!stopped) action() }
        delayedWake = callback
        clock.postFrameDelayed(delayMillis, callback)
    }
    fun shutdown() {
        stopped = true; framePosted = false; clock.remove(frameCallback); cancelDelayedWake()
        reasons.clear(); oneShotRequested = false; renderPending = false
    }
    private fun postFrame() { if (!framePosted) { framePosted = true; clock.postFrame(frameCallback) } }
}
