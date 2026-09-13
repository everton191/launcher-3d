package br.com.ne3d.spbshellmodern.shell3d.effects

enum class PanelTransitionState { IDLE, CAPTURE_PENDING, GL_OPENING, COMPOSE_LIVE, GL_CLOSING }

/** Single-owner transition state. Callers only advance it with elapsed time. */
class PanelTransitionController {
    var state: PanelTransitionState = PanelTransitionState.IDLE
        private set
    var mode: PanelEffectMode = PanelEffectMode.NONE
        private set
    var progress: Float = 0f
        private set
    var direction: Float = 1f
        private set
    var selectedPanel: Int = -1
        private set
    var crossfadeProgress: Float = 0f
        private set
    private var composeReady = false

    val blocksInput: Boolean get() = state == PanelTransitionState.CAPTURE_PENDING || state == PanelTransitionState.GL_OPENING || state == PanelTransitionState.GL_CLOSING
    val composeVisible: Boolean get() = state == PanelTransitionState.CAPTURE_PENDING || state == PanelTransitionState.COMPOSE_LIVE
    val phase: EffectPhase get() = when (state) { PanelTransitionState.GL_OPENING -> EffectPhase.OPENING; PanelTransitionState.GL_CLOSING -> EffectPhase.CLOSING; else -> EffectPhase.NONE }

    fun requestOpen(panel: Int, effect: PanelEffectMode) {
        if (state != PanelTransitionState.IDLE) return
        selectedPanel = panel; mode = effect; progress = 0f; crossfadeProgress = 0f; direction = 1f; state = PanelTransitionState.CAPTURE_PENDING
    }
    fun onTextureReady() {
        if (state == PanelTransitionState.CAPTURE_PENDING) state = if (direction > 0f) PanelTransitionState.GL_OPENING else PanelTransitionState.GL_CLOSING
    }
    fun requestClose() {
        if (state != PanelTransitionState.COMPOSE_LIVE) return
        direction = -1f; progress = 1f; state = PanelTransitionState.CAPTURE_PENDING
    }
    fun tick(dtSeconds: Float) {
        val duration = when (mode) { PanelEffectMode.STACK -> .260f; PanelEffectMode.FOLD -> .320f; PanelEffectMode.ORIGAMI -> .360f; PanelEffectMode.NONE -> .100f }
        when (state) {
            PanelTransitionState.GL_OPENING -> { progress = (progress + dtSeconds / duration).coerceAtMost(1f); crossfadeProgress = (crossfadeProgress + dtSeconds / CROSSFADE_SECONDS).coerceAtMost(1f); if (progress == 1f) { state = PanelTransitionState.COMPOSE_LIVE; composeReady = true } }
            PanelTransitionState.GL_CLOSING -> { progress = (progress - dtSeconds / duration).coerceAtLeast(0f); crossfadeProgress = (crossfadeProgress + dtSeconds / CROSSFADE_SECONDS).coerceAtMost(1f); if (progress == 0f) reset() }
            else -> Unit
        }
    }
    fun release() = reset()
    fun consumeComposeReady(): Boolean = composeReady.also { composeReady = false }
    private fun reset() { state = PanelTransitionState.IDLE; mode = PanelEffectMode.NONE; progress = 0f; crossfadeProgress = 0f; direction = 1f; selectedPanel = -1; composeReady = false }
    private companion object { const val CROSSFADE_SECONDS = .100f }
}
