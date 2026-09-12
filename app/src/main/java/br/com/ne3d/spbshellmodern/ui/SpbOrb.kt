package br.com.ne3d.spbshellmodern.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.ne3d.spbshellmodern.R
import br.com.ne3d.spbshellmodern.model.WidgetRenderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.*

/** Stops clocks and animation while the launcher is covered or the display is off. */
@Composable
internal fun rememberSpbResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/**
 * Orthographic textured sphere, not a rotated flat photograph. Projection and light are
 * calculated off the UI thread. The native SPB shaders/timing have not been recovered.
 */
@Composable
internal fun SpbOrb(
    moon: Boolean,
    phase: Double = .5,
    longitude: Double = 0.0,
    state: WidgetRenderState,
    modifier: Modifier,
    description: String
) {
    val resources = LocalContext.current.resources
    val projection = remember(moon) {
        val texture = BitmapFactory.decodeResource(resources, if (moon) R.drawable.spb_moon_map else R.drawable.spb_earth_map)
        SphereProjection(texture, 224, flipVertical = !moon).also { texture.recycle() }
    }
    val resumed = rememberSpbResumed()
    var frame by remember(projection) { mutableStateOf<Bitmap?>(null) }
    val animate = resumed && state in listOf(WidgetRenderState.MAGIC_ANIMATION, WidgetRenderState.ACTIVE_3D)
    LaunchedEffect(projection, phase, longitude, animate) {
        val start = android.os.SystemClock.elapsedRealtime()
        do {
            val rotation = longitude + if (animate) (android.os.SystemClock.elapsedRealtime() - start) / 1000.0 * .16 else 0.0
            frame = withContext(Dispatchers.Default) { projection.render(rotation, phase, moon) }
            if (animate) delay(66L)
        } while (isActive && animate)
    }
    frame?.let { Image(it.asImageBitmap(), description, modifier) }
}

private class SphereProjection(texture: Bitmap, private val diameter: Int, flipVertical: Boolean) {
    private val tw = texture.width
    private val th = texture.height
    private val pixels = IntArray(tw * th).also { texture.getPixels(it, 0, tw, 0, 0, tw, th) }
    private val nx = FloatArray(diameter * diameter)
    private val nz = FloatArray(diameter * diameter) { -1f }
    private val u = FloatArray(diameter * diameter)
    private val v = IntArray(diameter * diameter)
    private val coverage = IntArray(diameter * diameter)

    init {
        val radius = diameter / 2f - 1f
        for (y in 0 until diameter) for (x in 0 until diameter) {
            val index = y * diameter + x
            val xx = (x + .5f - diameter / 2f) / radius
            val yy = (y + .5f - diameter / 2f) / radius
            val r2 = xx * xx + yy * yy
            if (r2 <= 1f) {
                val z = sqrt(1f - r2)
                nx[index] = xx; nz[index] = z
                u[index] = (atan2(xx, z) / (2 * PI) + .5).toFloat()
                val latitude = asin(yy.coerceIn(-1f, 1f)) / PI + .5
                // The extracted Earth texture uses OpenGL's bottom-up V orientation.
                v[index] = ((if (flipVertical) 1.0 - latitude else latitude) * (th - 1)).toInt()
                coverage[index] = ((1f - sqrt(r2)) * radius * 255f).toInt().coerceIn(0, 255)
            }
        }
    }

    fun render(rotation: Double, phase: Double, moon: Boolean): Bitmap {
        val out = IntArray(diameter * diameter)
        // At new Moon the Sun is behind it; waxing phases illuminate its right side.
        val lightX = if (moon) sin(phase * 2 * PI) else -.45
        val lightZ = if (moon) -cos(phase * 2 * PI) else .89
        val turn = rotation / (2 * PI)
        for (i in out.indices) {
            if (nz[i] < 0f) continue
            val uu = ((u[i] + turn) % 1.0 + 1.0) % 1.0
            val source = pixels[v[i] * tw + (uu * tw).toInt().coerceIn(0, tw - 1)]
            val diffuse = (nx[i] * lightX + nz[i] * lightZ).coerceIn(0.0, 1.0)
            val gain = if (moon) .035 + .965 * sqrt(diffuse) else .24 + .76 * sqrt(diffuse)
            val red = (((source shr 16) and 255) * gain).toInt().coerceIn(0, 255)
            val green = (((source shr 8) and 255) * gain).toInt().coerceIn(0, 255)
            val blue = ((source and 255) * gain).toInt().coerceIn(0, 255)
            out[i] = (coverage[i] shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return Bitmap.createBitmap(out, diameter, diameter, Bitmap.Config.ARGB_8888)
    }
}
