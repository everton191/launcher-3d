package br.com.ne3d.spbshellmodern.shell3d.widgets.music

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetMaterial
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

class MusicScene : WidgetScene {
 override val id="music"; override val graph=SceneGraph(); override val interactions=InteractionMap(); private lateinit var disc:SceneNode; private var playing=false; private var prepared=false
 override fun prepare(context:WidgetSceneContext){if(prepared)return;prepared=true;graph.root.add(SceneNode("album").apply{mesh=MeshFactory.cube(.8f);material=WidgetMaterial(color=0xFF4B5C83.toInt());local.z=-.15f});disc=graph.root.add(SceneNode("disc").apply{mesh=MeshFactory.uvSphere(24,8,.48f);material=WidgetMaterial(color=0xFF191923.toInt(),emissive=.1f);local.x=.55f;local.z=.18f});graph.updateWorld()}
 override fun update(snapshot:WidgetSnapshot){if(snapshot is MusicSnapshot){playing=snapshot.playing;disc.material?.color=if(playing)0xFF2B2035.toInt() else 0xFF191923.toInt();graph.updateWorld()}}
 override fun tick(dtSeconds:Float):Boolean{if(!playing)return false;disc.local.rotationZ=(disc.local.rotationZ+dtSeconds*24f)%360f;graph.updateWorld();return true}
 override fun pause()=Unit; override fun resume()=Unit; override fun release(){graph.clear();interactions.clear();prepared=false;playing=false}
}
