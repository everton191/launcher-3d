package br.com.ne3d.spbshellmodern.shell3d.widgets.render

import android.graphics.Bitmap
import android.content.res.Resources

import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode

/** Renderer-agnostic material. GL handles are owned by the renderer, never by a WidgetScene. */
/** Logical texture descriptor. The renderer creates and owns its GL handle. */
class WidgetTextureRef(val key: String, val bitmapFactory: (Resources) -> Bitmap)
class WidgetMaterial(var textureId: Int = 0, var color: Int = 0xFFFFFFFF.toInt(), var alpha: Float = 1f, var emissive: Float = 0f, val textureRef: WidgetTextureRef? = null)
class WidgetRenderItem internal constructor(val node: SceneNode)
