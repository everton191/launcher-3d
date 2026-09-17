package br.com.ne3d.spbshellmodern.shell3d.animation

import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselCircularIndex
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselMotionSpec
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselPhysics
import br.com.ne3d.spbshellmodern.shell3d.presentation.CarouselPresentationRegistry
import br.com.ne3d.spbshellmodern.shell3d.presentation.PanelPresentation
import br.com.ne3d.spbshellmodern.shell3d.presentation.PresentationFrame
import br.com.ne3d.spbshellmodern.shell3d.presentation.PresentationItem
import br.com.ne3d.spbshellmodern.shell3d.presentation.registerLauncherPresentations

/** Autoplay/presentation phases. INTRO/ACTIVE/OUTRO detail lives in [PanelPresentationController]. */
enum class AutoplayPhase { INACTIVE, IDLE_WAIT, ROTATING_TO_NEXT, SETTLING, PRESENTING, BETWEEN_PANELS }

/** Idle boundary plus the circular presentation-autoplay state machine.
 *
 * The machine never fights the user: any interaction aborts the current
 * snap/presentation and restarts the idle wait. It only advances one panel
 * per cycle (logical + 1 → snap → stop → present → return → pause → next).
 * Owned by ShellEngine's GL thread; wall-clock deadlines keep render-on-demand
 * (waits burn no frames, the existing auto-wake re-evaluates them).
 */
class CarouselIdleController(
    private val spec: CarouselMotionSpec = CarouselMotionSpec(),
    private val nowNanos: () -> Long = System::nanoTime,
) {
    enum class State { INTERACTING, SETTLING, IDLE, PANEL_PREVIEW_ACTIVE }
    var state: State = State.IDLE
        private set
    private var autoplayOn: Boolean = true
    val autoplayEnabled: Boolean get() = autoplayOn
    var autoplayPhase: AutoplayPhase = AutoplayPhase.IDLE_WAIT
        private set
    var logicalIndex: Long = 0L
        private set
    val presentation = PanelPresentationController(
        spec.presentationIntroMs, spec.presentationActiveMs, spec.presentationOutroMs, nowNanos
    )
    val presentationEmphasis: Float get() = presentation.emphasis
    /** Live overlay items for the presented panel. Rebuilt on drawn frames only. */
    val liveItems = ArrayList<PresentationItem>()
    val presentations = CarouselPresentationRegistry()
    private var live: PanelPresentation? = null
    private var liveTimeSeconds = 0f
    init {
        registerLauncherPresentations(presentations)
    }
    val isAdvancing: Boolean get() =
        autoplayPhase == AutoplayPhase.ROTATING_TO_NEXT || autoplayPhase == AutoplayPhase.SETTLING
    private var waitStartNanos: Long = nowNanos()
    private var targetLogical = 0L
    private var targetPhysical = 0

    fun onInteraction() {
        state = State.INTERACTING
        abortToWait()
    }

    fun onSettling() {
        state = State.SETTLING
        abortToWait()
    }

    fun onIdle() {
        state = if (presentation.isRunning) State.PANEL_PREVIEW_ACTIVE else State.IDLE
    }

    fun activatePanelPreview() { if (state == State.IDLE) state = State.PANEL_PREVIEW_ACTIVE }

    fun setAutoplayEnabled(enabled: Boolean) {
        autoplayOn = enabled
        if (!enabled) {
            stopLive()
            presentation.cancel()
            autoplayPhase = AutoplayPhase.INACTIVE
        } else if (autoplayPhase == AutoplayPhase.INACTIVE) {
            autoplayPhase = AutoplayPhase.IDLE_WAIT
            waitStartNanos = nowNanos()
        }
    }

    fun syncLogical(logical: Long) {
        logicalIndex = logical
    }

    /** Opening a panel cancels motion/presentation immediately. */
    fun cancelForOpen() {
        stopLive()
        presentation.cancel()
        state = State.INTERACTING
        autoplayPhase = AutoplayPhase.IDLE_WAIT
        waitStartNanos = nowNanos()
    }

    /** Scheduler wake nudge; deadlines are re-evaluated in the same tick. Never resets the wait. */
    fun onAutoplayWakeup(): AutoplayPhase = autoplayPhase

    /** Advances autoplay/presentation. Returns true while frames are still needed. */
    fun tickAutoplay(
        dtSeconds: Float,
        carousel: CarouselPhysics,
        panelCount: Int,
        panelIdAt: (Int) -> String?,
        onPhysicalSettled: (logical: Long, physical: Int) -> Unit,
    ): Boolean {
        if (!autoplayOn || panelCount <= 0) {
            if (autoplayPhase != AutoplayPhase.INACTIVE) {
                stopLive()
                presentation.cancel()
                autoplayPhase = AutoplayPhase.INACTIVE
            }
            return false
        }
        if (autoplayPhase == AutoplayPhase.INACTIVE) {
            autoplayPhase = AutoplayPhase.IDLE_WAIT
            waitStartNanos = nowNanos()
        }
        val now = nowNanos()
        when (autoplayPhase) {
            AutoplayPhase.INACTIVE -> return false
            AutoplayPhase.IDLE_WAIT -> {
                if (now - waitStartNanos >= spec.autoplayIdleDelayMs * 1_000_000L) {
                    targetLogical = logicalIndex + 1L
                    targetPhysical = CarouselCircularIndex.physicalIndex(targetLogical, panelCount)
                    carousel.snapToIndex(targetPhysical, panelCount, spec.autoplaySnapDurationMs)
                    autoplayPhase = AutoplayPhase.ROTATING_TO_NEXT
                    return true
                }
                return false
            }
            AutoplayPhase.ROTATING_TO_NEXT -> {
                if (!carousel.isSettled) return true
                onPhysicalSettled(targetLogical, targetPhysical)
                logicalIndex = targetLogical
                autoplayPhase = AutoplayPhase.SETTLING
                return true
            }
            AutoplayPhase.SETTLING -> {
                if (!carousel.isSettled) {
                    autoplayPhase = AutoplayPhase.ROTATING_TO_NEXT
                    return true
                }
                stopLive()
                presentation.begin(targetLogical, targetPhysical, panelIdAt(targetPhysical).orEmpty())
                liveTimeSeconds = 0f
                val layer = presentations.presentationFor(presentation.state.panelId)
                layer.start(presentation.state)
                live = layer
                autoplayPhase = AutoplayPhase.PRESENTING
                return true
            }
            AutoplayPhase.PRESENTING -> {
                val timelineNeeds = presentation.tick(dtSeconds)
                liveTimeSeconds += dtSeconds
                var liveNeeds = false
                val layer = live
                if (layer != null) {
                    val frame = PresentationFrame(
                        progress = presentation.state.progress,
                        phaseProgress = presentation.state.phaseProgress,
                        dtSeconds = dtSeconds,
                        timeSeconds = liveTimeSeconds,
                    )
                    liveNeeds = layer.update(frame, presentation.state)
                    liveItems.clear()
                    if (presentation.isRunning) layer.collectItems(liveItems)
                }
                if (timelineNeeds || liveNeeds) return true
                if (!presentation.isRunning) {
                    stopLive()
                    waitStartNanos = now
                    autoplayPhase = AutoplayPhase.BETWEEN_PANELS
                }
                return false
            }
            AutoplayPhase.BETWEEN_PANELS -> {
                if (now - waitStartNanos >= spec.betweenPanelsDelayMs * 1_000_000L) {
                    targetLogical = logicalIndex + 1L
                    targetPhysical = CarouselCircularIndex.physicalIndex(targetLogical, panelCount)
                    carousel.snapToIndex(targetPhysical, panelCount, spec.autoplaySnapDurationMs)
                    autoplayPhase = AutoplayPhase.ROTATING_TO_NEXT
                    return true
                }
                return false
            }
        }
    }

    private fun abortToWait() {
        stopLive()
        presentation.cancel()
        autoplayPhase = if (autoplayOn) AutoplayPhase.IDLE_WAIT else AutoplayPhase.INACTIVE
        waitStartNanos = nowNanos()
    }

    private fun stopLive() {
        live?.stop()
        live = null
        liveItems.clear()
        liveTimeSeconds = 0f
    }
}
