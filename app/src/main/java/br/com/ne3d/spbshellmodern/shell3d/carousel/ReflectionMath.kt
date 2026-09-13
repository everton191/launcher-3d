package br.com.ne3d.spbshellmodern.shell3d.carousel

import kotlin.math.abs

/** Visual reflection is driven by the face angle, never by navigation selection. */
object ReflectionMath {
    fun normalizedAngle(angle: Float): Float = ((angle + 180f) % 360f + 360f) % 360f - 180f

    fun focus(panelAngle: Float, panelCount: Int): Float {
        if (panelCount <= 0) return 0f
        val fadeAngle = 180f / panelCount
        return (1f - abs(normalizedAngle(panelAngle)) / fadeAngle).coerceIn(0f, 1f)
    }
}
