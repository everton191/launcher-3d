package br.com.ne3d.spbshellmodern.shell3d.widgets.personal

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetMaterial
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

abstract class PersonalScene(private val sceneId:String, private val color:Int):WidgetScene{
 override val id=sceneId;override val graph=SceneGraph();override val interactions=InteractionMap();private val cards=ArrayList<SceneNode>(6);private var prepared=false
 override fun prepare(context:WidgetSceneContext){if(prepared)return;prepared=true;var i=0;while(i<6){cards+=graph.root.add(SceneNode("card-$i").apply{mesh=MeshFactory.cube(.25f);material=WidgetMaterial(color=color);local.x=-.65f+i*.26f;local.y=if(i%2==0).12f else -.12f;local.z=-i*.08f;local.alpha=0f});i++};graph.updateWorld()}
 override fun update(snapshot:WidgetSnapshot){val count=when(snapshot){is CalendarSnapshot->snapshot.events.size;is PhotosSnapshot->snapshot.photos.size;is ContactsSnapshot->snapshot.contacts.size;else->0};var i=0;while(i<cards.size){cards[i].local.alpha=if(i<count)1f else 0f;i++};graph.updateWorld()}
 override fun tick(dtSeconds:Float)=false;override fun pause()=Unit;override fun resume()=Unit;override fun release(){cards.clear();graph.clear();interactions.clear();prepared=false}
}
class CalendarScene:PersonalScene("calendar",0xFF8B78C4.toInt())
class PhotosScene:PersonalScene("photos",0xFF5BAAA8.toInt())
class ContactsScene:PersonalScene("contacts",0xFFCD8C5A.toInt())
