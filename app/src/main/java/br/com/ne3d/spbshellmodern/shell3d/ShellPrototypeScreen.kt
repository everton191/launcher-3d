package br.com.ne3d.spbshellmodern.shell3d

import android.content.Context
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.opengl.GLSurfaceView
import android.view.View
import android.view.MotionEvent
import android.view.Choreographer
import android.widget.FrameLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
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
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneRegistry
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneController
import br.com.ne3d.spbshellmodern.shell3d.widgets.debug.DebugWidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.debug.DebugWidgetDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.interaction.WidgetInteraction
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WorldTimeSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WorldTimeDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WorldTimeCities
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WorldTimeScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WidgetIds

/** GLES carousel fed with the current launcher workspace. Hidden Compose views provide live panel textures. */
@Composable fun ShellPrototypeScreen(
    panels: List<LauncherPanel>,
    selectedPanelId: String = panels.firstOrNull()?.id.orEmpty(),
    includeRealPanels: Boolean = true,
    exitOnTap: Boolean = true,
    gesturesEnabled: Boolean = true,
    debugWidgetScene: Boolean = false,
    worldTimeWidgetScene: Boolean = false,
    onExit: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) = key(panels.map { it.id }, selectedPanelId, includeRealPanels, exitOnTap, gesturesEnabled, debugWidgetScene, worldTimeWidgetScene) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { ShellPrototypeContainer(it, panels, selectedPanelId, includeRealPanels, exitOnTap, gesturesEnabled, debugWidgetScene, worldTimeWidgetScene, onExit) },
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
    debugWidgetScene: Boolean,
    worldTimeWidgetScene: Boolean,
    var onExit: (String) -> Unit,
) : FrameLayout(context) {
    private val hasRealSnapshots = includeRealPanels && !debugWidgetScene && !worldTimeWidgetScene
    private var captureRequested = false
    private val workspacePanels = panels.ifEmpty { listOf(panelTemplate(PanelType.HOME)) }
    private val pendingTextures = workspacePanels.map { ShellRenderer.PendingTexture(it.id, AtomicReference<Bitmap?>(null)) }.toTypedArray()
    private val widgetScene: WidgetScene? = if (debugWidgetScene) WidgetSceneRegistry().apply {
        register(WidgetSceneRegistry.DEBUG_SCENE) { DebugWidgetScene() }
    }.create(WidgetSceneRegistry.DEBUG_SCENE) else if (worldTimeWidgetScene) WidgetSceneRegistry.production().create(WidgetIds.WORLD_TIME) else null
    private val debugDataSource: DebugWidgetDataSource? = if (debugWidgetScene) DebugWidgetDataSource() else null
    private val worldTimeDataSource: WorldTimeDataSource? = if (worldTimeWidgetScene) WorldTimeDataSource().also { it.publishNow() } else null
    private val widgetDataSource: WidgetDataSource<out WidgetSnapshot>? = debugDataSource ?: worldTimeDataSource
    private val surface = ShellPrototypeView(context, pendingTextures, includeRealPanels, exitOnTap && !debugWidgetScene, gesturesEnabled,
        if (debugWidgetScene || worldTimeWidgetScene) emptyList() else workspacePanels.map { Panel3D(it.id, it.title, 0xFF36595D.toInt(), PanelTextureKind.REAL_SNAPSHOT) },
        widgetScene,
        widgetDataSource,
        debugDataSource,
        worldTimeDataSource,
        { snapshot -> updateWorldTimeInfo(snapshot) },
        workspacePanels.indexOfFirst { it.id == selectedPanelId }.coerceAtLeast(0)) {
            index -> onExit(workspacePanels.getOrNull(index)?.id ?: workspacePanels.first().id)
        }
    private val worldTimeInfo: TextView? = if (worldTimeWidgetScene) TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 18f
        setShadowLayer(6f, 0f, 2f, Color.BLACK)
        gravity = Gravity.CENTER
        setPadding(24, 18, 24, 28)
        updateWorldTimeInfo(worldTimeDataSource?.latest())
    } else null
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
        worldTimeInfo?.let { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)) }
        if (hasRealSnapshots) {
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
    private fun updateWorldTimeInfo(snapshot: WorldTimeSnapshot?) {
        val value = snapshot?.selectedValue() ?: return
        val city = WorldTimeCities.defaults.firstOrNull { it.id == value.id } ?: return
        worldTimeInfo?.text = "${city.displayName}  ${value.time}  ${value.offset}"
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
    private val widgetScene: WidgetScene?,
    private val widgetDataSource: WidgetDataSource<out WidgetSnapshot>?,
    private val debugDataSource: DebugWidgetDataSource?,
    private val worldTimeDataSource: WorldTimeDataSource?,
    private val onWorldTimeSnapshot: (WorldTimeSnapshot?) -> Unit,
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
    private val widgetController = widgetScene?.let { scene -> WidgetSceneController(scene, widgetDataSource ?: error("Widget scene requires a data source"), { post { scheduler.invalidateOnce() } }) { active -> post { if(active) scheduler.activate(FrameReason.WIDGET_ANIMATION) else scheduler.deactivate(FrameReason.WIDGET_ANIMATION) } } }
    private val renderConsumed = Runnable { scheduler.onRenderConsumed() }
    private var worldDownX = 0f
    private var worldLastX = 0f
    private var worldMoved = false
    private val worldClockRefresh = object : Runnable {
        override fun run() {
            worldTimeDataSource?.publishNow()
            onWorldTimeSnapshot(worldTimeDataSource?.latest())
            widgetController?.onSnapshotPublished()
            postDelayed(this, WORLD_CLOCK_REFRESH_MILLIS)
        }
    }
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
        widgetController = widgetController,
    )
    private val gestures = GestureController(engine, {
        removeCallbacks(settleMeasurement)
        scheduler.activate(FrameReason.PHYSICS)
        scheduler.invalidateOnce()
        postDelayed(settleMeasurement, MEASUREMENT_WINDOW_MS)
    }, onTap = { x ->
        if (widgetScene != null) {
            val localX = (x / width.coerceAtLeast(1).toFloat() - .5f) * 3f
            widgetController?.enqueue { widgetScene.interactions.dispatch(WidgetInteraction.Tap(localX, 0f)) }
            if (localX >= .6f) debugDataSource?.advance()
            worldTimeDataSource?.let { source ->
                val cityIndex = ((x / width.coerceAtLeast(1).toFloat()) * WorldTimeCities.defaults.size)
                    .toInt().coerceIn(0, WorldTimeCities.defaults.lastIndex)
                val city = WorldTimeCities.defaults[cityIndex]
                source.select(city.id)
                widgetController?.enqueue { (widgetScene as? WorldTimeScene)?.select(city.id) }
            }
            widgetController?.onSnapshotPublished()
        } else if (exitOnTap) {
            engine.beginExitForPanel(engine.panelIndexAt(x, width.toFloat()))
            scheduler.activate(FrameReason.TRANSITION)
            scheduler.invalidateOnce()
        }
    }, onGestureStarted = {
        scheduler.cancelDelayedWake()
        queueEvent { renderer.beginMeasurement() }
        scheduler.activate(FrameReason.INPUT)
    }, onGestureFinished = { scheduler.deactivate(FrameReason.INPUT) })
    init {
        setEGLContextClientVersion(3); setRenderer(renderer); renderMode = RENDERMODE_WHEN_DIRTY
        if (worldTimeDataSource != null) {
            setOnTouchListener { _, event -> onWorldTimeTouch(event) }
            post(worldClockRefresh)
        } else if (gesturesEnabled) {
            setOnTouchListener { _: View, event -> gestures.onTouch(event) }
        } else { isClickable = false; isFocusable = false }
        if (engine.entry.active) scheduler.activate(FrameReason.TRANSITION)
        scheduler.invalidateOnce()
    }
    fun onTextureAvailable() { scheduler.activate(FrameReason.TEXTURE_UPLOAD); scheduler.invalidateOnce() }
    fun pauseRenderer() {
        removeCallbacks(worldClockRefresh)
        widgetController?.pause()
        scheduler.pause()
        super.onPause()
    }
    fun resumeRenderer() {
        super.onResume()
        widgetController?.resume()
        if (worldTimeDataSource != null) post(worldClockRefresh)
        scheduler.resume()
        if (engine.entry.active || engine.exit.active) scheduler.activate(FrameReason.TRANSITION) else scheduler.invalidateOnce()
    }
    fun stopRenderer() {
        removeCallbacks(worldClockRefresh)
        scheduler.shutdown()
        queueEvent { renderer.release() }
    }
    private fun onWorldTimeTouch(event: MotionEvent): Boolean {
        val scene = widgetScene as? WorldTimeScene ?: return false
        val localX = (event.x / width.coerceAtLeast(1).toFloat() - .5f) * 3f
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                worldDownX = event.x
                worldLastX = event.x
                worldMoved = false
                widgetController?.enqueue { scene.interactions.dispatch(WidgetInteraction.DragStart(localX, 0f)) }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.x - worldLastX
                if (kotlin.math.abs(event.x - worldDownX) > TOUCH_SLOP_PX) worldMoved = true
                worldLastX = event.x
                widgetController?.enqueue { scene.drag(delta) }
                widgetController?.onSnapshotPublished()
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!worldMoved) {
                    val cityIndex = ((event.x / width.coerceAtLeast(1).toFloat()) * WorldTimeCities.defaults.size)
                        .toInt().coerceIn(0, WorldTimeCities.defaults.lastIndex)
                    val city = WorldTimeCities.defaults[cityIndex]
                    worldTimeDataSource?.select(city.id)
                    onWorldTimeSnapshot(worldTimeDataSource?.latest())
                    widgetController?.enqueue {
                        scene.interactions.dispatch(WidgetInteraction.Tap(localX, 0f))
                        scene.select(city.id)
                    }
                }
                widgetController?.enqueue { scene.interactions.dispatch(WidgetInteraction.DragEnd) }
                widgetController?.onSnapshotPublished()
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                widgetController?.enqueue { scene.interactions.dispatch(WidgetInteraction.DragEnd) }
                true
            }
            else -> false
        }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Reserve the edge strips for horizontal carousel rotation. Without this,
        // Android's predictive-back gesture consumes a swipe before GLSurfaceView.
        val edge = (32f * resources.displayMetrics.density).toInt().coerceAtMost(w / 3)
        if (edge > 0) systemGestureExclusionRects = listOf(Rect(0, 0, edge, h), Rect(w - edge, 0, w, h))
    }
    override fun onDetachedFromWindow() { stopRenderer(); super.onDetachedFromWindow() }
    private companion object {
        const val MEASUREMENT_WINDOW_MS = 30_000L
        const val WORLD_CLOCK_REFRESH_MILLIS = 60_000L
        const val TOUCH_SLOP_PX = 12f
    }
}
