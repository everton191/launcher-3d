package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetTextureRef

/** Original procedural equirectangular Earth texture; no third-party or SPB assets are used. */
object WorldTimeEarthTexture {
    val ref = WidgetTextureRef("world-time-earth-v1") { createBitmap() }
    fun createBitmap(): Bitmap = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(22, 76, 132))
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x335FA6D5; strokeWidth = 1f; style = Paint.Style.STROKE }
        for (x in 0..512 step 32) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), 256f, grid)
        for (y in 0..256 step 32) canvas.drawLine(0f, y.toFloat(), 512f, y.toFloat(), grid)
        val land = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 138, 80); style = Paint.Style.FILL }
        fun continent(vararg points: Float) { val path = Path(); path.moveTo(points[0], points[1]); var i=2; while(i<points.size){path.lineTo(points[i],points[i+1]);i+=2};path.close();canvas.drawPath(path,land) }
        // Equirectangular silhouettes placed around the actual longitude regions.
        continent(55f,70f, 84f,55f, 121f,66f, 132f,94f, 109f,111f, 92f,102f, 76f,124f, 58f,107f)
        continent(118f,125f, 143f,138f, 151f,174f, 136f,213f, 119f,190f, 112f,154f)
        continent(222f,62f, 255f,51f, 286f,69f, 304f,96f, 280f,112f, 259f,98f, 241f,119f, 222f,102f)
        continent(264f,116f, 294f,129f, 303f,172f, 283f,205f, 265f,180f, 252f,144f)
        continent(304f,66f, 356f,48f, 422f,68f, 454f,100f, 423f,119f, 371f,108f, 336f,124f, 305f,102f)
        continent(424f,169f, 461f,174f, 477f,202f, 448f,211f, 428f,194f)
        val ice = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAAE8F6FF.toInt() }
        canvas.drawRect(0f, 0f, 512f, 10f, ice); canvas.drawRect(0f, 246f, 512f, 256f, ice)
    }
}
