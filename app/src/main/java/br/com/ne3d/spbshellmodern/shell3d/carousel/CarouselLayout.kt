package br.com.ne3d.spbshellmodern.shell3d.carousel

import kotlin.math.*

enum class PanelRenderQuality { FULL, HIGH, STATIC, CULLED }
data class PanelTransform(val angle: Float, val x: Float, val y: Float, val z: Float, val rotationY: Float, val scale: Float, val alpha: Float, val visible: Boolean, val quality: PanelRenderQuality)
class MutablePanelTransform {
    var angle = 0f; var x = 0f; var y = 0f; var z = 0f; var rotationY = 0f; var scale = 1f; var alpha = 1f; var visible = false; var quality = PanelRenderQuality.FULL
}

class CarouselLayout(private val spec: CarouselMotionSpec) {
    /** Fewer faces grow to use the stage; dense rings contract without clipping. */
    fun sizeScale(count: Int): Float = (1.50f - count.coerceAtLeast(3) * .065f).coerceIn(.62f, 1.30f)

    /** Radius to the midpoint of each face of a closed regular polygon. */
    fun closedRadius(count: Int): Float {
        require(count >= 3) { "A carousel needs at least three panels" }
        return (spec.panelFaceWidth * spec.centerScale + spec.panelGap) / (2f * tan(Math.PI / count).toFloat())
    }

    fun transform(index: Int, count: Int, globalAngle: Float, radius: Float = spec.radius, spread: Float = 1f): PanelTransform {
        val result = MutablePanelTransform()
        transformInto(index, count, globalAngle, radius, spread, result)
        return PanelTransform(result.angle, result.x, result.y, result.z, result.rotationY, result.scale, result.alpha, result.visible, result.quality)
    }

    /** Renderer hot path: fills a reusable transform instead of allocating one per panel per frame. */
    fun transformInto(index: Int, count: Int, globalAngle: Float, radius: Float, spread: Float, result: MutablePanelTransform) {
        val step = 360f / count
        val angle = (index * step + globalAngle) * spread
        val radians = Math.toRadians(angle.toDouble())
        // Faces lie on the sides of a regular polygon.  The apothem (not the vertex radius)
        // preserves the configured small gap at every panel count.
        val countScale = sizeScale(count)
        val closedRadius = closedRadius(count)
        val effectiveRadius = closedRadius * countScale * (radius / spec.radius)
        val z = cos(radians).toFloat()
        val focus = (z + 1f) * .5f
        val normalized = ((angle + 180f) % 360f + 360f) % 360f - 180f
        val rank = kotlin.math.round(kotlin.math.abs(normalized) / step).toInt()
        val quality = when (rank) {
            0 -> PanelRenderQuality.FULL
            1 -> PanelRenderQuality.HIGH
            2, 3, 4 -> PanelRenderQuality.STATIC
            else -> PanelRenderQuality.CULLED
        }
        val y = (1f - z) * spec.depthLift
        result.angle = angle
        result.x = sin(radians).toFloat() * effectiveRadius
        result.y = y
        result.z = z * effectiveRadius
        result.rotationY = normalized * spec.sideFacingFactor
        result.scale = (spec.sideScale + (spec.centerScale - spec.sideScale) * focus) * countScale
        result.alpha = spec.sideAlpha + (1f - spec.sideAlpha) * focus
        result.visible = quality != PanelRenderQuality.CULLED
        result.quality = quality
    }
}
