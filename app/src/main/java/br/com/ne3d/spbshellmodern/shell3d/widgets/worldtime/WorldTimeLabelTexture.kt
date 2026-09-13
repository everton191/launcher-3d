package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetTextureRef

/** Lazily uploads one reusable bitmap per city label; never allocated during animation frames. */
object WorldTimeLabelTexture {
    fun ref(city: WorldTimeCity) = WidgetTextureRef("world-time-label-${city.id}") {
        Bitmap.createBitmap(384, 96, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).apply {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 56f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setShadowLayer(4f, 1f, 2f, Color.BLACK)
                    textAlign = Paint.Align.CENTER
                }
                drawText(city.displayName, bitmap.width / 2f, 66f, paint)
            }
        }
    }
}
