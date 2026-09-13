package br.com.ne3d.spbshellmodern.shell3d.widgets

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph

enum class WidgetSceneLifecycle { PREPARED, PAUSED, RESUMED, RELEASED }
class WidgetSceneContext(val density: Float, val viewportWidth: Int, val viewportHeight: Int, val invalidateOnce: () -> Unit, val meshFactory: MeshFactory = MeshFactory)

/** Contract owned by the GL thread after prepare. Producers communicate only via snapshots/commands. */
interface WidgetScene {
    val id: String
    val graph: SceneGraph
    val interactions: InteractionMap
    fun prepare(context: WidgetSceneContext)
    fun update(snapshot: WidgetSnapshot)
    fun tick(dtSeconds: Float): Boolean
    fun pause()
    fun resume()
    fun release()
}
