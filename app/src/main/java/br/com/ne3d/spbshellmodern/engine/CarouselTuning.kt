package br.com.ne3d.spbshellmodern.engine

/** Measured legacy units remain reference metadata, never interpreted as pixels. */
object CarouselTuning {
    const val liveReflections = true
    const val referencePanelAspect = 460f / 662f
    const val legacyPortraitDistance = 10f
    const val legacyLandscapeDistance = 5f
    const val enterDuration = 360
    const val exitDuration = 320
    const val normalDuration = 220
    // Provisional idle delay; the manual specifies a few seconds, not this value.
    const val idleDelayMillis = 4_500L
    const val edgeHoverMillis = 650L
    const val legacyCameraMode = false
}

enum class CameraState { CLOSE_UP, TRANSITION, CAROUSEL_DISTANCE }

fun cameraState(progress: Float) = when {
    progress <= 0f -> CameraState.CLOSE_UP
    progress >= 1f -> CameraState.CAROUSEL_DISTANCE
    else -> CameraState.TRANSITION
}
