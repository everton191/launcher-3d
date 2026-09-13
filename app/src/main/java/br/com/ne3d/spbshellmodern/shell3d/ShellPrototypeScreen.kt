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
import br.com.ne3d.spbshellmodern.model.WeatherInfo
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
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WorldTimeScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WorldTimeCities
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WidgetIds
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WorldTimeClockSchedule
import br.com.ne3d.spbshellmodern.shell3d.widgets.weather.WeatherWidgetDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.weather.WeatherWidgetIds
import br.com.ne3d.spbshellmodern.shell3d.widgets.music.MusicDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.music.MusicSessionRepository
import br.com.ne3d.spbshellmodern.shell3d.widgets.music.MusicScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.CalendarDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.PersonalWidgetRepository
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.PhotosDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.PhotosScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.PhotosTextureStore
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.ContactsDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.system.NotificationDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.system.NotificationBridge
import br.com.ne3d.spbshellmodern.shell3d.widgets.system.NotificationAccess
import br.com.ne3d.spbshellmodern.shell3d.widgets.system.NotificationScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.system.SystemDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.system.SystemStateRepository

/** GLES carousel fed with the current launcher workspace. Hidden Compose views provide live panel textures. */
@Composable fun ShellPrototypeScreen(
    panels: List<LauncherPanel>,
    selectedPanelId: String = panels.firstOrNull()?.id.orEmpty(),
    includeRealPanels: Boolean = true,
    exitOnTap: Boolean = true,
    gesturesEnabled: Boolean = true,
    debugWidgetScene: Boolean = false,
    worldTimeWidgetScene: Boolean = false,
    widgetSceneType: WidgetSceneType? = null,
    weatherInfo: WeatherInfo? = null,
    onExit: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) = key(panels.map { it.id }, selectedPanelId, includeRealPanels, exitOnTap, gesturesEnabled, debugWidgetScene, worldTimeWidgetScene, widgetSceneType) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { ShellPrototypeContainer(it, panels, selectedPanelId, includeRealPanels, exitOnTap, gesturesEnabled, debugWidgetScene, worldTimeWidgetScene, widgetSceneType, weatherInfo, onExit) },
        update = { it.onExit = onExit; it.updateWeather(weatherInfo); it.bindLifecycle(lifecycleOwner) },
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
    private val requestedWidgetScene: WidgetSceneType?,
    private val weatherInfo: WeatherInfo?,
    var onExit: (String) -> Unit,
) : FrameLayout(context) {
    private var lastWeatherInfo: WeatherInfo? = weatherInfo
    private val sceneType = requestedWidgetScene ?: if (worldTimeWidgetScene) WidgetSceneType.WORLD_TIME else null
    private val hasRealSnapshots = includeRealPanels && !debugWidgetScene && sceneType == null
    @Volatile private var photosDetached = false
    private var captureRequested = false
    private val workspacePanels = panels.ifEmpty { listOf(panelTemplate(PanelType.HOME)) }
    private val pendingTextures = workspacePanels.map { ShellRenderer.PendingTexture(it.id, AtomicReference<Bitmap?>(null)) }.toTypedArray()
    private val photosTextureStore: PhotosTextureStore? = if (sceneType == WidgetSceneType.PHOTOS) PhotosTextureStore() else null
    private val widgetScene: WidgetScene? = if (debugWidgetScene) WidgetSceneRegistry().apply {
        register(WidgetSceneRegistry.DEBUG_SCENE) { DebugWidgetScene() }
    }.create(WidgetSceneRegistry.DEBUG_SCENE) else when (sceneType) {
        WidgetSceneType.WORLD_TIME -> WidgetSceneRegistry.production().create(WidgetIds.WORLD_TIME)
        WidgetSceneType.WEATHER -> WidgetSceneRegistry.production().create(WeatherWidgetIds.WEATHER)
        WidgetSceneType.MUSIC -> MusicScene { action -> post { when (action) { MusicScene.Action.PREVIOUS -> musicRepository?.previous(); MusicScene.Action.TOGGLE -> musicRepository?.togglePlayPause(); MusicScene.Action.NEXT -> musicRepository?.next() } } }
        WidgetSceneType.CALENDAR -> WidgetSceneRegistry.production().create(WidgetSceneRegistry.CALENDAR)
        WidgetSceneType.PHOTOS -> PhotosScene(photosTextureStore ?: error("Photos texture store unavailable"))
        WidgetSceneType.CONTACTS -> WidgetSceneRegistry.production().create(WidgetSceneRegistry.CONTACTS)
        WidgetSceneType.NOTIFICATIONS -> WidgetSceneRegistry.production().create(WidgetSceneRegistry.NOTIFICATIONS)?.also { (it as? NotificationScene)?.onOpen = { key -> post { NotificationBridge.open(key) } } }
        WidgetSceneType.SYSTEM -> WidgetSceneRegistry.production().create(WidgetSceneRegistry.SYSTEM)
        null -> null
    }
    private val debugDataSource: DebugWidgetDataSource? = if (debugWidgetScene) DebugWidgetDataSource() else null
    private val worldTimeDataSource: WorldTimeDataSource? = if (sceneType == WidgetSceneType.WORLD_TIME) WorldTimeDataSource().also { it.publishNow() } else null
    private val weatherDataSource: WeatherWidgetDataSource? = if (sceneType == WidgetSceneType.WEATHER) WeatherWidgetDataSource().also { it.publishWeather(weatherInfo) } else null
    private val musicDataSource: MusicDataSource? = if (sceneType == WidgetSceneType.MUSIC) MusicDataSource() else null
    private val musicRepository: MusicSessionRepository? = if (sceneType == WidgetSceneType.MUSIC) MusicSessionRepository(context) else null
    private val calendarDataSource: CalendarDataSource? = if (sceneType == WidgetSceneType.CALENDAR) CalendarDataSource() else null
    private val photosDataSource: PhotosDataSource? = if (sceneType == WidgetSceneType.PHOTOS) PhotosDataSource() else null
    private val contactsDataSource: ContactsDataSource? = if (sceneType == WidgetSceneType.CONTACTS) ContactsDataSource() else null
    private val notificationDataSource: NotificationDataSource? = if (sceneType == WidgetSceneType.NOTIFICATIONS) NotificationDataSource() else null
    private val systemDataSource: SystemDataSource? = if (sceneType == WidgetSceneType.SYSTEM) SystemDataSource() else null
    private val widgetDataSource: WidgetDataSource<out WidgetSnapshot>? = debugDataSource ?: worldTimeDataSource ?: weatherDataSource ?: musicDataSource ?: calendarDataSource ?: photosDataSource ?: contactsDataSource ?: notificationDataSource ?: systemDataSource
    private val surface = ShellPrototypeView(context, pendingTextures, includeRealPanels, exitOnTap && !debugWidgetScene, gesturesEnabled,
        if (debugWidgetScene || sceneType != null) emptyList() else workspacePanels.map { Panel3D(it.id, it.title, 0xFF36595D.toInt(), PanelTextureKind.REAL_SNAPSHOT) },
        widgetScene,
        widgetDataSource,
        debugDataSource,
        worldTimeDataSource,
        weatherDataSource,
        { snapshot -> updateWorldTimeInfo(snapshot) },
        workspacePanels.indexOfFirst { it.id == selectedPanelId }.coerceAtLeast(0)) {
            index -> onExit(workspacePanels.getOrNull(index)?.id ?: workspacePanels.first().id)
        }
    private val worldTimeInfo: TextView? = if (sceneType == WidgetSceneType.WORLD_TIME) TextView(context).apply {
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
        override fun onResume(owner: LifecycleOwner) { surface.resumeRenderer(); refreshSystem(); musicRepository?.start(musicDataSource ?: return) }
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
        if (calendarDataSource != null) refreshCalendar()
        if (photosDataSource != null) refreshPhotos()
        if (contactsDataSource != null) refreshContacts()
        notificationDataSource?.let { source -> if (NotificationAccess.isAuthorized(context)) NotificationBridge.register(source) else source.publishNotifications(false, emptyList()) }
        if (systemDataSource != null) refreshSystem()
        musicRepository?.start(musicDataSource ?: error("Music source unavailable"))
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
    fun updateWeather(weather: WeatherInfo?) {
        if (weather == lastWeatherInfo) return
        lastWeatherInfo = weather
        surface.updateWeather(weather)
    }
    private fun refreshCalendar() {
        Thread {
            val (available, events) = PersonalWidgetRepository(context).calendar()
            calendarDataSource?.publishEvents(available, events)
            post { surface.onWidgetSnapshotPublished() }
        }.start()
    }
    private fun refreshPhotos() {
        Thread {
            val (available, photos) = PersonalWidgetRepository(context).photos()
            if (photosDetached) return@Thread
            if (available) photosTextureStore?.load(context.contentResolver, photos) { !photosDetached }
            if (photosDetached) return@Thread
            photosDataSource?.publishPhotos(available, photos)
            post { if (!photosDetached) surface.onWidgetSnapshotPublished() }
        }.start()
    }
    private fun refreshSystem() { systemDataSource?.let { SystemStateRepository(context).publish(it); surface.onWidgetSnapshotPublished() } }
    private fun refreshContacts() {
        Thread {
            val (available, contacts) = PersonalWidgetRepository(context).contacts()
            contactsDataSource?.publishContacts(available, contacts)
            post { surface.onWidgetSnapshotPublished() }
        }.start()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        photosDetached = false
    }
    override fun onDetachedFromWindow() {
        photosDetached = true
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        photosTextureStore?.release()
        notificationDataSource?.let(NotificationBridge::unregister)
        musicRepository?.stop()
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
    private val weatherDataSource: WeatherWidgetDataSource?,
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
    private val worldClockRefresh = Runnable { refreshWorldTimeAndSchedule() }
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
        widgetDensity = resources.displayMetrics.density,
        widgetResources = resources,
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
        (widgetScene as? WorldTimeScene)?.setSelectionListener { cityId ->
            post {
                worldTimeDataSource?.select(cityId)
                onWorldTimeSnapshot(worldTimeDataSource?.latest())
                widgetController?.onSnapshotPublished()
            }
        }
        if (worldTimeDataSource != null) {
            setOnTouchListener { _, event -> onWorldTimeTouch(event) }
            refreshWorldTimeAndSchedule()
        } else if (widgetScene is NotificationScene) {
            setOnTouchListener { _, event -> onNotificationTouch(event) }
        } else if (gesturesEnabled) {
            setOnTouchListener { _: View, event -> gestures.onTouch(event) }
        } else { isClickable = false; isFocusable = false }
        if (engine.entry.active) scheduler.activate(FrameReason.TRANSITION)
        scheduler.invalidateOnce()
    }
    fun onTextureAvailable() { scheduler.activate(FrameReason.TEXTURE_UPLOAD); scheduler.invalidateOnce() }
    fun updateWeather(weather: WeatherInfo?) {
        val source = weatherDataSource ?: return
        source.publishWeather(weather)
        widgetController?.onSnapshotPublished()
    }
    fun onWidgetSnapshotPublished() { widgetController?.onSnapshotPublished() }
    fun pauseRenderer() {
        removeCallbacks(worldClockRefresh)
        widgetController?.pause()
        scheduler.pause()
        super.onPause()
    }
    fun resumeRenderer() {
        super.onResume()
        widgetController?.resume()
        if (worldTimeDataSource != null) refreshWorldTimeAndSchedule()
        scheduler.resume()
        if (engine.entry.active || engine.exit.active) scheduler.activate(FrameReason.TRANSITION) else scheduler.invalidateOnce()
    }
    fun stopRenderer() {
        removeCallbacks(worldClockRefresh)
        scheduler.shutdown()
        queueEvent { renderer.release() }
    }
    private fun refreshWorldTimeAndSchedule() {
        removeCallbacks(worldClockRefresh)
        worldTimeDataSource?.publishNow()
        onWorldTimeSnapshot(worldTimeDataSource?.latest())
        widgetController?.onSnapshotPublished()
        if (worldTimeDataSource != null) postDelayed(worldClockRefresh, WorldTimeClockSchedule.delayToNextMinute(System.currentTimeMillis()))
    }
    private fun onNotificationTouch(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val scene = widgetScene as? NotificationScene ?: return false
        widgetController?.enqueue { scene.interactions.dispatch(WidgetInteraction.Tap(event.x, event.y)) }
        widgetController?.onSnapshotPublished()
        return true
    }    private fun onWorldTimeTouch(event: MotionEvent): Boolean {
        val scene = widgetScene as? WorldTimeScene ?: return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                worldDownX = event.x; worldLastX = event.x; worldMoved = false
                widgetController?.enqueue { scene.interactions.dispatch(WidgetInteraction.DragStart(event.x, event.y)) }; true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.x - worldLastX
                if (kotlin.math.abs(event.x - worldDownX) > TOUCH_SLOP_PX) worldMoved = true
                worldLastX = event.x
                widgetController?.enqueue { scene.interactions.dispatch(WidgetInteraction.Drag(delta, 0f)) }
                widgetController?.onSnapshotPublished(); true
            }
            MotionEvent.ACTION_UP -> {
                if (!worldMoved) widgetController?.enqueue { scene.interactions.dispatch(WidgetInteraction.Tap(event.x, event.y)) }
                widgetController?.enqueue { scene.interactions.dispatch(WidgetInteraction.DragEnd) }
                widgetController?.onSnapshotPublished(); true
            }
            MotionEvent.ACTION_CANCEL -> { widgetController?.enqueue { scene.interactions.dispatch(WidgetInteraction.DragEnd) }; true }
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
        const val TOUCH_SLOP_PX = 12f
    }
}


