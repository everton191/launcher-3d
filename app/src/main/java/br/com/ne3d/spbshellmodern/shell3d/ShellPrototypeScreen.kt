package br.com.ne3d.spbshellmodern.shell3d

import android.content.Context
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.opengl.GLSurfaceView
import android.view.View
import android.view.Choreographer
import android.widget.FrameLayout
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import br.com.ne3d.spbshellmodern.model.PanelType
import br.com.ne3d.spbshellmodern.model.LauncherPanel
import br.com.ne3d.spbshellmodern.model.panelTemplate
import br.com.ne3d.spbshellmodern.shell3d.core.ShellEngine
import br.com.ne3d.spbshellmodern.shell3d.core.ShellRenderer
import br.com.ne3d.spbshellmodern.shell3d.core.FrameReason
import br.com.ne3d.spbshellmodern.shell3d.core.FrameScheduler
import br.com.ne3d.spbshellmodern.shell3d.input.GestureController
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D
import br.com.ne3d.spbshellmodern.shell3d.scene.PanelTextureKind
import br.com.ne3d.spbshellmodern.shell3d.texture.PanelSnapshotCapture
import br.com.ne3d.spbshellmodern.ui.LauncherPanel
import java.util.concurrent.atomic.AtomicReference
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** GLES carousel fed with the current launcher workspace. Hidden Compose views provide live panel textures. */
@Composable fun ShellPrototypeScreen(
    panels: List<LauncherPanel>,
    selectedPanelId: String = panels.firstOrNull()?.id.orEmpty(),
    includeRealPanels: Boolean = true,
    exitOnTap: Boolean = true,
    gesturesEnabled: Boolean = true,
    onExit: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) = key(panels.map { it.id }, selectedPanelId, includeRealPanels, exitOnTap, gesturesEnabled) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { ShellPrototypeContainer(it, panels, selectedPanelId, includeRealPanels, exitOnTap, gesturesEnabled, onExit) },
        update = { it.onExit = onExit; it.bindLifecycle(lifecycleOwner) },
        modifier = modifier,
    )
}

private class ShellPrototypeContainer(
    context: Context,
    panels: List<LauncherPanel>,
    selectedPanelId: String,
    includeRealPanels: Boolean,
    exitOnTap: Boolean,
    gesturesEnabled: Boolean,
    var onExit: (String) -> Unit,
) : FrameLayout(context) {
    private val hasRealSnapshots = includeRealPanels
    private var captureRequested = false
    private val workspacePanels = panels.ifEmpty { listOf(panelTemplate(PanelType.HOME)) }
    private val pendingTextures = workspacePanels.map { ShellRenderer.PendingTexture(it.id, AtomicReference<Bitmap?>(null)) }.toTypedArray()
    private val surface = ShellPrototypeView(context, pendingTextures, includeRealPanels, exitOnTap, gesturesEnabled,
        workspacePanels.map { Panel3D(it.id, it.title, 0xFF36595D.toInt(), PanelTextureKind.REAL_SNAPSHOT) },
        workspacePanels.indexOfFirst { it.id == selectedPanelId }.coerceAtLeast(0)) {
            index -> onExit(workspacePanels.getOrNull(index)?.id ?: workspacePanels.first().id)
        }
    private val captures = workspacePanels.map(::snapshot)
    private var lifecycleOwner: LifecycleOwner? = null
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) = surface.pauseRenderer()
        override fun onResume(owner: LifecycleOwner) = surface.resumeRenderer()
        override fun onDestroy(owner: LifecycleOwner) = surface.stopRenderer()
    }
    init {
        Log.i("Shell3D.Capture", "container panels=${workspacePanels.size}")
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (includeRealPanels) {
            for (capture in captures) addView(capture.view, LayoutParams(PanelSnapshotCapture.WIDTH, PanelSnapshotCapture.HEIGHT))
            surface.onSurfaceReady = { captureRequested = false; requestCaptureAfterLayout() }
        }
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        requestCaptureAfterLayout()
    }
    private fun requestCaptureAfterLayout() {
        if (hasRealSnapshots && !captureRequested && width > 0 && height > 0) {
            captureRequested = true
            for (capture in captures) capture.capture.requestIfAllowed()
        }
    }
    fun bindLifecycle(owner: LifecycleOwner) {
        if (lifecycleOwner === owner) return
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = owner
        owner.lifecycle.addObserver(lifecycleObserver)
    }
    override fun onDetachedFromWindow() {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        super.onDetachedFromWindow()
    }
    private fun snapshot(panel: LauncherPanel): SnapshotHolder {
        val view = ComposeView(context).apply {
            setContent { MaterialTheme { LauncherPanel(panel, emptyList(), interactive = false, onLaunch = {}) } }
            translationX = -10_000f
        }
        val target = pendingTextures.first { it.key == panel.id }.bitmap
        return SnapshotHolder(view, PanelSnapshotCapture(panel.id, view, target) { surface.onTextureAvailable() })
    }
    private data class SnapshotHolder(val view: ComposeView, val capture: PanelSnapshotCapture)
}

private class ShellPrototypeView(
    context: Context,
    pendingTextures: Array<ShellRenderer.PendingTexture>,
    includeRealPanels: Boolean,
    exitOnTap: Boolean,
    gesturesEnabled: Boolean,
    panels: List<Panel3D>,
    initialSelectedIndex: Int,
    private val onExit: (Int) -> Unit,
) : GLSurfaceView(context) {
    private var surfaceReady = false
    var onSurfaceReady: () -> Unit = {}
        set(value) {
            field = value
            if (surfaceReady) post { field() }
        }
    private val engine = ShellEngine(includeRealSnapshots = includeRealPanels, panels = panels, initialSelectedIndex = initialSelectedIndex)
    private val scheduler = FrameScheduler(Choreographer.getInstance()) { requestRender() }
    private val renderConsumed = Runnable { scheduler.onRenderConsumed() }
    private val settleMeasurement = Runnable {
        queueEvent { renderer.publishMeasurement("input-30s") }
    }
    private val renderer = ShellRenderer(
        engine = engine,
        onFrameDrawn = { post(renderConsumed) },
        onAutoWakeNeeded = { post { scheduler.wakeOnceAfter(ShellEngine.AUTO_ROTATE_DELAY_MILLIS) { engine.beginAutoRotation(); scheduler.activate(FrameReason.AUTO_ROTATE); scheduler.invalidateOnce() } } },
        onEngineIdle = { post {
        scheduler.deactivate(FrameReason.PHYSICS)
        scheduler.deactivate(FrameReason.TRANSITION)
        scheduler.deactivate(FrameReason.AUTO_ROTATE)
        } },
        onSurfaceReady = { post {
            surfaceReady = true
            onSurfaceReady()
        } },
        onExitFinished = { post { onExit(engine.selectedIndex) } },
        onTextureUploadsDrained = { post { scheduler.deactivate(FrameReason.TEXTURE_UPLOAD) } },
        pendingTextures = pendingTextures,
    )
    private val gestures = GestureController(engine, {
        removeCallbacks(settleMeasurement)
        scheduler.activate(FrameReason.PHYSICS)
        scheduler.invalidateOnce()
        // A touch move must reach GLSurfaceView in the same UI turn; the scheduler
        // still owns subsequent frames for fling and snap.
        requestRender()
        postDelayed(settleMeasurement, MEASUREMENT_WINDOW_MS)
    }, onTap = { x ->
        if (exitOnTap) {
            // A card is a launcher page: leave the ring and show that page full-screen.
            onExit(engine.panelIndexAt(x, width.toFloat()))
        }
    }, onGestureStarted = {
        scheduler.cancelDelayedWake()
        queueEvent { renderer.beginMeasurement() }
        scheduler.activate(FrameReason.INPUT)
    }, onGestureFinished = { scheduler.deactivate(FrameReason.INPUT) })
    init {
        setEGLContextClientVersion(3); setRenderer(renderer); renderMode = RENDERMODE_WHEN_DIRTY
        if (gesturesEnabled) setOnTouchListener { _: View, event -> gestures.onTouch(event) }
        else { isClickable = false; isFocusable = false }
        if (engine.entry.active) scheduler.activate(FrameReason.TRANSITION)
        scheduler.invalidateOnce()
    }
    fun onTextureAvailable() { scheduler.activate(FrameReason.TEXTURE_UPLOAD); scheduler.invalidateOnce() }
    fun pauseRenderer() { scheduler.pause(); super.onPause() }
    fun resumeRenderer() {
        super.onResume()
        scheduler.resume()
        if (engine.entry.active || engine.exit.active) scheduler.activate(FrameReason.TRANSITION) else scheduler.invalidateOnce()
    }
    fun stopRenderer() { scheduler.shutdown(); queueEvent { renderer.release() } }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Reserve the edge strips for horizontal carousel rotation. Without this,
        // Android's predictive-back gesture consumes a swipe before GLSurfaceView.
        val edge = (32f * resources.displayMetrics.density).toInt().coerceAtMost(w / 3)
        if (edge > 0) systemGestureExclusionRects = listOf(Rect(0, 0, edge, h), Rect(w - edge, 0, w, h))
    }
    override fun onDetachedFromWindow() { stopRenderer(); super.onDetachedFromWindow() }
    private companion object { const val MEASUREMENT_WINDOW_MS = 30_000L }
}
