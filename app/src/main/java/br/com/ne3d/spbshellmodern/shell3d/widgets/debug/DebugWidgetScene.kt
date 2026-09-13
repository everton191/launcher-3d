package br.com.ne3d.spbshellmodern.shell3d.widgets.debug

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.animation.FloatTrack
import br.com.ne3d.spbshellmodern.shell3d.widgets.animation.WidgetAnimator
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.LatestWidgetDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionRegion
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.WidgetInteraction
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetMaterial
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

data class DebugWidgetSnapshot(override val revision: Long, val counter: Int, val label: String, val logicalTimestamp: Long) : WidgetSnapshot
class DebugWidgetDataSource : LatestWidgetDataSource<DebugWidgetSnapshot>() { private var revision=0L; fun advance() { publish(DebugWidgetSnapshot(++revision, revision.toInt(), "debug-$revision", revision)) } }

/** Debug-only proof scene: Sphere -> Cube parentage and an independent HexTile. */
class DebugWidgetScene : WidgetScene {
    override val id = "debug-widget-scene"; override val graph=SceneGraph(); override val interactions=InteractionMap(); private val animator=WidgetAnimator(); private var prepared=false; private var spinning=false; private lateinit var sphere: SceneNode; private lateinit var hex: SceneNode
    override fun prepare(context: WidgetSceneContext) { if(prepared)return; prepared=true
        sphere=graph.root.add(SceneNode("sphere").apply{mesh=MeshFactory.uvSphere();material=WidgetMaterial(color=0xFF4FA8FF.toInt());local.x=-.45f})
        sphere.add(SceneNode("cube").apply{mesh=MeshFactory.cube(.35f);material=WidgetMaterial(color=0xFFFFC247.toInt());local.y=.9f})
        hex=graph.root.add(SceneNode("hex").apply{mesh=MeshFactory.hexTile(.45f);material=WidgetMaterial(color=0xFF7EE081.toInt());local.x=1.15f})
        interactions.add(InteractionRegion("sphere",-.95f,-1f,.2f,1f,true){ event -> if(event is WidgetInteraction.Tap) toggleSpin() })
        interactions.add(InteractionRegion("hex",.6f,-.6f,1.7f,.6f){ event -> if(event is WidgetInteraction.Tap) pulseHex() })
        graph.updateWorld()
    }
    override fun update(snapshot: WidgetSnapshot) { if(snapshot is DebugWidgetSnapshot) hex.material?.emissive=(snapshot.counter%2).toFloat() }
    override fun tick(dtSeconds: Float): Boolean { if(spinning) sphere.local.rotationY=(sphere.local.rotationY+dtSeconds*55f)%360f; val animating=animator.tick(dtSeconds); graph.updateWorld(); return spinning||animating }
    override fun pause() = Unit; override fun resume() = Unit
    override fun release() { animator.clear(); interactions.clear(); graph.clear(); prepared=false; spinning=false }
    fun toggleSpin(){spinning=!spinning}; fun pulseHex(){animator.play(FloatTrack({hex.local.scaleX=it;hex.local.scaleY=it;hex.local.scaleZ=it},1f,1.25f,.18f))}
}
