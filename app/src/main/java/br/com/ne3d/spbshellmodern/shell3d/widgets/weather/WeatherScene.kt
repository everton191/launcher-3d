package br.com.ne3d.spbshellmodern.shell3d.widgets.weather

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetFramingProvider
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.InteractionMap
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetMaterial
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneGraph
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

/** Fixed-pool weather scene. It only consumes immutable WeatherSnapshot values. */
class WeatherScene : WidgetScene {
    override val id = WeatherWidgetIds.WEATHER
    override val graph = SceneGraph(SceneNode("weather-root"))
    override val interactions = InteractionMap()
    private lateinit var sun: SceneNode
    private lateinit var halo: SceneNode
    private lateinit var clouds: Array<SceneNode>
    private lateinit var precipitation: Array<SceneNode>
    private lateinit var fog: Array<SceneNode>
    private lateinit var lightning: SceneNode
    private lateinit var forecast: Array<SceneNode>
    private var condition = WeatherCondition.UNKNOWN
    private var entryRemaining = 0f
    private var prepared = false

    override fun prepare(context: WidgetSceneContext) {
        if (prepared) return; prepared = true
        val framing = WidgetFramingProvider.framingFor(id)
        graph.root.local.scaleX = framing.sceneScale
        graph.root.local.scaleY = framing.sceneScale
        graph.root.local.scaleZ = framing.sceneScale
        graph.root.add(SceneNode("sky").apply { mesh=MeshFactory.plane(1f); material=WidgetMaterial(color=0xFF102B57.toInt()); local.z=-1.2f; local.scaleX=2.2f; local.scaleY=1.3f })
        sun=graph.root.add(SceneNode("sun").apply { mesh=MeshFactory.uvSphere(16, 10, .28f); material=WidgetMaterial(color=0xFFFFFFFF.toInt(), emissive=.5f, textureRef=WeatherTextures.sunRef); local.x=.6f; local.y=.46f })
        halo=graph.root.add(SceneNode("sun-halo").apply { mesh=MeshFactory.plane(1f); material=WidgetMaterial(color=0xFFFFFFFF.toInt(), textureRef=WeatherTextures.haloRef, alpha=.28f); local.x=.6f; local.y=.46f; local.z=-.05f; local.scaleX=.42f; local.scaleY=.42f })
        clouds=Array(4) { i -> graph.root.add(SceneNode("cloud-$i").apply { mesh=MeshFactory.plane(.28f); material=WidgetMaterial(color=0xFFFFFFFF.toInt(), textureRef=WeatherTextures.cloudRef, alpha=.9f); local.x=-.65f+i*.42f; local.y=.38f-(i%2)*.16f; local.z=.12f+i*.04f; local.scaleX=.5f; local.scaleY=.5f }) }
        fog=Array(3) { i -> graph.root.add(SceneNode("fog-$i").apply { mesh=MeshFactory.plane(.16f); material=WidgetMaterial(color=0xFFFFFFFF.toInt(), textureRef=WeatherTextures.cloudRef, alpha=.26f); local.x=-.7f+i*.65f; local.y=-.08f+i*.12f; local.z=.18f; local.scaleX=.48f }) }
        precipitation=Array(20) { i -> graph.root.add(SceneNode("drop-$i").apply { mesh=MeshFactory.plane(.035f); material=WidgetMaterial(color=if(i%2==0) 0xFF79B9ED.toInt() else 0xFFF5FBFF.toInt(), alpha=.85f); local.x=-.82f+(i%5)*.40f; local.y=.52f-(i/5)*.27f; local.z=.15f; local.scaleX=.012f }) }
        lightning=graph.root.add(SceneNode("lightning").apply { mesh=MeshFactory.plane(.38f); material=WidgetMaterial(color=0xFFFFF3B0.toInt(), alpha=.9f, emissive=.7f); local.x=.15f; local.y=.15f; local.z=.2f; local.scaleX=.08f; local.rotationZ=18f })
        forecast=Array(5) { i -> graph.root.add(SceneNode("forecast-$i").apply { mesh=MeshFactory.cube(.18f); material=WidgetMaterial(color=0xFF63B3ED.toInt(), alpha=.85f); local.x=-.72f+i*.36f; local.y=-.62f; local.z=.05f; local.scaleY=.05f }) }
        applyCondition(); graph.updateWorld()
    }
    override fun update(snapshot: WidgetSnapshot) { if(snapshot !is WeatherSnapshot)return; condition=snapshot.condition; updateForecast(snapshot); entryRemaining=4.5f; applyCondition() }
    override fun tick(dtSeconds: Float): Boolean {
        if(entryRemaining<=0f) return false
        entryRemaining=(entryRemaining-dtSeconds).coerceAtLeast(0f)
        val progress=1f-entryRemaining/4.5f
        sun.local.scaleX=.65f+.35f*progress; sun.local.scaleY=sun.local.scaleX; sun.local.scaleZ=sun.local.scaleX
        halo.local.alpha=.12f+.18f*progress
        if(condition==WeatherCondition.THUNDERSTORM) lightning.local.alpha=if(entryRemaining>3.5f || (entryRemaining in 1.6f..1.75f)) .9f else 0f
        graph.updateWorld(); return entryRemaining>0f
    }
    override fun pause() { entryRemaining=0f }
    override fun resume() = Unit
    override fun release() { graph.clear(); interactions.clear(); prepared=false; entryRemaining=0f }
    private fun updateForecast(snapshot: WeatherSnapshot) { var i=0; while(i<forecast.size){ val day=snapshot.forecast.getOrNull(i); val range=day?.let { (it.max-it.min).coerceIn(1,30) } ?: 0; forecast[i].local.scaleY=if(range==0) .03f else .06f+range/40f; forecast[i].local.alpha=if(day==null)0f else 1f; i++ } }
    private fun applyCondition() {
        val cloudCount=when(condition){ WeatherCondition.MOSTLY_CLEAR->1; WeatherCondition.PARTLY_CLOUDY->2; WeatherCondition.CLOUDY,WeatherCondition.FOG,WeatherCondition.DRIZZLE,WeatherCondition.RAIN,WeatherCondition.SHOWERS,WeatherCondition.SNOW,WeatherCondition.THUNDERSTORM->4; else->0 }
        var i=0; while(i<clouds.size){clouds[i].local.alpha=if(i<cloudCount).9f else 0f;i++}
        val wet=condition==WeatherCondition.DRIZZLE||condition==WeatherCondition.RAIN||condition==WeatherCondition.SHOWERS||condition==WeatherCondition.SNOW||condition==WeatherCondition.THUNDERSTORM
        i=0; while(i<precipitation.size){ precipitation[i].local.alpha=if(wet && i < if(condition==WeatherCondition.DRIZZLE) 8 else 16) .85f else 0f; i++ }
        i=0; while(i<fog.size){fog[i].local.alpha=if(condition==WeatherCondition.FOG).32f else 0f;i++}
        sun.local.alpha=if(condition==WeatherCondition.CLEAR||condition==WeatherCondition.MOSTLY_CLEAR||condition==WeatherCondition.PARTLY_CLOUDY)1f else .35f
        halo.local.alpha=if(sun.local.alpha>0.5f).22f else 0f
        lightning.local.alpha=0f
    }
}

object WeatherWidgetIds { const val WEATHER = "weather" }
