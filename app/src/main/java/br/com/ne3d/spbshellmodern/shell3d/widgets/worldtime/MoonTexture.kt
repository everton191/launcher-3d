package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetTextureRef
import kotlin.random.Random

/**
 * Texturas ORIGINAIS da lua, geradas proceduralmente via Canvas.
 *
 * Nenhum pixel vem de assets do SPB/Yandex: base de regolito em tons de
 * cinza, crateras deterministas (seed fixa) com realce de borda iluminada
 * + sombra interna, manchas suaves de maria e vinheta sutil de limbo.
 * O mapa e equiretangular e pode ir direto na uvSphere do [MoonScene].
 */
object MoonTexture {
    const val KEY = "moon-surface-original-v1"
    val ref = WidgetTextureRef(KEY) { createBitmap() }

    fun createBitmap(width: Int = 512, height: Int = 256): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val random = Random(0xC0FFEE)

        // Base de regolito: gradiente vertical sutil (polos levemente mais escuros).
        val base = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.rgb(168, 168, 176), Color.rgb(148, 148, 156),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), base)

        // Grão fino de regolito.
        val grain = Paint()
        repeat(2_600) {
            val g = 130 + random.nextInt(70)
            grain.color = Color.argb(26, g, g, g + 6)
            canvas.drawPoint(random.nextFloat() * width, random.nextFloat() * height, grain)
        }

        // Marias: manchas escuras grandes e suaves (retrô, não geográficas reais).
        val maria = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 46 }
        val mariaSpots = arrayOf(
            floatArrayOf(.30f, .42f, .13f), floatArrayOf(.44f, .55f, .09f),
            floatArrayOf(.62f, .38f, .11f), floatArrayOf(.72f, .58f, .07f),
            floatArrayOf(.18f, .60f, .07f),
        )
        for (spot in mariaSpots) {
            val cx = spot[0] * width
            val cy = spot[1] * height
            val radius = spot[2] * width
            maria.color = Color.rgb(104, 106, 116)
            // Camadas concêntricas para borda suave sem blur (barato e determinístico).
            for (layer in 5 downTo 1) {
                maria.alpha = 14 + (5 - layer) * 8
                canvas.drawCircle(cx, cy, radius * layer / 5f, maria)
            }
        }

        // Crateras: anel claro no lado iluminado + sombra interna, piso levemente afundado.
        repeat(64) {
            val cx = random.nextFloat() * width
            val cy = height * .08f + random.nextFloat() * height * .84f
            val radius = 3f + random.nextFloat() * random.nextFloat() * 22f
            drawCrater(canvas, cx, cy, radius)
        }
        // Micro-crateras para textura de perto.
        repeat(160) {
            val cx = random.nextFloat() * width
            val cy = random.nextFloat() * height
            drawCrater(canvas, cx, cy, 1.2f + random.nextFloat() * 2.6f)
        }

        // Vinheta de limbo suave (topo/base um pouco mais escuros).
        val vignette = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.argb(38, 60, 60, 68), Color.argb(0, 60, 60, 68),
                    Color.argb(0, 60, 60, 68), Color.argb(38, 60, 60, 68),
                ),
                floatArrayOf(0f, .22f, .78f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignette)
        return bitmap
    }

    private fun drawCrater(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val floor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 96, 96, 104)
        }
        canvas.drawCircle(cx, cy, radius, floor)
        // Sombra interna (arco inferior-direito).
        canvas.drawArc(
            cx - radius, cy - radius, cx + radius, cy + radius,
            20f, 140f, false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 70, 70, 80)
                style = Paint.Style.STROKE
                strokeWidth = (radius * .28f).coerceAtLeast(1f)
            },
        )
        // Borda iluminada (lado superior-esquerdo).
        canvas.drawArc(
            cx - radius, cy - radius, cx + radius, cy + radius,
            200f, 140f, false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(110, 214, 214, 222)
                style = Paint.Style.STROKE
                strokeWidth = (radius * .22f).coerceAtLeast(1f)
            },
        )
    }
}

/**
 * Halo radial discreto para a lua (retrô-futurista): núcleo quente quase
 * branco-azulado que cai para transparente. Vai num plano atrás da esfera.
 */
object MoonGlowTexture {
    const val KEY = "moon-glow-original-v1"
    val ref = WidgetTextureRef(KEY) { createBitmap() }

    fun createBitmap(size: Int = 256): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        // Anéis concêntricos: núcleo denso + queda suave até a borda.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        for (layer in 24 downTo 1) {
            val fraction = layer / 24f
            val radius = center * (0.30f + 0.70f * fraction)
            val alpha = (66 * (1f - fraction) * (1f - fraction) + 4).toInt()
            glow.color = Color.argb(alpha, 190, 206, 232)
            canvas.drawCircle(center, center, radius, glow)
        }
        // Núcleo quente.
        glow.color = Color.argb(52, 226, 234, 248)
        canvas.drawCircle(center, center, center * .30f, glow)
        return bitmap
    }
}
