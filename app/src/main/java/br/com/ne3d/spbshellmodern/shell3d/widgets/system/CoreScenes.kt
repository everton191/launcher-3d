package br.com.ne3d.spbshellmodern.shell3d.widgets.system

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetProjection
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScreenPoint
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionRegion
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.WidgetInteraction
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetMaterial
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

class NotificationScene(var onOpen:(String)->Unit = {}):WidgetScene {
 override val id="notifications"; override val graph=SceneGraph(); override val interactions=InteractionMap(); private val cards=ArrayList<SceneNode>(5); private val keys=ArrayList<String>(5); private val points=ArrayList<WidgetScreenPoint>(5); private var ready=false
 override fun prepare(context:WidgetSceneContext){if(ready)return;ready=true;repeat(5){i->cards+=graph.root.add(SceneNode("notification-$i").apply{mesh=MeshFactory.plane(.18f);material=WidgetMaterial(color=0xFF496C9B.toInt());local.y=.36f-i*.18f;local.z=-i*.08f;local.x=i*.025f;local.rotationY=-i*3f;local.alpha=0f});points+=WidgetScreenPoint()};graph.updateWorld()}
 override fun update(snapshot:WidgetSnapshot){val value=snapshot as? NotificationSnapshot;keys.clear();val notifications=if(value?.available==true)value.notifications else emptyList();cards.forEachIndexed{i,card->val n=notifications.getOrNull(i);card.local.alpha=if(n==null)0f else 1f;if(n!=null)keys+=n.key};graph.updateWorld()}
 override fun updateProjection(projection:WidgetProjection){interactions.clear();cards.forEachIndexed{i,card->if(i>=keys.size)return@forEachIndexed;if(projection.projectOrigin(card.worldMatrix,points[i])){val p=points[i];interactions.add(InteractionRegion(keys[i],p.x-120f,p.y-52f,p.x+120f,p.y+52f){if(it is WidgetInteraction.Tap)onOpen(keys[i])})}}}
 override fun tick(dtSeconds:Float)=false;override fun pause()=Unit;override fun resume()=Unit;override fun release(){cards.clear();keys.clear();points.clear();graph.clear();interactions.clear();ready=false}
}
class SystemScene:WidgetScene { override val id="system";override val graph=SceneGraph();override val interactions=InteractionMap();private val meters=ArrayList<SceneNode>(3);private var ready=false
 override fun prepare(context:WidgetSceneContext){if(ready)return;ready=true;repeat(3){i->meters+=graph.root.add(SceneNode("system-$i").apply{mesh=MeshFactory.cube(.28f);material=WidgetMaterial(color=if(i==0)0xFF53AE70.toInt()else 0xFF4B89C8.toInt());local.x=(i-1)*.48f;local.y=-.25f;local.z=-i*.06f;local.scaleY=.1f})};graph.updateWorld()}
 override fun update(snapshot:WidgetSnapshot){val value=snapshot as? SystemSnapshot?:return;val levels=floatArrayOf(value.batteryPercent/100f,value.storagePercent/100f,if(value.networkConnected)1f else .12f);meters.forEachIndexed{i,meter->meter.local.scaleY=.12f+levels[i]*1.5f;meter.local.alpha=1f;if(i==0)meter.material=WidgetMaterial(color=if(value.charging)0xFFB6D64A.toInt()else 0xFF53AE70.toInt())};graph.updateWorld()}
 override fun tick(dtSeconds:Float)=false;override fun pause()=Unit;override fun resume()=Unit;override fun release(){meters.clear();graph.clear();interactions.clear();ready=false}
}

