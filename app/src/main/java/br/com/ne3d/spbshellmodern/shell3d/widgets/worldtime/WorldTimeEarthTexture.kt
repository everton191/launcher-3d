package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.content.res.Resources
import br.com.ne3d.spbshellmodern.R
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetTextureRef

/** NASA Blue Marble resource with the previous project-generated map retained only as a safe fallback. */
object WorldTimeEarthTexture {
    val ref = WidgetTextureRef("world-time-earth-nasa-bmng-v1") { resources ->
        BitmapFactory.decodeResource(resources, R.drawable.world_time_earth) ?: createFallbackBitmap()
    }
    private fun createFallbackBitmap(): Bitmap = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(22, 76, 132))
        val land = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 138, 80) }
        fun continent(vararg p: Float) { val path = Path(); path.moveTo(p[0], p[1]); var i = 2; while (i < p.size) { path.lineTo(p[i], p[i + 1]); i += 2 }; path.close(); canvas.drawPath(path, land) }
        continent(55f,70f, 84f,55f, 121f,66f, 132f,94f, 109f,111f, 92f,102f, 76f,124f, 58f,107f)
        continent(118f,125f, 143f,138f, 151f,174f, 136f,213f, 119f,190f, 112f,154f)
        continent(304f,66f, 356f,48f, 422f,68f, 454f,100f, 423f,119f, 371f,108f, 336f,124f, 305f,102f)
    }
}
