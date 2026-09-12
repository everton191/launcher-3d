package br.com.ne3d.spbshellmodern.shell3d.animation

/** State boundary for future preview-only widget animation; no widget work runs in this phase. */
class CarouselIdleController {
    enum class State { INTERACTING, SETTLING, IDLE, PANEL_PREVIEW_ACTIVE }
    var state: State = State.IDLE
        private set
    fun onInteraction() { state = State.INTERACTING }
    fun onSettling() { state = State.SETTLING }
    fun onIdle() { state = State.IDLE }
    fun activatePanelPreview() { if (state == State.IDLE) state = State.PANEL_PREVIEW_ACTIVE }
}
