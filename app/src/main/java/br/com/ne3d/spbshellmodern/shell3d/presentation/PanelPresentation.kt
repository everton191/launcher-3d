package br.com.ne3d.spbshellmodern.shell3d.presentation

import br.com.ne3d.spbshellmodern.shell3d.animation.PanelPresentationPhase
import br.com.ne3d.spbshellmodern.shell3d.animation.PanelPresentationState
import br.com.ne3d.spbshellmodern.shell3d.scene.RenderMesh
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetTextureRef

/** One frame of presentation input. Produced by the autoplay state machine. */
data class PresentationFrame(
    val progress: Float = 0f,
    val phaseProgress: Float = 0f,
    val dtSeconds: Float = 0f,
    /** Presentation-local clock. Advances only on drawn frames (render-on-demand). */
    val timeSeconds: Float = 0f,
)

/** One piece of live geometry in PANEL-LOCAL space.
 *
 * The card face spans x in [-1, 1], y in [-panelHalfHeight, +panelHalfHeight]
 * with its surface at z = 0. The local matrix must keep geometry inside those
 * bounds and in front of the face (z > 0). The renderer draws each item as
 * panelModelMatrix x localMatrix, so the animation follows the card. Matrix
 * arrays are freshly composed per frame by the presentation; the renderer
 * never mutates them.
 */
data class PresentationItem(
    val mesh: RenderMesh,
    val color: Int = 0xFFFFFFFF.toInt(),
    val alpha: Float = 1f,
    val textureRef: WidgetTextureRef? = null,
    val localMatrix: FloatArray = PresentationMatrices.identity(),
    val visible: Boolean = true,
)

/** Live overlay for the front panel. Generic: knows panel ids, never
 * HomeScreen, Compose, ERP or widget datasources. Implementations wrap the
 * existing WidgetScenes (same tick, same nodes, same textures) or own a few
 * meshes for entry motion. All calls happen on the GL thread; mesh allocation
 * is buffer-only work, safe anywhere.
 */
interface PanelPresentation {
    val presentationId: String
    /** Allocate meshes / prepare the wrapped scene. Buffer-only work, safe anywhere. */
    fun prepare()
    fun start(state: PanelPresentationState)
    /** Advance by one frame. Returns true while the motion still needs frames. */
    fun update(frame: PresentationFrame, state: PanelPresentationState): Boolean
    /** Append this frame's items (nothing when fully faded out). */
    fun collectItems(out: MutableList<PresentationItem>)
    /** Reset to base immediately; after this nothing may be drawn. */
    fun stop()
    fun release()
}

/** Fallback for panels without animation. Stateless and reusable. */
object NoPresentation : PanelPresentation {
    override val presentationId: String = "none"
    override fun prepare() = Unit
    override fun start(state: PanelPresentationState) = Unit
    override fun update(frame: PresentationFrame, state: PanelPresentationState): Boolean = false
    override fun collectItems(out: MutableList<PresentationItem>) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}
