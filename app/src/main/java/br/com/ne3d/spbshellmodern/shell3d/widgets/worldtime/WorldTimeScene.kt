package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

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

/** World Time GL scene. Selection is resolved against projected, front-facing markers. */
class WorldTimeScene(private val cities: List<WorldTimeCity> = WorldTimeCities.defaults) : WidgetScene {
    override val id = WidgetIds.WORLD_TIME
    override val graph = SceneGraph(SceneNode("world-time-root"))
    override val interactions = InteractionMap()
    private lateinit var earth: SceneNode
    private val markers = HashMap<String, SceneNode>()
    private val points = HashMap<String, WidgetScreenPoint>()
    private var projection: WidgetProjection? = null
    private var touchRadiusPx = 36f
    private var idleSeconds = 8f
    private var selected = cities.first().id
    private var prepared = false
    private var selectionListener: ((String) -> Unit)? = null

    override fun prepare(context: WidgetSceneContext) {
        if (prepared) return
        prepared = true; touchRadiusPx = 36f * context.density
        earth = graph.root.add(SceneNode("earth").apply {
            mesh = MeshFactory.uvSphere(28, 18, 1f)
            // Mirror the complete geographic scene, including city children, to match the requested reading direction.
            local.scaleX = -1f
            material = WidgetMaterial(textureRef = WorldTimeEarthTexture.ref)
        })
        earth.add(SceneNode("cloud-layer").apply {
            mesh = MeshFactory.uvSphere(28, 18, 1.024f)
            material = WidgetMaterial(textureRef = WorldTimeCloudTexture.ref, alpha = .38f)
        })
        cities.forEach { city ->
            val p = WorldTimeCoordinates.latLon(city.latitude, city.longitude, 1.035f)
            markers[city.id] = earth.add(SceneNode("marker-${city.id}").apply {
                mesh = MeshFactory.uvSphere(8, 6, .042f); material = WidgetMaterial(color = 0xFFE8D16A.toInt())
                local.x = p.x; local.y = p.y; local.z = p.z
                // Rotate the marker's local +Z axis onto the Earth's outward normal.
                local.rotationY = Math.toDegrees(kotlin.math.asin((p.x / 1.035f).toDouble())).toFloat()
                local.rotationX = Math.toDegrees(kotlin.math.atan2((-p.y).toDouble(), p.z.toDouble())).toFloat()
            })
            points[city.id] = WidgetScreenPoint()
        }
        interactions.add(InteractionRegion("globe", 0f, 0f, context.viewportWidth.toFloat(), context.viewportHeight.toFloat(), draggable = true) { event ->
            when (event) {
                is WidgetInteraction.Drag -> drag(event.dx)
                is WidgetInteraction.Tap -> hitTest(event.x, event.y)?.let(::select)
                else -> Unit
            }
        })
        refreshSelection(); graph.updateWorld()
    }
    override fun updateProjection(projection: WidgetProjection) { this.projection = projection }
    override fun update(snapshot: WidgetSnapshot) { if (snapshot is WorldTimeSnapshot && markers.containsKey(snapshot.selectedCityId)) { selected = snapshot.selectedCityId; refreshSelection() } }
    override fun tick(dtSeconds: Float): Boolean {
        if (idleSeconds <= 0f) { graph.updateWorld(); return false }
        idleSeconds = (idleSeconds - dtSeconds).coerceAtLeast(0f)
        earth.local.rotationY = (earth.local.rotationY + dtSeconds * 6f) % 360f
        graph.updateWorld(); return idleSeconds > 0f
    }
    override fun pause() { idleSeconds = 0f }
    override fun resume() { idleSeconds = 2f }
    override fun release() { markers.clear(); points.clear(); graph.clear(); prepared = false; idleSeconds = 0f; selectionListener = null }
    fun setSelectionListener(listener: ((String) -> Unit)?) { selectionListener = listener }
    fun drag(deltaX: Float) { idleSeconds = 0f; earth.local.rotationY = (earth.local.rotationY + deltaX * .35f) % 360f; graph.updateWorld() }
    fun select(cityId: String): Boolean {
        if (!markers.containsKey(cityId)) return false
        selected = cityId; idleSeconds = .45f; refreshSelection(); selectionListener?.invoke(cityId); return true
    }
    fun hitTest(x: Float, y: Float): String? {
        val projector = projection ?: return null
        var bestId: String? = null; var bestDistance = Float.MAX_VALUE
        for (city in cities) {
            val node = markers[city.id] ?: continue; val point = points[city.id] ?: continue
            if (node.worldMatrix[14] <= 0f || !projector.projectOrigin(node.worldMatrix, point) || !point.visible) continue
            val dx = point.x - x; val dy = point.y - y; val distance = dx * dx + dy * dy
            if (distance <= touchRadiusPx * touchRadiusPx && distance < bestDistance) { bestDistance = distance; bestId = city.id }
        }
        return bestId
    }
    private fun refreshSelection() { markers.forEach { (id, node) -> node.material?.color = if (id == selected) 0xFFFFA24D.toInt() else 0xFFE8D16A.toInt() } }
}
object WidgetIds { const val WORLD_TIME = "world-time" }
