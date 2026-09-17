package br.com.ne3d.spbshellmodern.shell3d.widgets.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetTextureRef

/**
 * Texturas ORIGINAIS da agenda, geradas proceduralmente via Canvas em
 * estética retrô-futurista (cartão escuro, faixa de acento colorida,
 * bloco de horário e linhas de texto estilizadas).
 *
 * Nenhum pixel vem de assets do SPB/Yandex. As linhas de texto são
 * barras abstratas (sem tipografia real: o conteúdo vem da base
 * texture do card, a apresentação só dá cara de widget real).
 */
object CalendarTextures {
    const val CARD_AMBER_KEY = "agenda-card-amber-v1"
    const val CARD_TEAL_KEY = "agenda-card-teal-v1"
    const val CARD_VIOLET_KEY = "agenda-card-violet-v1"
    const val CARD_SELECTED_KEY = "agenda-card-selected-v1"
    const val HEADER_KEY = "agenda-header-v1"

    /**
     * Faixas de acento por variante: 0 = destaque, 1 = âmbar, 2 = teal, 3 = violeta.
     * Literais ARGB (sem android.graphics.Color no init: testes unitários JVM
     * puros não têm o framework; o Color só é tocado na geração do Bitmap,
     * que roda no device/GL thread).
     */
    // rgb(255,178,74), rgb(255,178,74), rgb(94,214,196), rgb(178,148,255)
    private val accents = intArrayOf(0xFFFFB24A.toInt(), 0xFFFFB24A.toInt(), 0xFF5ED6C4.toInt(), 0xFFB294FF.toInt())

    val headerRef = WidgetTextureRef(HEADER_KEY) { createHeaderBitmap() }
    private val cardRefs = Array(4) { variant ->
        val key = when (variant) {
            0 -> CARD_SELECTED_KEY
            2 -> CARD_TEAL_KEY
            3 -> CARD_VIOLET_KEY
            else -> CARD_AMBER_KEY
        }
        WidgetTextureRef(key) { createCardBitmap(variant) }
    }

    fun cardRef(variant: Int): WidgetTextureRef = cardRefs[variant.coerceIn(0, 3)]

    /** Cartão de evento 256x160. Variante 0 = item selecionado (borda viva). */
    fun createCardBitmap(variant: Int = 1, width: Int = 256, height: Int = 160): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val accent = accents[variant.coerceIn(0, 3)]
        val selected = variant == 0

        // Corpo do cartão: azul-petróleo escuro com cantos arredondados.
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) Color.rgb(34, 48, 70) else Color.rgb(24, 34, 52)
        }
        val card = RectF(2f, 2f, width - 2f, height - 2f)
        canvas.drawRoundRect(card, 14f, 14f, body)

        // Faixa de acento no topo.
        val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawRoundRect(RectF(2f, 2f, width - 2f, 22f), 14f, 14f, stripe)
        canvas.drawRect(2f, 12f, width - 2f, 22f, stripe)

        // Bloco de horário à esquerda.
        val timeBox = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(64, 255, 255, 255)
        }
        canvas.drawRoundRect(RectF(14f, 38f, 66f, 96f), 8f, 8f, timeBox)
        val timeLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawRoundRect(RectF(22f, 48f, 58f, 58f), 5f, 5f, timeLine)
        canvas.drawRoundRect(RectF(22f, 68f, 58f, 78f), 5f, 5f, timeLine)

        // Linhas de texto estilizadas (título + duas linhas de detalhe).
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(240, 244, 250)
        }
        val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 180, 196, 214)
        }
        canvas.drawRoundRect(RectF(80f, 42f, 226f, 58f), 8f, 8f, title)
        canvas.drawRoundRect(RectF(80f, 68f, 200f, 80f), 6f, 6f, detail)
        canvas.drawRoundRect(RectF(80f, 88f, 168f, 100f), 6f, 6f, detail)

        // Marcador de seleção: ponto vivo à direita do cartão destacado.
        if (selected) {
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            canvas.drawCircle(width - 24f, 60f, 9f, dot)
            val dotCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 244, 220)
            }
            canvas.drawCircle(width - 24f, 60f, 4f, dotCore)
        }

        // Scanlines sutis para o charme retrô.
        val scan = Paint().apply { color = Color.argb(10, 0, 0, 0) }
        var y = 6f
        while (y < height) {
            canvas.drawRect(2f, y, width - 2f, y + 1f, scan)
            y += 5f
        }

        // Borda: viva no selecionado, discreta nos demais.
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) accent else Color.argb(90, 120, 140, 170)
            style = Paint.Style.STROKE
            strokeWidth = if (selected) 4f else 2f
        }
        canvas.drawRoundRect(card, 14f, 14f, border)
        return bitmap
    }

    /** Cabeçalho 256x64: barra com pastilhas, cara de "AGENDA". */
    fun createHeaderBitmap(width: Int = 256, height: Int = 64): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 42, 62)
        }
        canvas.drawRoundRect(RectF(2f, 2f, width - 2f, height - 2f), 12f, 12f, bar)
        // Pastilhas: 3 blocos que sugerem o título sem tipografia.
        val blocks = intArrayOf(
            Color.rgb(255, 178, 74), Color.rgb(240, 244, 250), Color.rgb(94, 214, 196),
        )
        var x = 20f
        val widths = floatArrayOf(64f, 96f, 44f)
        for (i in blocks.indices) {
            val block = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blocks[i] }
            canvas.drawRoundRect(RectF(x, 22f, x + widths[i], 42f), 10f, 10f, block)
            x += widths[i] + 12f
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 120, 140, 170)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(RectF(2f, 2f, width - 2f, height - 2f), 12f, 12f, border)
        return bitmap
    }
}
