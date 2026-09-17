package br.com.ne3d.spbshellmodern.shell3d.widgets

/** Configuration for scene framing parameters to avoid magic numbers in scenes. */
data class WidgetSceneFraming(
    val fov: Float = 60f,
    val cameraZ: Float = 5f,
    val cameraY: Float = 0f,
    val lookAtY: Float = 0f,
    val sceneScale: Float = 1f
)

/** Provides framing configurations based on Widget ID. */
object WidgetFramingProvider {
    private val configurations = mapOf(
        "world-time" to WidgetSceneFraming(
            fov = 45f,
            cameraZ = 4.5f,
            cameraY = 0f,
            lookAtY = 0f,
            sceneScale = 1.0f
        ),
        "weather" to WidgetSceneFraming(
            fov = 50f,
            cameraZ = 4.0f,
            cameraY = 0.5f,
            lookAtY = 0f,
            sceneScale = 1.2f
        ),
        "system" to WidgetSceneFraming(
            fov = 55f,
            cameraZ = 3.5f,
            cameraY = -0.5f,
            lookAtY = 0f,
            sceneScale = 1.1f
        )
    )

    /** Returns the framing for the given widget ID, or a default if not found. */
    fun framingFor(id: String): WidgetSceneFraming = configurations[id] ?: WidgetSceneFraming()

    /** True when the widget has a dedicated calibration instead of the shared default. */
    fun hasOverride(id: String): Boolean = configurations.containsKey(id)
}
