package br.com.ne3d.spbshellmodern.shell3d.core

/** Shared visual state for a panel and its reflected floor pass. */
object EffectivePanelRender {
    fun scale(baseScale: Float, isExitTarget: Boolean, exitProgress: Float): Float =
        baseScale * if (isExitTarget) 1f + exitProgress * .12f else 1f

    fun alpha(baseAlpha: Float, isExitTarget: Boolean, exiting: Boolean, exitProgress: Float, entryAlpha: Float): Float =
        baseAlpha * (if (exiting && !isExitTarget) 1f - exitProgress else 1f) * entryAlpha
}
