package br.com.ne3d.spbshellmodern.engine

import kotlin.math.roundToInt

data class CarouselConfig(
    val dragSensitivity: Float = .22f,
    val maxAngularVelocity: Float = 1440f,
    val friction: Float = 1.2f,
    val sideRotation: Float = 58f,
    val minScale: Float = .48f,
    val minAlpha: Float = .50f,
    val cameraDistance: Float = 24f,
    val snapStiffness: Float = 350f
)

val DefaultCarouselConfig = CarouselConfig()
const val SNAP_THRESHOLD = 18f
const val ENABLE_IDLE_MOTION = false

fun normalizeAngle(angle: Float): Float {
    var value = angle % 360f
    if (value < 0f) value += 360f
    return value
}

fun stepAngle(count: Int): Float = if (count == 0) 0f else 360f / count

fun targetRotationForPanel(index: Int, count: Int): Float = -index * stepAngle(count)

fun nearestPanel(rotation: Float, count: Int): Int =
    if (count <= 0) 0 else (-normalizeAngle(rotation) / stepAngle(count)).roundToInt().mod(count)

fun nearestEquivalentTarget(index: Int, count: Int, rotation: Float): Float {
    val delta = normalizeAngle(targetRotationForPanel(index, count) - rotation + 180f) - 180f
    return rotation + delta
}
