package br.com.ne3d.spbshellmodern.shell3d.texture

import android.graphics.*
import android.opengl.GLES30
import java.nio.ByteBuffer

/** GL-thread texture owner. A same-sized update uses sub-image, never reallocates a texture. */
class TextureManager {
    private val ids = mutableMapOf<String, Int>(); private val sizes = mutableMapOf<String, Pair<Int, Int>>()
    private var uploadBuffer = ByteBuffer.allocateDirect(PanelTextureSpec.WIDTH * PanelTextureSpec.HEIGHT * 4)
    data class Update(val textureId: Int, val bytes: Int)
    fun label(id: String, title: String, color: Int): Update {
        val bitmap = Bitmap.createBitmap(PanelTextureSpec.WIDTH, PanelTextureSpec.HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(color); Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
                p.color = Color.WHITE; p.textSize = 38f; p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                drawText(title, 28f, 64f, p); p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = 0xAAFFFFFF.toInt(); drawRect(16f, 16f, (PanelTextureSpec.WIDTH - 16).toFloat(), (PanelTextureSpec.HEIGHT - 16).toFloat(), p)
            }
        }
        val result = update(id, bitmap); bitmap.recycle(); return result
    }
    fun update(key: String, bitmap: Bitmap): Update {
        val old = ids[key]
        val texture = old ?: IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0].also { ids[key] = it }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        val sameSize = sizes[key] == (bitmap.width to bitmap.height)
        if (uploadBuffer.capacity() < bitmap.byteCount) uploadBuffer = ByteBuffer.allocateDirect(bitmap.byteCount)
        uploadBuffer.clear(); bitmap.copyPixelsToBuffer(uploadBuffer); uploadBuffer.position(0)
        if (sameSize) GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bitmap.width, bitmap.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, uploadBuffer)
        else { GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, bitmap.width, bitmap.height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, uploadBuffer); sizes[key] = bitmap.width to bitmap.height }
        return Update(texture, bitmap.byteCount)
    }
    fun textureId(key: String): Int = ids[key] ?: 0
    fun destroy() { ids.values.forEach { GLES30.glDeleteTextures(1, intArrayOf(it), 0) }; ids.clear(); sizes.clear() }
}
