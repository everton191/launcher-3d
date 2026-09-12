package br.com.ne3d.spbshellmodern.shell3d.texture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.platform.ComposeView
import java.util.concurrent.atomic.AtomicReference

/** Main-thread snapshot source. It is never called by the GL renderer or during a gesture. */
class PanelSnapshotCapture(
    private val key: String,
    private val view: ComposeView,
    private val target: AtomicReference<Bitmap?>,
    private val onReady: () -> Unit,
) {
    private var lastCaptureUptime = 0L
    fun requestIfAllowed() {
        if (SystemClock.uptimeMillis() - lastCaptureUptime < MIN_CAPTURE_INTERVAL_MS) return
        lastCaptureUptime = SystemClock.uptimeMillis()
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(PanelTextureSpec.WIDTH, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(PanelTextureSpec.HEIGHT, android.view.View.MeasureSpec.EXACTLY))
        view.layout(0, 0, PanelTextureSpec.WIDTH, PanelTextureSpec.HEIGHT)
        val bitmap = Bitmap.createBitmap(PanelTextureSpec.WIDTH, PanelTextureSpec.HEIGHT, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        target.getAndSet(bitmap)?.recycle()
        Log.i("Shell3D.Capture", "$key bitmap queued ${bitmap.width}x${bitmap.height}")
        onReady()
    }
    // The cards are viewed at carousel distance; 384x540 halves each axis and
    // cuts texture memory/upload work to one quarter on older GPUs.
    companion object { const val WIDTH = PanelTextureSpec.WIDTH; const val HEIGHT = PanelTextureSpec.HEIGHT; const val MIN_CAPTURE_INTERVAL_MS = 1_500L }
}
