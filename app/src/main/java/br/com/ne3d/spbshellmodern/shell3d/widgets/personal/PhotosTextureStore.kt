package br.com.ne3d.spbshellmodern.shell3d.widgets.personal

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetTextureRef

/** Android-side photo previews. The GL scene receives only logical texture references. */
class PhotosTextureStore {
    private val lock = Any()
    private val previews = LinkedHashMap<String, Bitmap>()

    fun load(resolver: ContentResolver, photos: List<PhotoValue>, isActive: () -> Boolean) {
        val decoded = LinkedHashMap<String, Bitmap>()
        for (photo in photos.take(MAX_PHOTOS)) {
            if (!isActive()) { decoded.values.forEach { it.recycle() }; return }
            decodePreview(resolver, photo.uri)?.let { decoded[photo.uri] = it }
        }
        if (!isActive()) { decoded.values.forEach { it.recycle() }; return }
        synchronized(lock) {
            previews.values.forEach { it.recycle() }
            previews.clear()
            previews.putAll(decoded)
        }
    }

    fun refFor(uri: String): WidgetTextureRef = WidgetTextureRef("photos:$uri") { previewCopy(uri) ?: neutralBitmap() }

    fun release() = synchronized(lock) {
        previews.values.forEach { it.recycle() }
        previews.clear()
    }

    private fun previewCopy(uri: String): Bitmap? = synchronized(lock) {
        previews[uri]?.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun decodePreview(resolver: ContentResolver, uri: String): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        resolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > PREVIEW_EDGE * 2 || height / sample > PREVIEW_EDGE * 2) sample *= 2
        return sample
    }

    private fun neutralBitmap(): Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF263238.toInt()) }

    private companion object {
        const val MAX_PHOTOS = 6
        const val PREVIEW_EDGE = 512
    }
}

