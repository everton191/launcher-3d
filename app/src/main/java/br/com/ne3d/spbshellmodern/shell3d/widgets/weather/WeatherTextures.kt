package br.com.ne3d.spbshellmodern.shell3d.widgets.weather

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetTextureRef
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Texturas ORIGINAIS do clima, geradas proceduralmente via Canvas em paleta
 * retrô-futurista quente (âmbar/dourado sobre azul-petróleo).
 *
 * Nada é extraído do SPB/Yandex: sol em degradê radial com anel de
 * granulação, halo de queda suave e nuvem fofa feita de elipses
 * sobrepostas com sombra inferior. Todas com fundo transparente para
 * composição sobre a base texture do card.
 */
object WeatherTextures {
    const val SUN_KEY = "weather-sun-original-v1"
    const val HALO_KEY = "weather-halo-original-v1"
    const val CLOUD_KEY = "weather-cloud-original-v1"

    val sunRef = WidgetTextureRef(SUN_KEY) { createSunBitmap() }
    val haloRef = WidgetTextureRef(HALO_KEY) { createHaloBitmap() }
    val cloudRef = WidgetTextureRef(CLOUD_KEY) { createCloudBitmap() }

    /**
     * Disco solar quente em cobertura total (sem transparência: vai numa
     * esfera, então cada texel precisa ser opaco). Núcleo claro, limbo
     * âmbar, granulação e um anel-órbita retrô que vira faixa na esfera.
     */
    fun createSunBitmap(size: Int = 256): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        // Fundo opaco: laranja profundo das bordas (cobre os cantos da UV).
        canvas.drawColor(Color.rgb(198, 106, 30))
        val disc = center * 1.05f
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center, center, disc,
                intArrayOf(
                    Color.rgb(255, 246, 214),
                    Color.rgb(255, 214, 110),
                    Color.rgb(244, 158, 52),
                ),
                floatArrayOf(0f, .55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(center, center, disc, body)
        // Granulação do limbo: pontos âmbar-escuros perto da borda.
        val random = Random(0x5ED)
        val grain = Paint()
        repeat(420) {
            val angle = random.nextFloat() * 2f * Math.PI.toFloat()
            val radius = disc * (.72f + random.nextFloat() * .26f)
            val x = center + cos(angle) * radius
            val y = center + sin(angle) * radius
            val tone = 200 + random.nextInt(55)
            grain.color = Color.argb(40, tone, (tone * .62f).toInt(), 30)
            canvas.drawPoint(x, y, grain)
        }
        // Anel retrô: órbita fina dourada ao redor do disco.
        val orbit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 224, 150)
            style = Paint.Style.STROKE
            strokeWidth = size / 128f
        }
        canvas.drawCircle(center, center, disc * .82f, orbit)
        return bitmap
    }

    /** Halo suave: branco-dourado que cai para transparente na borda. */
    fun createHaloBitmap(size: Int = 256): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val halo = Paint(Paint.ANTI_ALIAS_FLAG)
        for (layer in 28 downTo 1) {
            val fraction = layer / 28f
            val alpha = (52 * (1f - fraction) * (1f - fraction) + 2).toInt()
            halo.color = Color.argb(alpha, 255, 222, 140)
            canvas.drawCircle(center, center, center * fraction, halo)
        }
        return bitmap
    }

    /**
     * Nuvem fofa retrô: elipses sobrepostas em branco-azulado com base
     * sombreada e contorno superior iluminado. Fundo transparente.
     */
    fun createCloudBitmap(width: Int = 256, height: Int = 160): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val puffs = arrayOf(
            // x, y (fração), raio-x, raio-y (fração da largura/altura)
            floatArrayOf(.28f, .62f, .20f, .30f),
            floatArrayOf(.44f, .48f, .24f, .36f),
            floatArrayOf(.62f, .50f, .22f, .34f),
            floatArrayOf(.76f, .62f, .17f, .26f),
            floatArrayOf(.52f, .66f, .30f, .26f),
        )
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(232, 240, 248)
        }
        for (puff in puffs) {
            canvas.save()
            canvas.translate(puff[0] * width, puff[1] * height)
            canvas.scale(puff[2] * width / 64f, puff[3] * height / 64f)
            canvas.drawCircle(0f, 0f, 64f, body)
            canvas.restore()
        }
        // Sombra inferior: faixa azul-acinzentada translúcida na base.
        val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, 148, 170, 196)
        }
        for (puff in puffs) {
            canvas.save()
            canvas.translate(puff[0] * width, (puff[1] + puff[3] * .42f) * height)
            canvas.scale(puff[2] * width / 64f, puff[3] * height * .38f / 64f)
            canvas.drawCircle(0f, 0f, 64f, shade)
            canvas.restore()
        }
        // Realce superior: arco claro no topo dos dois puffs maiores.
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(
            .20f * width, .12f * height, .68f * width, .60f * height,
            200f, 100f, false, shine,
        )
        return bitmap
    }
}
