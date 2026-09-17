package br.com.ne3d.spbshellmodern.shell3d.widgets.personal

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.scene.RenderMesh
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetMaterial
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

abstract class PersonalScene(private val sceneId:String, private val color:Int):WidgetScene{
 override val id=sceneId;override val graph=SceneGraph();override val interactions=InteractionMap();private val cards=ArrayList<SceneNode>(6);private var prepared=false
 protected open fun meshFor(): RenderMesh = MeshFactory.cube(.25f)
 protected open fun materialFor(index:Int): WidgetMaterial = WidgetMaterial(color=color)
 protected open fun layoutCard(node:SceneNode,index:Int){node.local.x=-.65f+index*.26f;node.local.y=if(index%2==0).12f else -.12f;node.local.z=-index*.08f}
 protected open fun onPrepared(){}
 override fun prepare(context:WidgetSceneContext){if(prepared)return;prepared=true;var i=0;while(i<6){val node=SceneNode("card-$i").apply{mesh=meshFor();material=materialFor(i);layoutCard(this,i);local.alpha=0f};cards+=graph.root.add(node);i++};onPrepared();graph.updateWorld()}
 override fun update(snapshot:WidgetSnapshot){val count=when(snapshot){is CalendarSnapshot->snapshot.events.size;is PhotosSnapshot->snapshot.photos.size;is ContactsSnapshot->snapshot.contacts.size;else->0};var i=0;while(i<cards.size){cards[i].local.alpha=if(i<count)1f else 0f;i++};graph.updateWorld()}
 override fun tick(dtSeconds:Float)=false;override fun pause()=Unit;override fun resume()=Unit;override fun release(){cards.clear();graph.clear();interactions.clear();prepared=false}
 protected fun cardAt(index:Int): SceneNode? = cards.getOrNull(index)
 protected fun cardCount(): Int = cards.size
}
/** Agenda com cara de widget real: cartões texturizados em pilha, cabeçalho e destaque no próximo evento. */
class CalendarScene:PersonalScene("calendar",0xFF8B78C4.toInt()){
 private var entryRemaining=0f
 override fun meshFor(): RenderMesh = MeshFactory.plane(.30f)
 override fun materialFor(index:Int): WidgetMaterial = WidgetMaterial(color=0xFFFFFFFF.toInt(), textureRef=CalendarTextures.cardRef(if(index==0)0 else index%3+1))
 override fun layoutCard(node:SceneNode,index:Int){
  node.local.x=if(index%2==0)-.07f else .07f
  node.local.y=.52f-index*.30f
  node.local.z=.12f-index*.045f
  node.local.rotationY=if(index%2==0)5f else -5f
  node.local.scaleX=.82f;node.local.scaleY=.52f
 }
 override fun onPrepared(){
  graph.root.add(SceneNode("agenda-header").apply{
   mesh=MeshFactory.plane(.20f);material=WidgetMaterial(color=0xFFFFFFFF.toInt(),textureRef=CalendarTextures.headerRef)
   local.x=0f;local.y=.94f;local.z=.14f;local.scaleX=1.35f;local.scaleY=.40f;local.alpha=1f
  })
 }
 override fun update(snapshot:WidgetSnapshot){super.update(snapshot);if(snapshot is CalendarSnapshot && snapshot.events.isNotEmpty())entryRemaining=8f}
 override fun tick(dtSeconds:Float):Boolean{
  if(entryRemaining<=0f)return false
  entryRemaining=(entryRemaining-dtSeconds).coerceAtLeast(0f)
  // Flutuação sutil dos cartões visíveis; some com o fim da janela (render-on-demand preservado).
  val motion=minOf(1f,entryRemaining)
  var i=0;while(i<cardCount()){cardAt(i)?.let{ if(it.local.alpha>0f){it.local.z=(.12f-i*.045f)+.02f*motion* kotlin.math.sin(entryRemaining*2.2f+i*1.3f).toFloat()} };i++}
  graph.updateWorld();return entryRemaining>0f
 }
 override fun release(){entryRemaining=0f;super.release()}
}

/** A small gallery of textured planes. Photo pixels remain Android-side until the renderer uploads them. */
class PhotosScene(private val textures: PhotosTextureStore = PhotosTextureStore()):WidgetScene {
 override val id = "photos"
 override val graph = SceneGraph()
 override val interactions = InteractionMap()
 private val cards = ArrayList<SceneNode>(6)
 private var prepared = false
 override fun prepare(context: WidgetSceneContext) {
  if (prepared) return
  prepared = true
  repeat(6) { index ->
   cards += graph.root.add(SceneNode("photo-$index").apply {
    mesh = MeshFactory.plane(.30f)
    material = WidgetMaterial(color = 0xFFFFFFFF.toInt())
    local.x = (index % 3 - 1) * .58f
    local.y = if (index < 3) .25f else -.25f
    local.z = -index * .07f
    local.rotationY = (index % 3 - 1) * -8f
    local.alpha = 0f
   })
  }
  graph.updateWorld()
 }
 override fun update(snapshot: WidgetSnapshot) {
  val photos = (snapshot as? PhotosSnapshot)?.takeIf { it.available }?.photos.orEmpty()
  cards.forEachIndexed { index, card ->
   val photo = photos.getOrNull(index)
   card.local.alpha = if (photo == null) 0f else 1f
   if (photo != null) card.material = WidgetMaterial(color = 0xFFFFFFFF.toInt(), textureRef = textures.refFor(photo.uri))
  }
  graph.updateWorld()
 }
 override fun tick(dtSeconds: Float) = false
 override fun pause() = Unit
 override fun resume() = Unit
 override fun release() { cards.clear(); graph.clear(); interactions.clear(); prepared = false; textures.release() }
}
class ContactsScene:PersonalScene("contacts",0xFFCD8C5A.toInt())
