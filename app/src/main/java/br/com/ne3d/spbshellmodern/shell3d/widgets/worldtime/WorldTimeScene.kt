package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionRegion
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.WidgetInteraction
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetMaterial
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

/** World Time GL scene. Earth owns marker transforms; the scene never formats time while rendering. */
class WorldTimeScene(private val cities: List<WorldTimeCity> = WorldTimeCities.defaults) : WidgetScene {
    override val id = WidgetIds.WORLD_TIME; override val graph=SceneGraph(SceneNode("world-time-root")); override val interactions=InteractionMap(); private lateinit var earth: SceneNode; private val markers=HashMap<String,SceneNode>(); private var idleSeconds=8f; private var selected=cities.first().id; private var prepared=false
    override fun prepare(context: WidgetSceneContext) { if(prepared)return; prepared=true; earth=graph.root.add(SceneNode("earth").apply { mesh=MeshFactory.uvSphere(28,18,1f); material=WidgetMaterial(color=0xFF174D86.toInt()) })
        cities.forEach { city -> val p=WorldTimeCoordinates.latLon(city.latitude,city.longitude,1.06f); markers[city.id]=earth.add(SceneNode("marker-${city.id}").apply { mesh=MeshFactory.hexTile(.055f);material=WidgetMaterial(color=0xFFE8D16A.toInt());local.x=p.x;local.y=p.y;local.z=p.z }) }
        interactions.add(InteractionRegion("globe", -1.5f, -1f, 1.5f, 1f, draggable = true) { event ->
            when (event) {
                is WidgetInteraction.Drag -> drag(event.dx)
                is WidgetInteraction.Tap -> select(cityAt(event.x).id)
                else -> Unit
            }
        })
        refreshSelection(); graph.updateWorld() }
    override fun update(snapshot: WidgetSnapshot) { if(snapshot is WorldTimeSnapshot && cities.any{it.id==snapshot.selectedCityId}) { selected=snapshot.selectedCityId;refreshSelection() } }
    override fun tick(dtSeconds: Float): Boolean { if(idleSeconds<=0f){graph.updateWorld();return false}; idleSeconds=(idleSeconds-dtSeconds).coerceAtLeast(0f);earth.local.rotationY=(earth.local.rotationY+dtSeconds*6f)%360f;graph.updateWorld();return idleSeconds>0f }
    override fun pause(){idleSeconds=0f}; override fun resume(){idleSeconds=2f}; override fun release(){markers.clear();graph.clear();prepared=false;idleSeconds=0f}
    fun drag(deltaX: Float){idleSeconds=0f;earth.local.rotationY=(earth.local.rotationY+deltaX*.12f)%360f;graph.updateWorld()}
    fun select(cityId:String):Boolean { if(cityId !in markers)return false;selected=cityId;idleSeconds=.45f;refreshSelection();return true }
    private fun cityAt(localX: Float): WorldTimeCity {
        val index = (((localX / 3f) + .5f) * cities.size).toInt().coerceIn(0, cities.lastIndex)
        return cities[index]
    }
    private fun refreshSelection(){markers.forEach{(id,node)->node.material?.color=if(id==selected)0xFFFFA24D.toInt()else 0xFFE8D16A.toInt()}}
}
object WidgetIds { const val WORLD_TIME = "world-time" }
