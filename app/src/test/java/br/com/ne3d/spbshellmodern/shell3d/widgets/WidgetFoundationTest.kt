package br.com.ne3d.spbshellmodern.shell3d.widgets

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.animation.FloatTrack
import br.com.ne3d.spbshellmodern.shell3d.widgets.animation.Vec3
import br.com.ne3d.spbshellmodern.shell3d.widgets.animation.Vec3Track
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.LatestWidgetDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionRegion
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.WidgetInteraction
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode
import org.junit.Assert.*
import org.junit.Test

class WidgetFoundationTest {
    @Test fun `scene graph composes parent transform and removes nodes`() { val graph=SceneGraph(); val parent=graph.root.add(SceneNode("earth").apply{local.x=2f;local.alpha=.5f}); val child=parent.add(SceneNode("city").apply{local.y=3f});graph.updateWorld();assertEquals(2f,child.worldMatrix[12],0f);assertEquals(3f,child.worldMatrix[13],0f);assertEquals(.5f,child.worldAlpha,0f);assertSame(child,graph.find("city"));assertTrue(parent.remove(child));assertNull(graph.find("city")) }
    @Test fun `primitives have finite vertices valid indices and UVs`() { listOf(MeshFactory.cube(),MeshFactory.uvSphere(),MeshFactory.cylinder(),MeshFactory.hexTile()).forEach { mesh -> val data=FloatArray(mesh.vertexCount*5);mesh.vertices.position(0);mesh.vertices.get(data);data.forEach{assertTrue(it.isFinite())};for(i in 0 until mesh.vertexCount){assertTrue(data[i*5+3] in 0f..1f);assertTrue(data[i*5+4] in 0f..1f)};mesh.indices.position(0);repeat(mesh.indexCount){assertTrue(mesh.indices.get().toInt() and 0xFFFF in 0 until mesh.vertexCount)} } }
    @Test fun `tracks interpolate and finish`() { var value=0f;val float=FloatTrack({value=it},0f,10f,1f);float.tick(.5f);assertEquals(5f,value,.001f);float.tick(.5f);assertFalse(float.active);var vector=Vec3(0f,0f,0f);val vec=Vec3Track({x,y,z->vector=Vec3(x,y,z)},Vec3(0f,0f,0f),Vec3(2f,4f,6f),1f);vec.tick(.5f);assertEquals(Vec3(1f,2f,3f),vector) }
    @Test fun `data source retains newest snapshot and interactions route`() { data class Snapshot(override val revision:Long):WidgetSnapshot; val source=LatestWidgetDataSource<Snapshot>();source.publish(Snapshot(1));source.publish(Snapshot(2));source.publish(Snapshot(3));assertEquals(3,source.latest()!!.revision);var taps=0;val map=InteractionMap();map.add(InteractionRegion("hit",0f,0f,1f,1f,onInteraction={if(it is WidgetInteraction.Tap)taps++}));assertTrue(map.dispatch(WidgetInteraction.Tap(.5f,.5f)));assertFalse(map.dispatch(WidgetInteraction.Tap(2f,2f)));assertEquals(1,taps) }
}
