package br.com.ne3d.spbshellmodern.shell3d.presentation

import br.com.ne3d.spbshellmodern.shell3d.animation.PanelPresentationPhase
import kotlin.math.sin

/** Shared motion curves for live presentations. */
object PresentationMotion {
    fun smoothstep(value: Float): Float =
        value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    /** 0 at rest, ramps in INTRO, holds 1 in ACTIVE, ramps out in OUTRO. */
    fun envelope(phase: PanelPresentationPhase, phaseProgress: Float): Float = when (phase) {
        PanelPresentationPhase.IDLE -> 0f
        PanelPresentationPhase.INTRO -> smoothstep(phaseProgress)
        PanelPresentationPhase.ACTIVE -> 1f
        PanelPresentationPhase.OUTRO -> 1f - smoothstep(phaseProgress)
    }

    /** 0..1 oscillation for pulses, drifts and equalizers. */
    fun pulse(timeSeconds: Float, speed: Float, offset: Float = 0f): Float =
        (sin((timeSeconds * speed + offset) * Math.PI.toFloat() * 2f) * .5f + .5f).coerceIn(0f, 1f)

    /** Staggered 0..1 ramp: item `index` of `total` starts after `stagger` of the phase. */
    fun stagger(phaseProgress: Float, index: Int, total: Int, stagger: Float = .5f): Float {
        if (total <= 1) return phaseProgress.coerceIn(0f, 1f)
        val start = stagger * index / (total - 1)
        return ((phaseProgress - start) / (1f - start)).coerceIn(0f, 1f)
    }
}
