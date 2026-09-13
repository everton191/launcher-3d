package br.com.ne3d.spbshellmodern.shell3d.widgets.system

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetMaterial
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

abstract class CoreScene(private val sceneId:String):WidgetScene{override val id=sceneId;override val graph=SceneGraph();override val interactions=InteractionMap();private lateinit var meter:SceneNode;private var ready=false
 override fun prepare(context:WidgetSceneContext){if(ready)return;ready=true;meter=graph.root.add(SceneNode("meter").apply{mesh=MeshFactory.cube(.55f);material=WidgetMaterial(color=0xFF5A95CB.toInt());local.scaleY=.1f});graph.updateWorld()}
 override fun update(snapshot:WidgetSnapshot){meter.local.scaleY=when(snapshot){is SystemSnapshot->.08f+snapshot.batteryPercent/100f;is NotificationSnapshot->.08f+snapshot.notifications.size*.16f;else->.08f};graph.updateWorld()};override fun tick(dt:Float)=false;override fun pause()=Unit;override fun resume()=Unit;override fun release(){graph.clear();interactions.clear();ready=false}}
 class NotificationScene:CoreScene("notifications")
 class SystemScene:CoreScene("system")
