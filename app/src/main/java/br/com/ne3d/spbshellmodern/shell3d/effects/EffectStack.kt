package br.com.ne3d.spbshellmodern.shell3d.effects

import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D
import br.com.ne3d.spbshellmodern.shell3d.scene.Transform3D

class EffectInput { var panelIndex = 0; var selectedIndex = 0; var angle = 0f; var velocity = 0f }
class EffectContext(val panelCount: Int)
fun interface PanelEffector { fun apply(panel: Panel3D, input: EffectInput, output: Transform3D) }
class EffectStack(private val effectors: List<PanelEffector> = emptyList()) {
    fun apply(panel: Panel3D, input: EffectInput) { panel.renderTransform.setFrom(panel.baseTransform); effectors.forEach { it.apply(panel, input, panel.renderTransform) } }
}
/** Baseline prism currently passes through the layout transform; future effects append after it. */
class SpbPrismEffector : PanelEffector { override fun apply(panel: Panel3D, input: EffectInput, output: Transform3D) = Unit }
