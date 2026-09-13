package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.Resources
import br.com.ne3d.spbshellmodern.R
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetTextureRef

/** Sparse transparent cloud mask, separate from the NASA base map. */
object WorldTimeCloudTexture {
    val ref = WidgetTextureRef("world-time-clouds-v2") { resources ->
        BitmapFactory.decodeResource(resources, R.drawable.world_time_clouds) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
