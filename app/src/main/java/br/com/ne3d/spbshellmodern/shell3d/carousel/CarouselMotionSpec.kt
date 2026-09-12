package br.com.ne3d.spbshellmodern.shell3d.carousel

/** One calibration point for the renderer; values are deliberately independent of pixels. */
data class CarouselMotionSpec(
    // The radius is recalculated from this face width and the current panel count, so the
    // tangent cards close into one cylinder without gaps.
    // Vertically oriented 9:16 cards: the geometric half-height is 16/9 of the half-width.
    val radius: Float = 2.35f, val panelFaceWidth: Float = 2f, val panelAspectRatio: Float = 16f / 9f, val panelAngle: Float = 72f, val panelGap: Float = .11f,
    val cameraFov: Float = 58f, val cameraZ: Float = 7.2f, val cameraY: Float = 2.2f, val lookAtY: Float = 0f,
    val entryCameraFov: Float = 54f, val entryCameraZ: Float = 6.7f, val entryCameraY: Float = 1.75f,
    val centerScale: Float = .88f, val sideScale: Float = .88f, val sideAlpha: Float = .72f,
    val depthLift: Float = 0f, val sideFacingFactor: Float = 1f,
    val verticalDragToCameraY: Float = .008f, val minimumCameraY: Float = .6f, val maximumCameraY: Float = 4.2f,
    // One deliberate full-width swipe should expose several faces instead of
    // barely advancing the ring.
    val dragToAngleRatio: Float = .30f, val minimumFlingVelocity: Float = 240f,
    val maximumFlingVelocity: Float = 2_150f, val friction: Float = 4.35f,
    val snapThreshold: Float = 2.8f, val snapDurationMs: Long = 300,
    val entryDurationMs: Long = 430, val exitDurationMs: Long = 340,
    val entryRadiusDelayMs: Long = 70, val entrySpreadDelayMs: Long = 105, val entrySideAlphaDelayMs: Long = 125,
    val overshoot: Float = .04f, val settleThreshold: Float = .05f
)
