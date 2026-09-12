package br.com.ne3d.spbshellmodern.ui

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.SemanticsPropertyKey
import br.com.ne3d.spbshellmodern.engine.*
import kotlin.math.*

val RotationAngleKey = SemanticsPropertyKey<Float>("Carousel angle")
val SelectedPanelKey = SemanticsPropertyKey<Int>("Selected panel")
val CarouselProgressKey = SemanticsPropertyKey<Float>("Carousel progress")
val LauncherModeKey = SemanticsPropertyKey<String>("Launcher mode")
val PanelOrderKey = SemanticsPropertyKey<String>("Panel order")
val MagicActiveKey = SemanticsPropertyKey<Boolean>("Magic active")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> Carousel3D(
    items: List<T>, rotation: () -> Float, selectedIndex: Int,
    progress: Float, wheelMode: Boolean, normalDrag: Float, onPanelClick: (Int) -> Unit,
    onPanelLongClick: (Int) -> Unit,
    config: CarouselConfig = DefaultCarouselConfig,
    itemKey: (T) -> Any = { it as Any },
    keepComposed: (T) -> Boolean = { false },
    content: @Composable (T, Boolean) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .86f * progress)),
                startY = height * .35f, endY = height))
            drawOval(Brush.radialGradient(listOf(Color(0xFFB0B2AA).copy(alpha = .22f * progress), Color.Transparent),
                center = Offset(width * .5f, height * .79f), radius = width * .65f),
                Offset(-width * .15f, height * .61f), Size(width * 1.3f, height * .38f))
        }
        val panelWidth = maxWidth
        val panelHeight = maxHeight
        val currentRotation by rememberUpdatedState(rotation)
        val drawOrder by remember(items.size) { derivedStateOf {
            items.indices.sortedBy { cos(Math.toRadians((it * stepAngle(items.size) + currentRotation()).toDouble())) }
        } }
        items.forEachIndexed { index, item ->
            val relative = index - selectedIndex
            if (progress > 0f || abs(relative) <= 1 || keepComposed(item)) key(itemKey(item)) {
                val panelLayer = rememberGraphicsLayer()
                val reflectionPaint = remember { Paint() }
                val captureEnabled = rememberUpdatedState(CarouselTuning.liveReflections && progress > 0f)
                val contentCapture = remember(panelLayer) {
                    // This layer caches the widget drawing independently from the changing
                    // angle/light outside it. Its draw callback only observes capture mode
                    // and the live child content, so rotation does not re-record the card.
                    Modifier.graphicsLayer {
                        // Cache live widget rasterization on the GPU while the plane moves.
                        // Content invalidations still refresh this layer (clock/photos/etc.).
                        compositingStrategy = if (captureEnabled.value) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                    }.drawWithContent {
                        if (captureEnabled.value) {
                            panelLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(panelLayer)
                        } else drawContent()
                    }
                }
                Box(Modifier.size(panelWidth, panelHeight).zIndex(if (progress > 0f) drawOrder.indexOf(index).toFloat() else -abs(relative).toFloat())
                    .graphicsLayer {
                        val angle = normalizeAngle(index * stepAngle(items.size) + rotation())
                        val rad = Math.toRadians(angle.toDouble())
                        val x = sin(rad).toFloat()
                        val depth = cos(rad).toFloat()
                        val fraction = relative + normalDrag / width
                        val normalScale = 1f - abs(fraction).coerceAtMost(1f) * .06f
                        // Faces sit on the sides of one regular polygon.  Using the apothem
                        // keeps a small, stable gap instead of opening or overlapping faces.
                        val wheelScale = .52f
                        // Every face has exactly the same width and sits on a regular
                        // polygon.  Its apothem makes adjacent vertices touch; no depth-scale
                        // or per-card Y offset is allowed here because either one opens gaps.
                        val apothem = (width * wheelScale) /
                            (2f * tan(Math.PI / items.size).toFloat())
                        // A page swipe is a flat strip.  Once the wheel is requested, every
                        // card starts behind the selected face and grows into the polygon.  Do
                        // not blend from the flat strip here: that is what made the cards appear
                        // to open away from the carousel before they joined it.
                        translationX = if (wheelMode) x * apothem * progress else fraction * width
                        val depthAmount = (depth + 1f) * .5f
                        translationY = 0f
                        scaleX = if (wheelMode) 1f - (1f - wheelScale) * progress else normalScale
                        scaleY = scaleX
                        rotationY = if (wheelMode) -angle * progress else -fraction * 14f
                        val normalAlpha = if (abs(relative) <= 1) 1f else 0f
                        alpha = if (wheelMode) {
                            if (relative == 0) 1f else (.42f + depthAmount * .52f) * progress
                        } else normalAlpha
                        // Keep the full polygon outside the near plane.  The faces are joined
                        // by their vertices; perspective must not pull rear cards over front.
                        cameraDistance = width * (2f + progress)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }.drawWithContent {
                        drawContent()
                        if (progress > .01f) {
                            val rad = Math.toRadians(normalizeAngle(index * stepAngle(items.size) + rotation()).toDouble())
                            val x = sin(rad).toFloat()
                            val depth = cos(rad).toFloat()
                            // Angle-dependent light is independent from rear-panel opacity.
                            val shade = (1f - (depth + 1f) / 2f) * .22f * progress
                            drawRect(Color.Black.copy(alpha = shade))
                            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = abs(x) * .22f * progress),
                                Color.Transparent, Color.White.copy(alpha = .035f * progress))))
                            if (CarouselTuning.liveReflections && depth > .3f) {
                                reflectionPaint.alpha = .18f * progress * depth
                                drawContext.canvas.saveLayer(Rect(0f, size.height, size.width, size.height * 1.18f), reflectionPaint)
                                withTransform({
                                    translate(0f, size.height * 1.18f)
                                    scale(1f, -.18f, Offset.Zero)
                                }) { drawLayer(panelLayer) }
                                drawContext.canvas.restore()
                            }
                        }
                    }) {
                    Box(Modifier.fillMaxSize().then(contentCapture)) {
                        content(item, progress == 0f && relative == 0)
                    }
                    if (progress > 0f) Box(Modifier.fillMaxSize().testTag("panel-$index").combinedClickable(
                        onClick = { onPanelClick(index) }, onLongClick = { onPanelLongClick(index) }
                    ))
                }
            }
        }
    }
}

// Shared by the wheel, NORMAL swipe and the handle; velocity includes the final up event.
fun Modifier.rotationGestures(
    enabled: Boolean = true, onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: (Float) -> Unit
): Modifier = composed {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    pointerInput(enabled) {
        if (enabled) {
            val tracker = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                tracker.resetTracking()
                tracker.addPosition(down.uptimeMillis, down.position)
                val crossed = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    change.consume(); start()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    drag(over)
                }
                if (crossed != null) {
                    val completed = horizontalDrag(crossed.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        drag(change.position.x - change.previousPosition.x)
                        change.consume()
                    }
                    currentEvent.changes.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                    end(if (completed) tracker.calculateVelocity().x else 0f)
                }
            }
        }
    }
}
