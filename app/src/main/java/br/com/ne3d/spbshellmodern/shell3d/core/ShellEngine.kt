package br.com.ne3d.spbshellmodern.shell3d.core

import br.com.ne3d.spbshellmodern.shell3d.carousel.*
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D
import br.com.ne3d.spbshellmodern.shell3d.animation.CarouselEntryTransition
import br.com.ne3d.spbshellmodern.shell3d.animation.CarouselExitTransition
import br.com.ne3d.spbshellmodern.shell3d.animation.CarouselIdleController
import br.com.ne3d.spbshellmodern.shell3d.animation.PanelPresentationState
import br.com.ne3d.spbshellmodern.shell3d.presentation.PresentationItem
import br.com.ne3d.spbshellmodern.engine.nearestPanel
import br.com.ne3d.spbshellmodern.shell3d.effects.EffectContext
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffectDebug
import br.com.ne3d.spbshellmodern.shell3d.effects.configureEffect
import android.util.Log
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
    val idle = CarouselIdleController(spec)
    var selectedIndex = 0
        private set
    /** Unbounded logical position; the visible slot is its circular projection. */
    var logicalIndex: Long = 0L
        private set
    fun physicalIndex(): Int = CarouselCircularIndex.physicalIndex(logicalIndex, state.panels.size)
    val presentationEmphasis: Float get() = idle.presentationEmphasis
    val presentationState: PanelPresentationState get() = idle.presentation.state
    /** Live overlay items of the presented front panel. Empty unless presenting. */
    val livePresentationItems: List<PresentationItem>
        get() = idle.liveItems
    val autoplayPhase get() = idle.autoplayPhase
    fun setAutoplayEnabled(enabled: Boolean) = idle.setAutoplayEnabled(enabled)
    var cameraY = spec.cameraY
        private set
    private var autoWakePending = false
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
        if (state.panels.isNotEmpty()) {
            selectedIndex = initialSelectedIndex.coerceIn(state.panels.indices)
            logicalIndex = selectedIndex.toLong()
            idle.syncLogical(logicalIndex)
            carousel.setAngle(-selectedIndex * 360f / state.panels.size)
        }
        effectContext = EffectContext(panelCount = state.panels.size, motionSpec = spec)
        var index = 0
        while (index < state.panels.size) {
            state.panels[index].effectStack.prepare(effectContext)
            state.panels[index].deformerStack.prepare(effectContext)
            state.panels[index].configureEffect(br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffectMode.NONE)
            index++
        }
    }
    /** Main/UI thread entry points only enqueue; the GL thread owns all physics mutation. */
    fun onDrag(dx: Float) = accumulate(pendingDxBits, dx)
    fun onGestureStart() = commands.add(Command.GestureStart)
    fun onGestureEnd() = commands.add(Command.GestureEnd)
    fun onVerticalDrag(dy: Float) = accumulate(pendingDyBits, dy)
    fun onFling(velocityX: Float) = commands.add(Command.Fling(velocityX))
    fun beginExitForPanel(index: Int) = commands.add(Command.ExitPanel(index))
    fun panelIndexAt(tapX: Float, surfaceWidth: Float): Int {
        if (state.panels.isEmpty()) return 0
        val targetX = (tapX / surfaceWidth.coerceAtLeast(1f) - .5f) * 2f
        var bestIndex = 0; var bestDistance = Float.MAX_VALUE
        val step = 360f / state.panels.size
        state.panels.indices.forEach { index ->
            val projectedX = sin((index * step + carousel.angle) * PI / 180.0).toFloat()
            val distance = abs(projectedX - targetX)
            if (distance < bestDistance) { bestDistance = distance; bestIndex = index }
        }
        return bestIndex
    }
    fun beginAutoRotation() = commands.add(Command.AutoRotate)
    fun blocksInput(): Boolean = exit.active

    private fun applyVerticalDrag(dy: Float) {
        if (abs(dy) < .01f) return
        cameraY = (cameraY + dy * spec.verticalDragToCameraY).coerceIn(spec.minimumCameraY, spec.maximumCameraY)
    }
    private fun applyExitForPanel(index: Int) {
        if (entry.active || exit.active || state.panels.isEmpty()) return
        selectedIndex = index.coerceIn(state.panels.indices)
        logicalIndex = selectedIndex.toLong()
        idle.syncLogical(logicalIndex)
        idle.cancelForOpen()
        state.panels[selectedIndex].configureEffect(PanelEffectDebug.mode)
        Log.i("Shell3D.Exit", "target=${state.panels[selectedIndex].id} index=$selectedIndex effect=${PanelEffectDebug.mode}")
        exit.begin()
    }
    fun tick(dt: Float): Boolean {
        drainCommands()
        val entering = entry.tick(dt)
        if (entering) return true
        if (exit.active) return exit.tick(dt)
        if (carousel.dragging) return true
        val settling = carousel.tick(dt, state.panels.size)
        if (settling) return true
        // Circular presentation autoplay: snap exactly one panel, stop, present, return, pause.
        if (idle.tickAutoplay(dt, carousel, state.panels.size,
                { state.panels.getOrNull(it)?.id },
                { logical, physical -> selectedIndex = physical; logicalIndex = logical })) return true
        // A manually settled ring re-syncs the visible selection; never during an autoplay advance.
        if (!entry.active && !exit.active && !idle.isAdvancing && state.panels.isNotEmpty()) {
            val front = nearestPanel(carousel.angle, state.panels.size)
            if (front != selectedIndex) {
                selectedIndex = front
                logicalIndex = front.toLong()
                idle.syncLogical(logicalIndex)
            }
        }
        idle.onIdle()
        autoWakePending = true
        return false
    }
    fun consumeExitCompleted() = exit.consumeCompleted()
    fun consumeAutoWakePending(): Boolean = autoWakePending.also { autoWakePending = false }
    /** GL-owner terminal cleanup; safe to call more than once. */
    fun releaseEffects() { var index = 0; while (index < state.panels.size) { state.panels[index].effectStack.release(); state.panels[index].deformerStack.release(); index++ } }

    private fun drainCommands() {
        while (true) {
            val command = commands.poll() ?: break
            when (command) {
            Command.GestureStart -> { autoWakePending = false; carousel.beginDrag(); idle.onInteraction() }
            else -> consumePendingMoves()
            }
            when (command) {
            Command.GestureStart -> Unit
            Command.GestureEnd -> { carousel.endDrag(); idle.onSettling() }
            is Command.Fling -> { autoWakePending = false; idle.onSettling(); carousel.fling(command.velocityX) }
            is Command.ExitPanel -> applyExitForPanel(command.index)
            // Scheduler idle wake: the autoplay machine re-evaluates its deadlines in tick().
            Command.AutoRotate -> { idle.onAutoplayWakeup() }
            }
        }
        consumePendingMoves()
    }

    private fun consumePendingMoves() {
        val dx = Float.fromBits(pendingDxBits.getAndSet(0))
        val dy = Float.fromBits(pendingDyBits.getAndSet(0))
        if (dx != 0f) { autoWakePending = false; idle.onInteraction(); carousel.dragBy(dx) }
        if (dy != 0f) { autoWakePending = false; idle.onInteraction(); applyVerticalDrag(dy) }
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
        data class ExitPanel(val index: Int) : Command
    }
    companion object { const val AUTO_ROTATE_DELAY_MILLIS = 5_000L }
}
