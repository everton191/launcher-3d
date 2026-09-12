package br.com.ne3d.spbshellmodern.shell3d.core

import br.com.ne3d.spbshellmodern.shell3d.carousel.*
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D
import br.com.ne3d.spbshellmodern.shell3d.animation.CarouselEntryTransition
import br.com.ne3d.spbshellmodern.shell3d.animation.CarouselExitTransition
import br.com.ne3d.spbshellmodern.shell3d.animation.CarouselIdleController
import kotlin.math.roundToInt
import kotlin.math.abs
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
    var selectedIndex = 0
        private set
    var cameraY = spec.cameraY
        private set
    private var autoRotationRequested = false
    private var autoWakePending = false
    private val commands = ConcurrentLinkedQueue<Command>()
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
    }
    /** Main/UI thread entry points only enqueue; the GL thread owns all physics mutation. */
    fun onDrag(dx: Float) = accumulate(pendingDxBits, dx)
    fun onGestureStart() = commands.add(Command.GestureStart)
    fun onGestureEnd() = commands.add(Command.GestureEnd)
    fun onVerticalDrag(dy: Float) = accumulate(pendingDyBits, dy)
    fun onFling(velocityX: Float) = commands.add(Command.Fling(velocityX))
    fun beginExitAt(tapX: Float, surfaceWidth: Float) = commands.add(Command.Exit(tapX, surfaceWidth))
    fun beginAutoRotation() = commands.add(Command.AutoRotate)

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
    fun tick(dt: Float): Boolean {
        drainCommands()
        val entering = entry.tick(dt)
        if (entering) return true
        if (exit.active) return exit.tick(dt)
        if (carousel.dragging) return true
        val settling = carousel.tick(dt, state.panels.size)
        if (settling) return true
        if (autoRotationRequested) {
            carousel.autoRotate(dt * AUTO_ROTATE_DEGREES_PER_SECOND)
            return true
        }
        idle.onIdle()
        autoWakePending = true
        return false
    }
    fun consumeExitCompleted() = exit.consumeCompleted()
    fun consumeAutoWakePending(): Boolean = autoWakePending.also { autoWakePending = false }

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
    }
    companion object { const val AUTO_ROTATE_DELAY_MILLIS = 5_000L; private const val AUTO_ROTATE_DEGREES_PER_SECOND = 18f }
}
