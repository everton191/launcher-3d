package br.com.ne3d.spbshellmodern.shell3d.widgets.music

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetProjection
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScreenPoint
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionRegion
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.WidgetInteraction
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetMaterial
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

class MusicScene(private val onAction: (Action) -> Unit = {}) : WidgetScene {
 enum class Action { PREVIOUS, TOGGLE, NEXT }
 override val id = "music"; override val graph = SceneGraph(); override val interactions = InteractionMap()
 private lateinit var disc: SceneNode; private lateinit var play: SceneNode; private val controls=ArrayList<SceneNode>(3); private val points=ArrayList<WidgetScreenPoint>(3); private var playing=false; private var prepared=false
 override fun prepare(context: WidgetSceneContext) { if(prepared)return; prepared=true
  graph.root.add(SceneNode("album").apply { mesh=MeshFactory.plane(.45f); material=WidgetMaterial(color=0xFF4B5C83.toInt()); local.z=-.15f })
  disc=graph.root.add(SceneNode("disc").apply { mesh=MeshFactory.uvSphere(24,8,.48f); material=WidgetMaterial(color=0xFF191923.toInt()); local.x=.55f;local.z=.18f })
  Action.entries.forEachIndexed { index, action -> val node=graph.root.add(SceneNode("control-$action").apply { mesh=MeshFactory.cube(.16f);material=WidgetMaterial(color=0xFF6B7FAA.toInt());local.x=(index-1)*.38f;local.y=-.68f;local.z=.1f });controls+=node;points+=WidgetScreenPoint();if(action==Action.TOGGLE)play=node }
  graph.updateWorld()
 }
 override fun update(snapshot: WidgetSnapshot) { if(snapshot is MusicSnapshot){playing=snapshot.playing;disc.material?.color=if(playing)0xFF2B2035.toInt()else 0xFF191923.toInt();play.material?.color=if(playing)0xFFB36A79.toInt()else 0xFF6B7FAA.toInt();graph.updateWorld()} }
 override fun updateProjection(projection: WidgetProjection) { interactions.clear(); controls.forEachIndexed { index,node -> if(projection.projectOrigin(node.worldMatrix,points[index])) { val point=points[index]; val action=Action.entries[index]; interactions.add(InteractionRegion(action.name,point.x-54f,point.y-54f,point.x+54f,point.y+54f){if(it is WidgetInteraction.Tap)onAction(action)}) } } }
 override fun tick(dtSeconds: Float): Boolean { if(!playing)return false;disc.local.rotationZ=(disc.local.rotationZ+dtSeconds*24f)%360f;graph.updateWorld();return true }
 override fun pause()=Unit; override fun resume()=Unit; override fun release(){controls.clear();points.clear();graph.clear();interactions.clear();prepared=false;playing=false}
}
