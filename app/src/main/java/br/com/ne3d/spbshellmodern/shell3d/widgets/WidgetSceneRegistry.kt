package br.com.ne3d.spbshellmodern.shell3d.widgets

/** Registry maps a future widget type to a scene factory without teaching the renderer widget names. */
class WidgetSceneRegistry {
    private val factories = HashMap<String, () -> WidgetScene>()
    fun register(id: String, factory: () -> WidgetScene) { require(id !in factories) { "Widget scene already registered: $id" }; factories[id]=factory }
    fun create(id: String): WidgetScene? = factories[id]?.invoke()
    companion object { const val DEBUG_SCENE = "debug-widget-scene" }
}
