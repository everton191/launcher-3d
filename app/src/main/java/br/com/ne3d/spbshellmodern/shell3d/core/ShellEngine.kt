package br.com.ne3d.spbshellmodern.shell3d.core

import br.com.ne3d.spbshellmodern.shell3d.carousel.*
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D
import br.com.ne3d.spbshellmodern.shell3d.animation.CarouselEntryTransition
import br.com.ne3d.spbshellmodern.shell3d.animation.CarouselExitTransition
import br.com.ne3d.spbshellmodern.shell3d.animation.CarouselIdleController
import br.com.ne3d.spbshellmodern.shell3d.effects.EffectContext
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffectDebug
import br.com.ne3d.spbshellmodern.shell3d.effects.configureEffect
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelTransitionController
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.PI
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class ShellEngine(
    val spec: CarouselMotionSpec = CarouselMotionSpec(),
    includeRealSnapshots: Boolean = true,
    panels: List<Panel3D>? = null,
    initialSelectedIndex: Int = 0,
) {
    val state = SceneState(); val carousel = CarouselPhysics(spec)
    val entry = CarouselEntryTransition(spec)
    val exit = CarouselExitTransition(spec)
    val idle = CarouselIdleController()
    val transition = PanelTransitionController()
    var selectedIndex = 0
        private set
    var cameraY = spec.cameraY
        private set
    private var autoRotationRequested = false
    private var autoWakePending = false
    private var pendingOpenPanelIndex: Int? = null
    private var texturesReady = false
    private val commands = ConcurrentLinkedQueue<Command>()
    private val effectContext: EffectContext
    private val pendingDxBits = AtomicInteger(0)
    private val pendingDyBits = AtomicInteger(0)
    init {
        val snapshotKind = if (includeRealSnapshots) br.com.ne3d.spbshellmodern.shell3d.scene.PanelTextureKind.REAL_SNAPSHOT else br.com.ne3d.spbshellmodern.shell3d.scene.PanelTextureKind.STATIC
        state.panels += panels ?: listOf(
            Panel3D("home", "Home", 0xFF285B8C.toInt(), snapshotKind),
            Panel3D("agenda", "Agenda", 0xFF315F51.toInt(), snapshotKind),
            Panel3D("photos", "Fotos", 0xFF653A68.toInt(), snapshotKind),
            Panel3D("apps", "Apps", 0xFF405E86.toInt(), snapshotKind),
            Panel3D("weather", "Clima", 0xFF456E78.toInt(), snapshotKind),
            Panel3D("clock", "Relógio", 0xFF4A5F89.toInt(), snapshotKind),
            Panel3D("moon", "Lua", 0xFF48546B.toInt(), snapshotKind),
            Panel3D("gallery", "Galeria", 0xFF705747.toInt(), snapshotKind),
        )
        if (state.panels.isNotEmpty()) carousel.setAngle(-initialSelectedIndex.coerceIn(state.panels.indices) * 360f / state.panels.size)
        effectContext = EffectContext(panelCount = state.panels.size, motionSpec = spec)
        var index = 0
        while (index < state.panels.size) {
            state.panels[index].effectStack.prepare(effectContext)
            state.panels[index].deformerStack.prepare(effectContext)
            state.panels[index].configureEffect(PanelEffectDebug.mode)
            index++
        }
    }
    /** Main/UI thread entry points only enqueue; the GL thread owns all physics mutation. */
    fun onDrag(dx: Float) = accumulate(pendingDxBits, dx)
    fun onGestureStart() = commands.add(Command.GestureStart)
    fun onGestureEnd() = commands.add(Command.GestureEnd)
    fun onVerticalDrag(dy: Float) = accumulate(pendingDyBits, dy)
    fun onFling(velocityX: Float) = commands.add(Command.Fling(velocityX))
    fun openPanelAt(tapX: Float, surfaceWidth: Float) = commands.add(Command.OpenAt(tapX, surfaceWidth))
    fun beginAutoRotation() = commands.add(Command.AutoRotate)
    fun beginPanelTransition(panel: Int, mode: br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffectMode) = commands.add(Command.TransitionOpen(panel, mode))
    /** Called by ShellRenderer on its owning GL thread after TextureManager.update completes. */
    fun onTextureUploaded() { texturesReady = true; transition.onTextureReady() }
    fun blocksInput(): Boolean = transition.blocksInput

    private fun applyVerticalDrag(dy: Float) {
        if (abs(dy) < .01f) return
        cameraY = (cameraY + dy * spec.verticalDragToCameraY).coerceIn(spec.minimumCameraY, spec.maximumCameraY)
    }
    private fun applyExit(tapX: Float, surfaceWidth: Float) {
        if (entry.active || exit.active) return
        val step = 360f / state.panels.size
        val centeredIndex = ((-carousel.angle / step).roundToInt() % state.panels.size + state.panels.size) % state.panels.size
        // A short tap chooses the face under the left, centre, or right third of the ring.
        // A held touch never arrives here; it remains a carousel gesture.
        val offset = when {
            tapX < surfaceWidth * .34f -> -1
            tapX > surfaceWidth * .66f -> 1
            else -> 0
        }
        selectedIndex = (centeredIndex + offset).mod(state.panels.size)
        exit.begin()
    }
    private fun openAt(tapX: Float, surfaceWidth: Float) {
        if (state.panels.isEmpty() || pendingOpenPanelIndex != null || transition.blocksInput) return
        val targetX = (tapX / surfaceWidth.coerceAtLeast(1f) - .5f) * 2f
        var bestIndex = 0; var bestDistance = Float.MAX_VALUE
        val step = 360f / state.panels.size
        state.panels.indices.forEach { index ->
            val angle = index * step + carousel.angle
            val projectedX = sin(angle * PI / 180.0).toFloat()
            val distance = abs(projectedX - targetX)
            if (distance < bestDistance) { bestDistance = distance; bestIndex = index }
        }
        pendingOpenPanelIndex = bestIndex
        carousel.snapToIndex(bestIndex, state.panels.size)
    }
    private fun startPendingOpen() {
        val index = pendingOpenPanelIndex ?: return
        pendingOpenPanelIndex = null
        selectedIndex = index
        state.panels[index].configureEffect(PanelEffectDebug.mode)
        transition.requestOpen(index, PanelEffectDebug.mode)
        if (texturesReady) transition.onTextureReady()
    }
    fun tick(dt: Float): Boolean {
        drainCommands()
        transition.tick(dt)
        if (transition.blocksInput) return true
        val entering = entry.tick(dt)
        if (entering) return true
        if (exit.active) return exit.tick(dt)
        if (carousel.dragging) return true
        val settling = carousel.tick(dt, state.panels.size)
        if (settling) return true
        if (pendingOpenPanelIndex != null) { startPendingOpen(); return true }
        if (autoRotationRequested) {
            carousel.autoRotate(dt * AUTO_ROTATE_DEGREES_PER_SECOND)
            return true
        }
        idle.onIdle()
        autoWakePending = true
        return false
    }
    fun consumeExitCompleted() = exit.consumeCompleted()
    fun consumeComposeReady() = transition.consumeComposeReady()
    fun consumeAutoWakePending(): Boolean = autoWakePending.also { autoWakePending = false }
    /** GL-owner terminal cleanup; safe to call more than once. */
    fun releaseEffects() { transition.release(); var index = 0; while (index < state.panels.size) { state.panels[index].effectStack.release(); state.panels[index].deformerStack.release(); index++ } }

    private fun drainCommands() {
        while (true) {
            val command = commands.poll() ?: break
            when (command) {
            Command.GestureStart -> { autoRotationRequested = false; autoWakePending = false; carousel.beginDrag(); idle.onInteraction() }
            else -> consumePendingMoves()
            }
            when (command) {
            Command.GestureStart -> Unit
            Command.GestureEnd -> { carousel.endDrag(); idle.onSettling() }
            is Command.Fling -> { autoRotationRequested = false; autoWakePending = false; idle.onSettling(); carousel.fling(command.velocityX) }
            is Command.Exit -> applyExit(command.tapX, command.width)
            is Command.OpenAt -> openAt(command.tapX, command.width)
            is Command.TransitionOpen -> transition.requestOpen(command.panel, command.mode)
            Command.AutoRotate -> { if (!carousel.dragging && !exit.active) autoRotationRequested = true }
            }
        }
        consumePendingMoves()
    }

    private fun consumePendingMoves() {
        val dx = Float.fromBits(pendingDxBits.getAndSet(0))
        val dy = Float.fromBits(pendingDyBits.getAndSet(0))
        if (dx != 0f) { autoRotationRequested = false; autoWakePending = false; idle.onInteraction(); carousel.dragBy(dx) }
        if (dy != 0f) { autoRotationRequested = false; autoWakePending = false; idle.onInteraction(); applyVerticalDrag(dy) }
    }
    private fun accumulate(target: AtomicInteger, delta: Float) {
        while (true) {
            val before = target.get()
            val after = Float.fromBits(before) + delta
            if (target.compareAndSet(before, after.toBits())) return
        }
    }

    private sealed interface Command {
        data object GestureStart : Command
        data object GestureEnd : Command
        data object AutoRotate : Command
        data class Fling(val velocityX: Float) : Command
        data class Exit(val tapX: Float, val width: Float) : Command
        data class OpenAt(val tapX: Float, val width: Float) : Command
        data class TransitionOpen(val panel: Int, val mode: br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffectMode) : Command
    }
    companion object { const val AUTO_ROTATE_DELAY_MILLIS = 5_000L; private const val AUTO_ROTATE_DEGREES_PER_SECOND = 18f }
}
