package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetFramingProvider
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetProjection
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetMaterial
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

/**
 * Cena da lua: mesmo sistema do globo ([WorldTimeScene]), mas com tema lunar.
 *
 * Esfera com [MoonTexture] original (regolito + crateras), rotação lenta
 * contínua e halo discreto ([MoonGlowTexture]) atrás da esfera. Nenhum
 * asset do SPB/Yandex é usado; nenhum segundo motor é criado.
 */
class MoonScene : WidgetScene {
    override val id = MoonWidgetIds.MOON
    override val graph = SceneGraph(SceneNode("moon-root"))
    override val interactions = InteractionMap()
    private lateinit var moon: SceneNode
    private lateinit var glow: SceneNode
    private var projection: WidgetProjection? = null
    private var idleSeconds = 8f
    private var prepared = false

    override fun prepare(context: WidgetSceneContext) {
        if (prepared) return
        prepared = true
        val framing = WidgetFramingProvider.framingFor(id)
        graph.root.local.scaleX = framing.sceneScale
        graph.root.local.scaleY = framing.sceneScale
        graph.root.local.scaleZ = framing.sceneScale
        moon = graph.root.add(SceneNode("moon").apply {
            mesh = MeshFactory.uvSphere(28, 18, 1f)
            material = WidgetMaterial(textureRef = MoonTexture.ref)
        })
        glow = graph.root.add(SceneNode("moon-glow").apply {
            mesh = MeshFactory.plane(1.45f)
            material = WidgetMaterial(textureRef = MoonGlowTexture.ref, alpha = .55f)
            // Colado atrás da esfera (anel visível na silhueta via depth) e
            // acima do corte do collectItems (world z >= -0.05).
            local.z = -.04f
        })
        graph.updateWorld()
    }

    override fun updateProjection(projection: WidgetProjection) {
        this.projection = projection
    }

    override fun update(snapshot: WidgetSnapshot) = Unit

    override fun tick(dtSeconds: Float): Boolean {
        if (idleSeconds <= 0f) {
            graph.updateWorld()
            return false
        }
        idleSeconds = (idleSeconds - dtSeconds).coerceAtLeast(0f)
        moon.local.rotationY = (moon.local.rotationY + dtSeconds * 5f) % 360f
        graph.updateWorld()
        return idleSeconds > 0f
    }

    override fun pause() {
        idleSeconds = 0f
    }

    override fun resume() {
        idleSeconds = 2f
    }

    override fun release() {
        graph.clear()
        interactions.clear()
        prepared = false
        idleSeconds = 0f
        projection = null
    }
}

object MoonWidgetIds {
    const val MOON = "moon"
}
