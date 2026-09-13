package br.com.ne3d.spbshellmodern.shell3d.widgets.render

import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

/** Renderer-agnostic material. GL handles are owned by the renderer, never by a WidgetScene. */
class WidgetMaterial(var textureId: Int = 0, var color: Int = 0xFFFFFFFF.toInt(), var alpha: Float = 1f, var emissive: Float = 0f)
class WidgetRenderItem internal constructor(val node: SceneNode)
