package br.com.ne3d.spbshellmodern.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.ne3d.spbshellmodern.model.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Active panels above, preserved panels on the floor; changes commit only on drop. */
@Composable
fun PanelOrganizer(
    panels: List<LauncherPanel>, stored: List<LauncherPanel>, selectedId: String,
    onSelect: (String) -> Unit, onMove: (String, Int) -> Unit,
    onStore: (String) -> Unit, onRestore: (String, Int) -> Unit,
    onAdd: (PanelType, Int) -> Unit,
    onBack: () -> Unit,
    carousel: @Composable () -> Unit,
    preview: @Composable (LauncherPanel) -> Unit
) {
    val activeScroll = rememberLazyListState()
    val floorScroll = rememberLazyListState()
    var activeBounds by remember { mutableStateOf(Rect.Zero) }
    var floorBounds by remember { mutableStateOf(Rect.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<LauncherPanel?>(null) }
    var position by remember { mutableStateOf(Offset.Zero) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edge = with(density) { 36.dp.toPx() }
    // The floor contains only panels the user has removed. It never manufactures a
    // second copy of a widget template.
    val floorPanels = stored
    LaunchedEffect(dragging?.id) {
        while (dragging != null) {
            val bounds = if (floorBounds.contains(position)) floorBounds else activeBounds
            val scroll = if (floorBounds.contains(position)) floorScroll else activeScroll
            val delta = when {
                position.x < bounds.left + edge -> -edge * .22f
                position.x > bounds.right - edge -> edge * .22f
                else -> 0f
            }
            if (delta != 0f) scroll.scrollBy(delta)
            delay(16)
        }
    }
    fun drop() {
        val panel = dragging ?: return
        if (floorBounds.contains(position) && panels.any { it.id == panel.id }) {
            onStore(panel.id)
        } else if (activeBounds.contains(position)) {
            val visible = panels.mapIndexedNotNull { index, item ->
                cardBounds[item.id]?.takeIf { it.overlaps(activeBounds) }?.let { index to it }
            }
            val insertion = visible.firstOrNull { position.x < it.second.center.x }?.first
                ?: ((visible.lastOrNull()?.first ?: panels.lastIndex) + 1)
            val from = panels.indexOfFirst { it.id == panel.id }
            when {
                from >= 0 -> onMove(panel.id, (if (insertion > from) insertion - 1 else insertion).coerceIn(panels.indices))
                stored.any { it.id == panel.id } -> onRestore(panel.id, insertion)
                // A panel not present in active or stored is not a valid drag source.
                else -> Unit
            }
        }
        dragging = null
    }
    val latestDrop by rememberUpdatedState({ drop() })
    @Composable fun card(panel: LauncherPanel, onFloor: Boolean) {
        DisposableEffect(panel.id) { onDispose { cardBounds.remove(panel.id) } }
        Column(Modifier.width(104.dp).testTag("organize-${panel.id}")
            .onGloballyPositioned { cardBounds[panel.id] = it.boundsInRoot() }
            .graphicsLayer { alpha = if (dragging?.id == panel.id) .25f else 1f }
            .pointerInput(panel.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragging = panel; position = cardBounds[panel.id]!!.topLeft + local
                    },
                    onDrag = { change, delta -> change.consume(); position += delta },
                    onDragEnd = { latestDrop() }, onDragCancel = { dragging = null }
                )
            }.then(if (onFloor) Modifier else Modifier.clickable { onSelect(panel.id) }),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(100.dp, 148.dp).clipToBounds()
                .border(if (panel.id == selectedId) 2.dp else 1.dp,
                    if (panel.id == selectedId) Color(0xFFFFD36D) else Color(0xFF859E9E))) {
                Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(300.dp, 444.dp).graphicsLayer {
                        scaleX = 1f / 3; scaleY = 1f / 3; transformOrigin = TransformOrigin(0f, 0f)
                    }) { preview(panel) }
            }
            Text(panel.title, fontSize = 12.sp, maxLines = 1)
            if (!panel.removable && !onFloor) Text("Fixo", fontSize = 10.sp, color = Color.LightGray)
        }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            Box(Modifier.fillMaxWidth().weight(1.12f).testTag("active-panels")
                .onGloballyPositioned { activeBounds = it.boundsInRoot() }
                .background(if (dragging != null && activeBounds.contains(position)) Color(0x334B827A) else Color.Transparent)
                ) {
                    carousel()
                    // This overlay owns all organizer gestures, so the GLSurfaceView below
                    // cannot interpret a long press as a carousel tap.
                    Box(Modifier.align(Alignment.Center).fillMaxWidth(.45f).fillMaxHeight(.65f).pointerInput(selectedId) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { local ->
                            panels.firstOrNull { it.id == selectedId }?.let { panel ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragging = panel
                                position = activeBounds.topLeft + local
                            }
                        },
                        onDrag = { change, delta -> change.consume(); position += delta },
                        onDragEnd = { latestDrop() }, onDragCancel = { dragging = null }
                    )
                })
                }
            Text(if (dragging != null && floorBounds.contains(position)) "Piso · solte para remover" else "Piso · arraste para o carrossel", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp)
            LazyRow(state = floorScroll, modifier = Modifier.fillMaxWidth().weight(.88f)
                .testTag("panel-floor").onGloballyPositioned { floorBounds = it.boundsInRoot() }
                .background(if (dragging != null && floorBounds.contains(position)) Color(0x8870653B) else Color(0xAA252929)),
                contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(floorPanels, key = { it.id }) { card(it, true) }
            }
        }
        dragging?.let { panel ->
            Box(Modifier.offset { IntOffset((position.x - origin.x - 50.dp.toPx(density)).roundToInt(),
                (position.y - origin.y - 74.dp.toPx(density)).roundToInt()) }
                .size(100.dp, 148.dp).testTag("dragging-panel").background(Color(0xEE34585B)).border(2.dp, Color(0xFFFFD36D)),
                contentAlignment = Alignment.Center) { Text(panel.title, Modifier.padding(8.dp)) }
        }
        Text("← Voltar", Modifier.align(Alignment.BottomStart).padding(14.dp)
            .background(Color(0xCC252929)).border(1.dp, Color(0xFF859E9E))
            .clickable { onBack() }.padding(horizontal = 18.dp, vertical = 12.dp), fontSize = 14.sp)
    }
}

private fun androidx.compose.ui.unit.Dp.toPx(density: androidx.compose.ui.unit.Density) = with(density) { toPx() }
