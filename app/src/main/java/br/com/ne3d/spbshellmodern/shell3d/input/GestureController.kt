package br.com.ne3d.spbshellmodern.shell3d.input

import android.view.MotionEvent
import android.view.VelocityTracker
import br.com.ne3d.spbshellmodern.shell3d.core.ShellEngine

class GestureController(
    private val engine: ShellEngine,
    private val invalidate: () -> Unit,
    private val onTap: (Float) -> Unit = {},
    private val onGestureStarted: () -> Unit = {},
    private val onGestureFinished: () -> Unit = {},
) {
    private var lastX = 0f; private var lastY = 0f; private var totalDx = 0f; private var totalDy = 0f; private var downTime = 0L; private var tracker: VelocityTracker? = null
    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; totalDx = 0f; totalDy = 0f; downTime = event.eventTime; engine.onGestureStart(); tracker = VelocityTracker.obtain().also { it.addMovement(event) }; onGestureStarted(); invalidate() }
            MotionEvent.ACTION_MOVE -> { val dx = event.x - lastX; val dy = event.y - lastY; totalDx += dx; totalDy += dy; if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) engine.onDrag(dx) else engine.onVerticalDrag(dy); lastX = event.x; lastY = event.y; tracker?.addMovement(event); invalidate() }
            MotionEvent.ACTION_UP -> {
                tracker?.addMovement(event); tracker?.computeCurrentVelocity(1000); engine.onGestureEnd()
                if (event.eventTime - downTime <= TAP_TIMEOUT_MS && kotlin.math.abs(totalDx) < TAP_SLOP_PX && kotlin.math.abs(totalDy) < TAP_SLOP_PX) onTap(event.x)
                else if (kotlin.math.abs(totalDx) >= kotlin.math.abs(totalDy)) engine.onFling(tracker?.xVelocity ?: 0f)
                tracker?.recycle(); tracker = null; onGestureFinished(); invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { engine.onGestureEnd(); tracker?.recycle(); tracker = null; onGestureFinished(); invalidate() }
        }; return true
    }
    private companion object { const val TAP_SLOP_PX = 14f; const val TAP_TIMEOUT_MS = 180L }
}
