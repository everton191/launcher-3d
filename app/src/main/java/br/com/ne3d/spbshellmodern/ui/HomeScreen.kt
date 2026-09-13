package br.com.ne3d.spbshellmodern.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.widget.Toast
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.dp
import br.com.ne3d.spbshellmodern.data.AppRepository
import br.com.ne3d.spbshellmodern.data.PanelPreferencesRepository
import br.com.ne3d.spbshellmodern.data.WidgetRepository
import br.com.ne3d.spbshellmodern.model.*
import br.com.ne3d.spbshellmodern.engine.*
import br.com.ne3d.spbshellmodern.BuildConfig
import br.com.ne3d.spbshellmodern.shell3d.ShellPrototypeScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(homeRequestId: Int = 0, initialPanelId: String? = null) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val widgets = remember { WidgetRepository(context) }
    val apps by produceState<List<AppItem>>(emptyList(), repository) {
        value = withContext(Dispatchers.IO) { repository.load() }
    }
    val panelPreferences = remember { PanelPreferencesRepository(context) }
    var panels by remember { mutableStateOf(initialPanels()) }
    var homePanelId by remember { mutableStateOf("home") }
    var storedPanels by remember { mutableStateOf<List<LauncherPanel>>(emptyList()) }
    var tray by remember { mutableStateOf<List<ShellItem>>(emptyList()) }
    var dock by remember { mutableStateOf<List<ShellItem>>(emptyList()) }
    val persistMutex = remember { Mutex() }
    var workspacePendingSave by remember { mutableStateOf<ShellWorkspace?>(null) }
    var showPanelSettings by remember { mutableStateOf(false) }
    var showAddItem by remember { mutableStateOf(false) }
    var itemTargetPanel by remember { mutableStateOf<String?>(null) }
    var editingItem by remember { mutableStateOf<ShellItem?>(null) }
    var openFolder by remember { mutableStateOf<ShellItem?>(null) }
    var expandedFolderWidget by remember { mutableStateOf<ShellItem?>(null) }
    var dockTarget by remember { mutableStateOf<Int?>(null) }
    var draggedItem by remember { mutableStateOf<ShellItem?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var itemDragStart by remember { mutableStateOf(Offset.Zero) }
    var trayBounds by remember { mutableStateOf(Rect.Zero) }
    var trashBounds by remember { mutableStateOf(Rect.Zero) }
    var wheelBounds by remember { mutableStateOf(Rect.Zero) }
    val itemBounds = remember { mutableMapOf<String, Rect>() }
    var interactionEpoch by remember { mutableLongStateOf(0L) }
    var magic by remember { mutableStateOf(false) }
    var fingerDown by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val haptics = LocalHapticFeedback.current
    fun touch() { magic = false; interactionEpoch++ }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) magic = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var selectedPanelIndex by rememberSaveable { mutableIntStateOf(0) }
    var launcherMode by rememberSaveable { mutableStateOf(LauncherMode.NORMAL) }
    var drag by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(targetRotationForPanel(selectedPanelIndex, panels.size)) }
    var carouselProgress by remember { mutableFloatStateOf(if (launcherMode == LauncherMode.NORMAL) 0f else 1f) }
    var critical by remember { mutableStateOf(false) }
    LaunchedEffect(initialPanelId, panels) {
        initialPanelId?.let { id ->
            val index = panels.indexOfFirst { it.id == id }
            if (index >= 0) { selectedPanelIndex = index; rotation = targetRotationForPanel(index, panels.size) }
        }
    }
    // A cancelled Compose animation must never leave the normal workspace unable
    // to receive another horizontal swipe.
    LaunchedEffect(critical, launcherMode) {
        if (critical && launcherMode == LauncherMode.NORMAL) {
            delay(900)
            if (launcherMode == LauncherMode.NORMAL) {
                critical = false
                drag = 0f
            }
        }
    }
    val scope = rememberCoroutineScope()
    var motion by remember { mutableStateOf<Job?>(null) }
    var camera by remember { mutableStateOf<Job?>(null) }
    val carouselConfig = remember { DefaultCarouselConfig }
    var showAddPanel by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var weather by remember { mutableStateOf<WeatherInfo?>(null) }
    var weatherCity by rememberSaveable { mutableStateOf("") }
    var loadedPreferences by remember { mutableStateOf(false) }
    fun loadPhotos() {
        scope.launch { photos = withContext(Dispatchers.IO) {
            widgets.loadRecentPhotos(if (context.resources.configuration.smallestScreenWidthDp >= 600) 9 else 7)
        } }
    }
    fun loadWeather(city: String = weatherCity) {
        scope.launch {
            withContext(Dispatchers.IO) { panelPreferences.saveWeatherCity(city) }
            weather = try { withContext(Dispatchers.IO) { widgets.loadWeather(city.ifBlank { null }) } }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { null }
            if (weather == null) Toast.makeText(context, "Localização ou conexão indisponível", Toast.LENGTH_SHORT).show()
        }
    }
    val photoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadPhotos()
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadWeather()
    }
    fun uniqueWidgetPanels(source: List<LauncherPanel>, alreadyUsed: MutableSet<PanelType>): List<LauncherPanel> =
        source.filter { panel -> panel.type == PanelType.APPS || alreadyUsed.add(panel.type) }
    LaunchedEffect(panelPreferences) {
        val original = withContext(Dispatchers.IO) { panelPreferences.loadWorkspace() }
        // Old drag builds could persist two copies of a live widget. Keep the first
        // active copy and one removed copy only when that widget is not active.
        val usedTypes = mutableSetOf<PanelType>()
        val workspace = original.copy(
            panels = uniqueWidgetPanels(original.panels, usedTypes),
            storedPanels = uniqueWidgetPanels(original.storedPanels, usedTypes)
        )
        if (workspace != original) withContext(Dispatchers.IO) { panelPreferences.saveWorkspace(workspace) }
        val savedPanels = workspace.panels
        val activeId = workspace.activePanelId
        weatherCity = withContext(Dispatchers.IO) { panelPreferences.loadWeatherCity() }
        panels = savedPanels
        homePanelId = workspace.homePanelId
        storedPanels = workspace.storedPanels
        tray = workspace.tray
        dock = workspace.dock
        selectedPanelIndex = savedPanels.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        rotation = targetRotationForPanel(selectedPanelIndex, panels.size)
        loadedPreferences = true
        loadPhotos()
        if (weatherCity.isNotBlank()) loadWeather(weatherCity)
    }
    LaunchedEffect(workspacePendingSave) {
        val snapshot = workspacePendingSave ?: return@LaunchedEffect
        persistMutex.withLock {
            withContext(Dispatchers.IO) { panelPreferences.saveWorkspace(snapshot) }
            // A newer interaction may have occurred while DataStore wrote this snapshot.
            // In that case its effect remains queued and is the only state allowed to clear.
            if (workspacePendingSave === snapshot) workspacePendingSave = null
        }
    }
    fun persistPanels() {
        val snapshot = ShellWorkspace(panels, panels[selectedPanelIndex.coerceIn(panels.indices)].id, homePanelId, storedPanels, tray, dock)
        if (loadedPreferences) workspacePendingSave = snapshot
    }
    fun updateItems(panelId: String, transform: (List<ShellItem>) -> List<ShellItem>) {
        panels = panels.map { if (it.id == panelId) it.copy(widgets = transform(it.widgets), customized = true) else it }
        persistPanels()
    }
    fun seedItems(panel: LauncherPanel): List<ShellItem> = if (panel.customized || panel.widgets.isNotEmpty()) panel.widgets else {
        if (panel.type in listOf(PanelType.FOLDER, PanelType.FAVORITES)) {
            val components = context.getSharedPreferences("spb_utility_widgets", Context.MODE_PRIVATE)
                .getStringSet("folder.${panel.id}", emptySet()).orEmpty()
            val members = components.sorted().mapNotNull { component ->
                val parts = component.split('/', limit = 2)
                if (parts.size != 2) null else {
                    val app = apps.firstOrNull { it.packageName == parts[0] && it.className == parts[1] }
                    ShellItem(java.util.UUID.randomUUID().toString(), PanelType.APPS, WidgetPresentation.ICON,
                        parts[0], parts[1], app?.label ?: parts[0])
                }
            }
            if (panel.type == PanelType.FOLDER) listOf(ShellItem(panel.id, PanelType.FOLDER,
                WidgetPresentation.EXPANDED, title = panel.title, children = members)) else members
        }
        else listOf(ShellItem(panel.id, if (panel.type == PanelType.HOME) PanelType.CLOCK else panel.type,
            WidgetPresentation.EXPANDED, title = if (panel.type == PanelType.HOME) "Relógio" else panel.title))
    }
    fun beginEditing() {
        val panel = panels[selectedPanelIndex]
        if (panel.type == PanelType.APPS) return
        touch(); motion?.cancel(); camera?.cancel(); carouselProgress = 0f; critical = false
        launcherMode = LauncherMode.EDIT_ITEMS
        updateItems(panel.id) { seedItems(panel) }
    }
    fun removeItemEverywhere(id: String) {
        fun remove(items: List<ShellItem>): List<ShellItem> = items.filterNot { it.id == id }.map { it.copy(children = remove(it.children)) }
        panels = panels.map { it.copy(widgets = remove(it.widgets)) }
        tray = remove(tray)
    }
    fun releaseAndroidWidgets(item: ShellItem) {
        if (item.type == PanelType.ANDROID_WIDGET) removeSpbAndroidWidget(context, item.id)
        item.children.forEach(::releaseAndroidWidgets)
    }
    fun editNeighbor(delta: Int): Int = (1..panels.size).map { (selectedPanelIndex + delta * it).mod(panels.size) }
        .firstOrNull { panels[it].type != PanelType.APPS } ?: selectedPanelIndex
    fun startItemDrag(item: ShellItem, position: Offset) {
        touch(); draggedItem = item; dragPosition = position; itemDragStart = position
        launcherMode = LauncherMode.EDIT_ITEMS
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    fun finishItemDrag() {
        val item = draggedItem ?: return
        val target = panels[selectedPanelIndex]
        val hit = target.widgets.indexOfFirst { itemBounds[it.id]?.contains(dragPosition) == true }
        when {
            trashBounds.contains(dragPosition) -> {
                removeItemEverywhere(item.id)
                releaseAndroidWidgets(item)
            }
            trayBounds.contains(dragPosition) -> { removeItemEverywhere(item.id); tray = tray + item }
            wheelBounds.contains(dragPosition) -> {
                if ((dragPosition - itemDragStart).getDistance() < 12f) editingItem = item
                else {
                    removeItemEverywhere(item.id)
                    updateItems(target.id) { list -> list.toMutableList().apply { add(if (hit < 0) size else hit.coerceAtMost(size), item) } }
                }
            }
        }
        draggedItem = null; persistPanels()
    }
    fun addItem(type: PanelType? = null, app: AppItem? = null) {
        val item = ShellItem(java.util.UUID.randomUUID().toString(), type ?: PanelType.HOME,
            if (app != null) WidgetPresentation.ICON else if (type == PanelType.FOLDER) WidgetPresentation.ROW else WidgetPresentation.COMPACT,
            app?.packageName, app?.className, app?.label ?: panelTemplate(type!!).title)
        val dockIndex = dockTarget
        val folder = openFolder
        when {
            dockIndex != null -> {
                val current = dock.toMutableList()
                while (current.size <= dockIndex) current += ShellItem("dock-${current.size}", PanelType.HOME)
                current[dockIndex] = item; dock = current; dockTarget = null
            }
            folder?.type == PanelType.FOLDER -> {
                panels = panels.map { p -> p.copy(widgets = p.widgets.map { if (it.id == folder.id) it.copy(children = it.children + item) else it }) }
                openFolder = folder.copy(children = folder.children + item)
            }
            else -> {
                val id = itemTargetPanel ?: panels[selectedPanelIndex].id
                val target = panels.first { it.id == id }
                updateItems(id) { seedItems(target) + item }
            }
        }
        showAddItem = false; itemTargetPanel = null; persistPanels()
    }
    LaunchedEffect(homeRequestId, loadedPreferences) {
        if (homeRequestId > 0 && loadedPreferences) {
            motion?.cancel(); camera?.cancel(); critical = false; touch()
            selectedPanelIndex = panels.indexOfFirst { it.id == homePanelId }.coerceAtLeast(0)
            rotation = targetRotationForPanel(selectedPanelIndex, panels.size); drag = 0f
            carouselProgress = 0f; launcherMode = LauncherMode.NORMAL
        }
    }
    LaunchedEffect(selectedPanelIndex) { itemBounds.clear() }
    val edge = if (draggedItem == null || !wheelBounds.contains(dragPosition)) 0 else when {
        dragPosition.x < wheelBounds.left + 44 -> -1
        dragPosition.x > wheelBounds.right - 44 -> 1
        else -> 0
    }
    LaunchedEffect(edge, draggedItem?.id, selectedPanelIndex) {
        if (edge != 0) {
            delay(CarouselTuning.edgeHoverMillis)
            val next = editNeighbor(edge)
            if (panels[next].type != PanelType.APPS) {
                selectedPanelIndex = next; rotation = targetRotationForPanel(next, panels.size)
                val panel = panels[next]; updateItems(panel.id) { seedItems(panel) }
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(interactionEpoch, rotation, resumed && !critical && !fingerDown &&
                launcherMode == LauncherMode.CAROUSEL && carouselProgress >= 1f &&
                abs(rotation - targetRotationForPanel(selectedPanelIndex, panels.size)) < .01f)
        }.collectLatest { (_, _, idle) ->
            magic = false
            if (idle) {
                delay(CarouselTuning.idleDelayMillis)
                magic = true
            }
        }
    }
    fun enterOrganize(index: Int = selectedPanelIndex) {
        if (critical || launcherMode == LauncherMode.NORMAL) return
        motion?.cancel()
        touch()
        selectedPanelIndex = index
        rotation = nearestEquivalentTarget(index, panels.size, rotation)
        launcherMode = LauncherMode.ORGANIZE
    }
    fun movePanel(delta: Int) {
        val from = selectedPanelIndex
        val to = (from + delta).coerceIn(panels.indices)
        if (from == to) return
        panels = panels.toMutableList().apply { add(to, removeAt(from)) }
        selectedPanelIndex = to
        rotation = targetRotationForPanel(to, panels.size)
        persistPanels()
    }
    fun removePanel(id: String = panels[selectedPanelIndex].id) {
        val selected = panels.firstOrNull { it.id == id } ?: return
        if (!selected.removable) return
        val oldIndex = panels.indexOf(selected)
        storedPanels = storedPanels + selected
        if (homePanelId == selected.id) homePanelId = panels.first { it.type == PanelType.HOME }.id
        selectedPanelIndex = oldIndex.coerceAtMost(panels.lastIndex - 1)
        panels = panels.toMutableList().apply { removeAt(oldIndex) }
        rotation = targetRotationForPanel(selectedPanelIndex, panels.size)
        persistPanels()
    }
    fun restorePanel(id: String, insertion: Int = panels.size) {
        if (panels.size >= MAX_PANELS) return
        val stored = storedPanels.firstOrNull { it.id == id } ?: return
        if (panels.any { it.type == stored.type }) return
        selectedPanelIndex = insertion.coerceIn(0, panels.size)
        panels = panels.toMutableList().apply { add(selectedPanelIndex, stored) }
        storedPanels = storedPanels.filterNot { it.id == id }
        rotation = targetRotationForPanel(selectedPanelIndex, panels.size)
        showAddPanel = false
        persistPanels()
    }
    fun addPanel(type: PanelType, insertion: Int = panels.size) {
        if (panels.size >= MAX_PANELS) return
        if (panels.any { it.type == type } || storedPanels.any { it.type == type }) return
        selectedPanelIndex = insertion.coerceIn(0, panels.size)
        panels = panels.toMutableList().apply { add(selectedPanelIndex, panelTemplate(type, freshPanelId(type))) }
        rotation = targetRotationForPanel(selectedPanelIndex, panels.size)
        showAddPanel = false
        persistPanels()
    }

    fun openCarousel() {
        touch()
        if (critical || launcherMode != LauncherMode.NORMAL) return
        motion?.cancel(); drag = 0f
        rotation = targetRotationForPanel(selectedPanelIndex, panels.size)
        launcherMode = LauncherMode.CAROUSEL
        camera?.cancel()
        camera = scope.launch { animate(carouselProgress, 1f, animationSpec = tween(CarouselTuning.enterDuration)) { v, _ -> carouselProgress = v } }
    }
    fun startRotation() { if (!critical) { motion?.cancel(); openCarousel() } }
    fun selectPanel(index: Int) {
        if (critical) return
        motion?.cancel(); camera?.cancel(); critical = true
        motion = scope.launch {
            try {
                animate(rotation, nearestEquivalentTarget(index, panels.size, rotation),
                    animationSpec = spring(dampingRatio = 1f, stiffness = carouselConfig.snapStiffness)) { v, _ -> rotation = v }
                selectedPanelIndex = index
                rotation = targetRotationForPanel(index, panels.size)
                persistPanels()
                animate(carouselProgress, 0f, animationSpec = tween(CarouselTuning.exitDuration)) { v, _ -> carouselProgress = v }
                launcherMode = LauncherMode.NORMAL
            } finally { critical = false }
        }
    }
    fun finishRotation(pixelVelocity: Float) {
        motion = scope.launch {
            val velocity = (pixelVelocity * carouselConfig.dragSensitivity).coerceIn(-carouselConfig.maxAngularVelocity, carouselConfig.maxAngularVelocity)
            if (abs(velocity) > SNAP_THRESHOLD) {
                AnimationState(rotation, velocity).animateDecay(exponentialDecay(carouselConfig.friction)) {
                    rotation = value
                    if (abs(this.velocity) <= SNAP_THRESHOLD) cancelAnimation()
                }
            }
            val index = nearestPanel(rotation, panels.size)
            animate(rotation, nearestEquivalentTarget(index, panels.size, rotation),
                animationSpec = spring(dampingRatio = 1f, stiffness = carouselConfig.snapStiffness)) { v, _ -> rotation = v }
            selectedPanelIndex = index
            rotation = targetRotationForPanel(index, panels.size)
            persistPanels()
        }
    }
    fun finishNormal(width: Float, velocity: Float) {
        critical = true
        val next = (selectedPanelIndex + when {
            drag < -width * .18f || velocity < -800f -> 1
            drag > width * .18f || velocity > 800f -> -1
            else -> 0
        }).coerceIn(panels.indices)
        motion = scope.launch {
            try {
                animate(drag, (selectedPanelIndex - next) * width, animationSpec = tween(CarouselTuning.normalDuration)) { v, _ -> drag = v }
                selectedPanelIndex = next; drag = 0f
                rotation = targetRotationForPanel(next, panels.size)
                persistPanels()
            } finally { critical = false }
        }
    }
    var debug by remember { mutableStateOf(false) }
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(debug) {
        if (debug) {
            var start = withFrameNanos { it }; var frames = 0
            while (true) {
                val now = withFrameNanos { it }; frames++
                if (now - start >= 1_000_000_000) { fps = frames; frames = 0; start = now }
            }
        }
    }
    val homeRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val onLaunch: (AppItem) -> Unit = { app ->
        try { repository.launch(app) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, "Aplicativo indisponível", Toast.LENGTH_SHORT).show() }
        catch (_: SecurityException) { Toast.makeText(context, "Não foi possível abrir o aplicativo", Toast.LENGTH_SHORT).show() }
    }
    BackHandler {
        if (!critical) {
            if (launcherMode == LauncherMode.EDIT_ITEMS) { launcherMode = LauncherMode.NORMAL; draggedItem = null; persistPanels() }
            else if (launcherMode == LauncherMode.NORMAL) {
                selectedPanelIndex = panels.indexOfFirst { it.id == homePanelId }.coerceAtLeast(0)
                rotation = targetRotationForPanel(selectedPanelIndex, panels.size); drag = 0f
            }
            else if (launcherMode == LauncherMode.ORGANIZE) launcherMode = LauncherMode.CAROUSEL
            else selectPanel(selectedPanelIndex)
        }
    }
    val safeSelectedIndex = selectedPanelIndex.coerceIn(panels.indices)
    val selectedPanel = panels[safeSelectedIndex]
    val shellColors = darkColorScheme(primary = Color(0xFFDCD9C8), background = Color(0xFF121416))
    MaterialTheme(colorScheme = shellColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    fingerDown = event.changes.any { it.pressed }
                    if (event.changes.any { it.changedToDownIgnoreConsumed() }) touch()
                } }
            }) {
            androidx.compose.foundation.Image(
                painterResource(br.com.ne3d.spbshellmodern.R.drawable.yandex_shell_wallpaper), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .92f
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .10f + carouselProgress * .82f)))
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.systemGestures.only(WindowInsetsSides.Bottom)))
                .padding(horizontal = 3.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showPanelSettings = true }) { Text(selectedPanel.title, fontSize = 15.sp, maxLines = 1) }
                    if (launcherMode == LauncherMode.NORMAL) TextButton(onClick = { beginEditing() },
                        modifier = Modifier.semantics { contentDescription = "Editar" }, enabled = selectedPanel.type != PanelType.APPS) {
                        val glyphColor = LocalContentColor.current
                        androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                            drawLine(glyphColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
                            drawLine(glyphColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
                        }
                    }
                    else Text("${selectedPanelIndex + 1} / ${panels.size}", color = Color.LightGray, fontSize = 13.sp)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    val width = with(LocalDensity.current) { maxWidth.toPx() }
                    Box(Modifier.fillMaxSize().testTag("wheel").onGloballyPositioned { wheelBounds = it.boundsInRoot() }.semantics {
                        set(RotationAngleKey, rotation); set(SelectedPanelKey, selectedPanelIndex)
                        set(CarouselProgressKey, carouselProgress); set(LauncherModeKey, launcherMode.name)
                        set(PanelOrderKey, panels.joinToString("|") { it.id })
                        set(MagicActiveKey, magic)
                    }.rotationGestures(enabled = !critical && launcherMode == LauncherMode.NORMAL,
                        onStart = { if (launcherMode == LauncherMode.NORMAL) motion?.cancel() else startRotation() },
                        onDrag = { if (launcherMode == LauncherMode.NORMAL) drag = (drag + it).coerceIn(-width, width) else rotation += it * carouselConfig.dragSensitivity },
                        onEnd = { if (launcherMode == LauncherMode.NORMAL) finishNormal(width, it) else finishRotation(it) }
                    )) {
                        if (launcherMode == LauncherMode.ORGANIZE) PanelOrganizer(
                            panels, storedPanels, selectedPanel.id,
                            onSelect = { id -> selectedPanelIndex = panels.indexOfFirst { it.id == id }; showPanelSettings = true },
                            onMove = { id, target ->
                                selectedPanelIndex = panels.indexOfFirst { it.id == id }
                                movePanel(target - selectedPanelIndex)
                            }, onStore = { removePanel(it) }, onRestore = { id, index -> restorePanel(id, index) },
                            onAdd = { type, index -> addPanel(type, index) },
                            onBack = {
                                rotation = targetRotationForPanel(selectedPanelIndex, panels.size)
                                launcherMode = LauncherMode.CAROUSEL
                                persistPanels()
                            },
                            carousel = {
                                ShellPrototypeScreen(
                                    panels = panels,
                                    selectedPanelId = selectedPanel.id,
                                    exitOnTap = false,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                        ) { panel ->
                            LauncherPanel(panel, apps, photos, weather, weatherCity,
                                carouselPreview = true, interactive = false, onLaunch = {})
                        }
                        else if (launcherMode == LauncherMode.CAROUSEL) ShellPrototypeScreen(
                            panels = panels,
                            selectedPanelId = selectedPanel.id,
                            onExit = { id ->
                                val index = panels.indexOfFirst { it.id == id }.coerceAtLeast(0)
                                selectedPanelIndex = index
                                rotation = targetRotationForPanel(index, panels.size)
                                launcherMode = LauncherMode.NORMAL
                                persistPanels()
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) else Carousel3D(panels, { rotation }, selectedPanelIndex, carouselProgress,
                            launcherMode == LauncherMode.CAROUSEL, drag,
                            { index ->
                                if (launcherMode == LauncherMode.ORGANIZE) {
                                    selectedPanelIndex = index
                                    rotation = nearestEquivalentTarget(index, panels.size, rotation)
                                } else selectPanel(index)
                            },
                            { enterOrganize(it) }, carouselConfig, itemKey = { it.id },
                            keepComposed = { panel -> draggedItem != null && panel.widgets.any { it.id == draggedItem?.id } }
                        ) { panel, interactive ->
                            val widgetState = when {
                                launcherMode == LauncherMode.EDIT_ITEMS -> WidgetRenderState.EDIT
                                magic && panel.id == panels[selectedPanelIndex].id -> WidgetRenderState.MAGIC_ANIMATION
                                !interactive -> WidgetRenderState.CAROUSEL_PREVIEW
                                else -> WidgetRenderState.NORMAL_2D
                            }
                            LauncherPanel(
                                panel = panel, apps = apps, photos = photos, weather = weather, weatherCity = weatherCity,
                                onWeatherCityChange = { weatherCity = it },
                                onRequestPhotos = {
                                    photoPermission.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE)
                                },
                                onRefreshWeather = { city -> if (city.isBlank()) locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else loadWeather(city) },
                                carouselPreview = !interactive, renderState = widgetState,
                                interactive = interactive && !critical, onEditItems = { beginEditing() },
                                itemContent = if (panel.type == PanelType.APPS || panel.customized || panel.widgets.isNotEmpty() ||
                                    (launcherMode == LauncherMode.EDIT_ITEMS && panel.id == selectedPanel.id)) { { source ->
                                    if (source.type == PanelType.APPS) SpbAppsList(apps, panels, onLaunch,
                                        onAdd = { app -> itemTargetPanel = homePanelId; addItem(app = app) },
                                        onLocate = { id -> selectPanel(panels.indexOfFirst { it.id == id }.coerceAtLeast(0)) }, interactive)
                                    else ShellWorkspaceGrid(source.widgets, apps, launcherMode == LauncherMode.EDIT_ITEMS,
                                        interactive, draggedItem?.id, ::startItemDrag,
                                        { dragPosition += it }, ::finishItemDrag, { draggedItem = null },
                                        { id, bounds -> itemBounds[id] = bounds },
                                        { item -> if (item.type == PanelType.FOLDER) openFolder = item
                                            else updateItems(source.id) { list -> list.map { if (it.id == item.id) it.copy(presentation = WidgetPresentation.EXPANDED) else it } } }, onLaunch) { item ->
                                        LauncherPanel(panelTemplate(item.type, item.id).copy(title = item.title, hue = source.hue), apps,
                                            photos, weather, weatherCity, { weatherCity = it },
                                            { photoPermission.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE) },
                                            { city -> if (city.isBlank()) locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else loadWeather(city) },
                                            carouselPreview = !interactive,
                                            renderState = if (item.presentation == WidgetPresentation.THREE_D && widgetState == WidgetRenderState.NORMAL_2D)
                                                WidgetRenderState.ACTIVE_3D else widgetState, presentation = item.presentation,
                                            interactive = interactive && launcherMode != LauncherMode.EDIT_ITEMS, onLaunch = onLaunch)
                                    }
                                } } else null, workspacePanel = true
                            ) { if (interactive && !critical) onLaunch(it) }
                        }
                    }
                }
                if (launcherMode == LauncherMode.EDIT_ITEMS) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { selectedPanelIndex = editNeighbor(-1); rotation = targetRotationForPanel(selectedPanelIndex, panels.size) }) { Text("‹ Painel") }
                        TextButton(onClick = { showAddItem = true }) { Text("+ Item") }
                        TextButton(onClick = { launcherMode = LauncherMode.NORMAL; persistPanels() }) { Text("Concluir") }
                        TextButton(onClick = { selectedPanelIndex = editNeighbor(1); rotation = targetRotationForPanel(selectedPanelIndex, panels.size) }) { Text("Painel ›") }
                    }
                    Row(Modifier.fillMaxWidth().height(64.dp).testTag("editing-tray")
                        .onGloballyPositioned { trayBounds = it.boundsInRoot() }.background(Color(0xCC3F4144)), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            if (tray.isEmpty()) Text("Bandeja · arraste itens para cá", Modifier.padding(8.dp), fontSize = 12.sp)
                            tray.forEach { item -> TextButton(onClick = {
                                tray = tray.filterNot { it.id == item.id }
                                updateItems(selectedPanel.id) { it + item }; persistPanels()
                            }) { Text("↑ ${item.title}", maxLines = 1) } }
                        }
                        Box(Modifier.size(60.dp).testTag("item-trash").onGloballyPositioned { trashBounds = it.boundsInRoot() }
                            .background(if (draggedItem != null && trashBounds.contains(dragPosition)) Color(0xFFAD3030) else Color(0xFF353535)), contentAlignment = Alignment.Center) { Text("Lixeira", fontSize = 12.sp) }
                    }
                }
                if (BuildConfig.DEBUG && debug) {
                        Text("rotation: ${rotation.toInt()} · selected: $selectedPanelIndex · fps: $fps")
                        Text("Painéis: ${panels.size} · ${cameraState(carouselProgress)} · Magic: $magic")
                }
                if (launcherMode != LauncherMode.CAROUSEL && launcherMode != LauncherMode.ORGANIZE) Row(Modifier.fillMaxWidth().height(27.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    panels.forEachIndexed { index, panel ->
                        Text(if (panel.id == homePanelId) "⌂" else if (index == selectedPanelIndex) "●" else "○",
                            Modifier.clickable(enabled = !critical && launcherMode != LauncherMode.EDIT_ITEMS) { selectPanel(index) }
                                .semantics { contentDescription = "Ir para ${panel.title}" }.padding(horizontal = 3.dp),
                            color = if (index == selectedPanelIndex) Color.White else Color(0xFFADAFB2), fontSize = if (panel.id == homePanelId) 23.sp else 14.sp)
                    }
                }
                if (launcherMode != LauncherMode.CAROUSEL && launcherMode != LauncherMode.ORGANIZE) Box(Modifier.fillMaxWidth().height(64.dp)) {
                    androidx.compose.foundation.Image(painterResource(br.com.ne3d.spbshellmodern.R.drawable.spb_dock_toolbar), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        for (slot in 0..4) {
                            if (slot == 2) Box(Modifier.weight(1f).fillMaxHeight().testTag("handle")
                                .combinedClickable(enabled = !critical, onLongClick = {
                                    if (launcherMode == LauncherMode.NORMAL) {
                                        openCarousel(); scope.launch { delay(CarouselTuning.enterDuration.toLong()); enterOrganize() }
                                    } else enterOrganize()
                                }, onClick = {
                                    if (launcherMode == LauncherMode.EDIT_ITEMS) { launcherMode = LauncherMode.NORMAL; openCarousel() }
                                    else if (launcherMode == LauncherMode.NORMAL) openCarousel() else selectPanel(selectedPanelIndex)
                                })
                                .rotationGestures(enabled = !critical && launcherMode == LauncherMode.NORMAL, onStart = { startRotation() },
                                    onDrag = { rotation += it * carouselConfig.dragSensitivity }, onEnd = { finishRotation(it) }), contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.Image(painterResource(br.com.ne3d.spbshellmodern.R.drawable.spb_dock_handle), "Carrossel", Modifier.fillMaxWidth().height(62.dp), contentScale = ContentScale.Fit)
                            } else {
                                val dockIndex = if (slot < 2) slot else slot - 1
                                val custom = dock.getOrNull(dockIndex)
                                val target = apps.firstOrNull { it.packageName == custom?.appPackage && it.className == custom?.appClass }
                                val label = target?.label ?: listOf("Telefone", "Mensagens", "Painéis", "Apps")[dockIndex]
                                val organizerButtonVisible = dockIndex != 2 || launcherMode == LauncherMode.CAROUSEL
                                Box(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = label }.combinedClickable(
                                    enabled = target != null || organizerButtonVisible,
                                    onLongClick = {
                                    dockTarget = dockIndex; showAddItem = true
                                }, onClick = {
                                    if (target != null) onLaunch(target)
                                    else when (dockIndex) {
                                        0 -> runCatching { context.startActivity(Intent(Intent.ACTION_DIAL)) }
                                        1 -> runCatching { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)) }
                                        2 -> {
                                            if (launcherMode == LauncherMode.CAROUSEL) enterOrganize()
                                        }
                                        3 -> { launcherMode = LauncherMode.NORMAL; selectPanel(panels.indexOfFirst { it.type == PanelType.APPS }.coerceAtLeast(0)) }
                                    }
                                }), contentAlignment = Alignment.Center) {
                                    val dockApp = target ?: if (dockIndex < 2) apps.firstOrNull { app ->
                                        if (dockIndex == 0) app.label.equals("Telefone", true) || app.packageName.contains("dialer")
                                        else app.label.contains("Mensagen", true) || app.packageName.contains("messaging")
                                    } else null
                                    val icon = remember(dockApp?.packageName, dockApp?.className) { dockApp?.icon?.toBitmap(128, 128)?.asImageBitmap() }
                                    if (icon != null) androidx.compose.foundation.Image(icon, null, Modifier.size(44.dp))
                                    else if (dockIndex == 3 || organizerButtonVisible) androidx.compose.foundation.Image(
                                        painterResource(if (dockIndex == 2) br.com.ne3d.spbshellmodern.R.drawable.spb_dock_panels else br.com.ne3d.spbshellmodern.R.drawable.spb_dock_apps),
                                        null,
                                        Modifier.size(40.dp)
                                    )
                                    else Text(if (dockIndex == 0) "☎" else "✉", fontSize = 37.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
                if (launcherMode == LauncherMode.CAROUSEL) Box(
                    Modifier.fillMaxWidth().height(76.dp).testTag("carousel-editor-button"),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    androidx.compose.foundation.Image(
                        painterResource(br.com.ne3d.spbshellmodern.R.drawable.spb_dock_panels),
                        "Editar painéis",
                        Modifier.padding(end = 26.dp).size(58.dp)
                            .combinedClickable(enabled = !critical, onClick = { enterOrganize() })
                    )
                }
            }
            if (draggedItem != null) Text(draggedItem!!.title, Modifier.offset { IntOffset(dragPosition.x.roundToInt(), dragPosition.y.roundToInt()) }
                .background(Color(0xDD373737)).padding(12.dp), color = if (trashBounds.contains(dragPosition)) Color.Red else Color.White)
            }
        }
    }
    MaterialTheme(colorScheme = shellColors) {
    if (showAddPanel) {
        AlertDialog(
            onDismissRequest = { showAddPanel = false },
            title = { Text("Adicionar painel") },
            text = {
                LazyColumn(Modifier.heightIn(max = 520.dp).testTag("widget-catalog")) {
                    item { Text("Painéis", color = Color(0xFF91DDDA), style = MaterialTheme.typography.titleSmall) }
                    items(2) { index ->
                        val type = listOf(PanelType.FAVORITES, PanelType.AGENDA)[index]
                        TextButton(onClick = { addPanel(type) }, Modifier.fillMaxWidth().testTag("add-${type.name}")) {
                            Text(panelTemplate(type).title)
                        }
                    }
                    item { HorizontalDivider(); Text("Widgets SPB", Modifier.padding(top = 12.dp), color = Color(0xFF91DDDA), style = MaterialTheme.typography.titleSmall) }
                    items(spbWidgetTypes.size) { index ->
                        val type = spbWidgetTypes[index]
                        TextButton(onClick = { addPanel(type) }, Modifier.fillMaxWidth().testTag("add-${type.name}")) {
                            Text(panelTemplate(type).title)
                        }
                    }
                    item {
                        HorizontalDivider()
                        Text("Widgets Android", Modifier.padding(top = 12.dp), color = Color(0xFF91DDDA), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { addPanel(PanelType.ANDROID_WIDGET) }, Modifier.testTag("add-ANDROID_WIDGET")) { Text("Escolher widget Android") }
                    }
                    if (storedPanels.isNotEmpty()) {
                        item { Text("Painéis guardados", Modifier.padding(top = 12.dp)) }
                        items(storedPanels, key = { "stored-${it.id}" }) { stored ->
                            TextButton(enabled = panels.size < MAX_PANELS, onClick = {
                                restorePanel(stored.id)
                            }) { Text("Restaurar · ${stored.title}") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddPanel = false }) { Text("Cancelar") } }
        )
    }
    if (showPanelSettings) {
        var name by remember(selectedPanel.id) { mutableStateOf(selectedPanel.title) }
        AlertDialog(onDismissRequest = { showPanelSettings = false }, title = { Text("Configurar painel") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it.take(48) }, label = { Text("Nome do painel") }, singleLine = true)
                Text("Cor do painel")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0f, 45f, 120f, 180f, 240f, 300f).forEach { hue ->
                        Box(Modifier.size(30.dp).background(Color.hsv(hue, .4f, .65f)).clickable {
                            panels = panels.map { if (it.id == selectedPanel.id) it.copy(hue = hue) else it }; persistPanels()
                        })
                    }
                }
                TextButton(onClick = { homePanelId = selectedPanel.id; persistPanels() }) {
                    Text(if (homePanelId == selectedPanel.id) "★ Painel Home" else "Usar este painel como Home")
                }
                TextButton(onClick = {
                    showPanelSettings = false
                    if (launcherMode == LauncherMode.EDIT_ITEMS) launcherMode = LauncherMode.NORMAL
                    if (launcherMode == LauncherMode.NORMAL) openCarousel()
                    scope.launch { delay(CarouselTuning.enterDuration.toLong()); enterOrganize() }
                }) { Text("Gerenciar painéis") }
                TextButton(onClick = { requestHomeSelection(context) { homeRequest.launch(it) } }) { Text("Definir launcher padrão") }
                if (BuildConfig.DEBUG) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(debug, { debug = it }); Text("Mostrar diagnóstico")
                }
            }
        }, confirmButton = { TextButton(onClick = {
            if (name.isNotBlank()) panels = panels.map { if (it.id == selectedPanel.id) it.copy(title = name.trim()) else it }
            persistPanels(); showPanelSettings = false
        }) { Text("Salvar") } })
    }
    if (showAddItem) {
        var query by remember { mutableStateOf("") }
        var appTab by remember { mutableStateOf(dockTarget != null || openFolder != null) }
        AlertDialog(onDismissRequest = { showAddItem = false; dockTarget = null }, title = { Text(if (dockTarget != null) "Atalho do dock" else "Adicionar item") },
            text = {
                Column(Modifier.heightIn(max = 560.dp)) {
                    if (dockTarget == null && openFolder == null) Row {
                        TextButton(onClick = { appTab = false }) { Text("Widgets SPB") }
                        TextButton(onClick = { appTab = true }) { Text("Aplicativos") }
                    }
                    OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true)
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        if (appTab) items(apps.filter { it.label.contains(query, true) }, key = { it.packageName + it.className }) { app ->
                            TextButton(onClick = { addItem(app = app) }, Modifier.fillMaxWidth()) { Text(app.label) }
                        } else {
                            items((spbWidgetTypes + PanelType.ANDROID_WIDGET).filter { panelTemplate(it).title.contains(query, true) }) { type ->
                                TextButton(onClick = { addItem(type) }, Modifier.fillMaxWidth().testTag("item-add-${type.name}")) { Text(panelTemplate(type).title) }
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showAddItem = false; dockTarget = null }) { Text("Fechar") } })
    }
    editingItem?.let { item ->
        var title by remember(item.id) { mutableStateOf(item.title) }
        fun replace(updated: ShellItem) {
            panels = panels.map { p -> p.copy(widgets = p.widgets.map { if (it.id == updated.id) updated else it }) }
            tray = tray.map { if (it.id == updated.id) updated else it }; editingItem = updated; persistPanels()
        }
        AlertDialog(onDismissRequest = { editingItem = null }, title = { Text("Editar item") }, text = {
            Column {
                OutlinedTextField(title, { title = it.take(48) }, label = { Text("Nome") }, singleLine = true)
                if (item.appPackage == null) {
                    Text("Apresentação")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        WidgetPresentation.entries.forEach { view -> TextButton(onClick = { replace(item.copy(presentation = view)) }) {
                            Text(when (view) {
                                WidgetPresentation.ICON -> "Ícone"
                                WidgetPresentation.COMPACT -> "Compacto"
                                WidgetPresentation.ROW -> "Linha"
                                WidgetPresentation.EXPANDED -> "Expandido"
                                WidgetPresentation.FULL_PANEL -> "Painel"
                                WidgetPresentation.THREE_D -> "3D"
                            })
                        } }
                    }
                }
                panels[selectedPanelIndex].widgets.filter { item.type != PanelType.FOLDER && it.type == PanelType.FOLDER && it.id != item.id }.forEach { folder ->
                    TextButton(onClick = {
                        removeItemEverywhere(item.id)
                        updateItems(selectedPanel.id) { list -> list.map { if (it.id == folder.id) it.copy(children = it.children + item) else it } }
                        editingItem = null
                    }) { Text("Mover para ${folder.title}") }
                }
                TextButton(onClick = { removeItemEverywhere(item.id); tray += item; editingItem = null; persistPanels() }) { Text("Guardar na bandeja") }
                TextButton(onClick = {
                    removeItemEverywhere(item.id)
                    releaseAndroidWidgets(item)
                    editingItem = null; persistPanels()
                }) { Text("Remover item") }
            }
        }, confirmButton = { TextButton(onClick = { replace(item.copy(title = title.trim())); editingItem = null }) { Text("Salvar") } })
    }
    openFolder?.takeIf { !showAddItem && expandedFolderWidget == null }?.let { folder ->
        AlertDialog(onDismissRequest = { openFolder = null }, title = { Text(folder.title.ifBlank { "Pasta" }) }, text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(folder.children, key = { it.id }) { child ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(modifier = Modifier.weight(1f), onClick = {
                            val app = apps.firstOrNull { it.packageName == child.appPackage && it.className == child.appClass }
                            if (app != null) onLaunch(app)
                            else if (child.type == PanelType.FOLDER) openFolder = child
                            else if (child.appPackage == null) expandedFolderWidget = child
                        }) { Text(child.title) }
                        TextButton(onClick = {
                            removeItemEverywhere(child.id); tray += child
                            openFolder = folder.copy(children = folder.children.filterNot { it.id == child.id }); persistPanels()
                        }) { Text("↑ Bandeja") }
                    }
                }
                item { TextButton(onClick = { showAddItem = true }) { Text("+ Aplicativo") } }
            }
        }, confirmButton = { TextButton(onClick = { openFolder = null }) { Text("Fechar") } })
    }
    expandedFolderWidget?.let { item ->
        AlertDialog(onDismissRequest = { expandedFolderWidget = null }, title = { Text(item.title) }, text = {
            Box(Modifier.fillMaxWidth().height(420.dp)) {
                LauncherPanel(panelTemplate(item.type, item.id).copy(title = item.title), apps,
                    photos, weather, weatherCity, { weatherCity = it },
                    { photoPermission.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE) },
                    { city -> if (city.isBlank()) locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else loadWeather(city) },
                    onLaunch = onLaunch)
            }
        }, confirmButton = { TextButton(onClick = { expandedFolderWidget = null }) { Text("Fechar") } })
    }
    }
}

private fun requestHomeSelection(context: Context, launch: (Intent) -> Unit) {
    val roles = context.getSystemService(RoleManager::class.java) ?: return
    if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
        launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
    }
}
