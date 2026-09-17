package br.com.ne3d.spbshellmodern.shell3d.animation

/** Generic per-panel presentation contract.
 *
 * Deliberately free of widget names (world time, weather, music, system):
 * the launcher uses panel ids, and Simplifica 3D will reuse the same states
 * for its own modules. Widget-specific motion plugs in later.
 */
enum class PanelPresentationPhase { IDLE, INTRO, ACTIVE, OUTRO }

data class PanelPresentationState(
    val logicalIndex: Long = 0L,
    val physicalIndex: Int = 0,
    val panelId: String = "",
    val phase: PanelPresentationPhase = PanelPresentationPhase.IDLE,
    val progress: Float = 0f,
    val phaseProgress: Float = 0f,
)

/** Runs INTRO → ACTIVE → OUTRO for one stopped front panel.
 *
 * INTRO/OUTRO advance with frame time (smooth, reversible); ACTIVE holds
 * until its wall-clock deadline so the renderer can stay idle while the
 * highlight is static. OUTRO always lands back on the exact base pose.
 */
class PanelPresentationController(
    private val introMs: Long,
    private val activeMs: Long,
    private val outroMs: Long,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    constructor(
        introMs: Long = 800L,
        activeMs: Long = 7_000L,
        outroMs: Long = 800L,
    ) : this(introMs, activeMs, outroMs, System::nanoTime)

    var state: PanelPresentationState = PanelPresentationState()
        private set
    private var introElapsedMs = 0f
    private var outroElapsedMs = 0f
    private var activeDeadlineNanos = 0L

    val isRunning: Boolean get() = state.phase != PanelPresentationPhase.IDLE

    /** 0 at rest, eases 0→1 in INTRO, holds 1 in ACTIVE, back to 0 in OUTRO. */
    val emphasis: Float get() = when (state.phase) {
        PanelPresentationPhase.IDLE -> 0f
        PanelPresentationPhase.INTRO -> smoothstep(phaseFraction(introElapsedMs, introMs))
        PanelPresentationPhase.ACTIVE -> 1f
        PanelPresentationPhase.OUTRO -> 1f - smoothstep(phaseFraction(outroElapsedMs, outroMs))
    }

    fun begin(logicalIndex: Long, physicalIndex: Int, panelId: String) {
        introElapsedMs = 0f
        outroElapsedMs = 0f
        activeDeadlineNanos = 0L
        state = PanelPresentationState(
            logicalIndex = logicalIndex,
            physicalIndex = physicalIndex,
            panelId = panelId,
            phase = PanelPresentationPhase.INTRO,
            progress = 0f,
            phaseProgress = 0f,
        )
    }

    fun cancel() {
        introElapsedMs = 0f
        outroElapsedMs = 0f
        activeDeadlineNanos = 0L
        state = PanelPresentationState()
    }

    /** Advances the timeline. Returns true while frames are still needed. */
    fun tick(dtSeconds: Float): Boolean {
        when (state.phase) {
            PanelPresentationPhase.IDLE -> return false
            PanelPresentationPhase.INTRO -> {
                introElapsedMs += dtSeconds * 1_000f
                val fraction = phaseFraction(introElapsedMs, introMs)
                state = state.copy(progress = fraction / 3f, phaseProgress = fraction)
                if (fraction >= 1f) {
                    activeDeadlineNanos = nowNanos() + activeMs * 1_000_000L
                    state = state.copy(phase = PanelPresentationPhase.ACTIVE, progress = 1f / 3f, phaseProgress = 0f)
                }
                return true
            }
            PanelPresentationPhase.ACTIVE -> {
                if (nowNanos() >= activeDeadlineNanos) {
                    outroElapsedMs = 0f
                    state = state.copy(phase = PanelPresentationPhase.OUTRO, progress = 2f / 3f, phaseProgress = 0f)
                    return true
                }
                // Static highlight: no frames burned while waiting out the deadline.
                return false
            }
            PanelPresentationPhase.OUTRO -> {
                outroElapsedMs += dtSeconds * 1_000f
                val fraction = phaseFraction(outroElapsedMs, outroMs)
                state = state.copy(progress = 2f / 3f + fraction / 3f, phaseProgress = fraction)
                if (fraction >= 1f) {
                    state = PanelPresentationState(
                        logicalIndex = state.logicalIndex,
                        physicalIndex = state.physicalIndex,
                        panelId = state.panelId,
                    )
                    return false
                }
                return true
            }
        }
    }

    private fun phaseFraction(elapsedMs: Float, durationMs: Long): Float =
        if (durationMs <= 0L) 1f else (elapsedMs / durationMs).coerceIn(0f, 1f)

    private fun smoothstep(value: Float): Float =
        value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
