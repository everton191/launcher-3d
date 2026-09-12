package br.com.ne3d.spbshellmodern.shell3d.effects

import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselMotionSpec
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D
import br.com.ne3d.spbshellmodern.shell3d.scene.Transform3D

class EffectInput { var panelIndex = 0; var selectedIndex = 0; var angle = 0f; var velocity = 0f }
class EffectContext(val panelCount: Int, val motionSpec: CarouselMotionSpec? = null)
interface PanelEffector { fun prepare(context: EffectContext) {} ; fun apply(panel: Panel3D, input: EffectInput, output: Transform3D); fun release() {} }
class EffectStack {
    private val effectors = ArrayList<PanelEffector>()
    private var context: EffectContext? = null
    val size: Int get() = effectors.size
    fun isEmpty(): Boolean = effectors.isEmpty()
    fun prepare(context: EffectContext) { this.context = context; var i=0; while(i<effectors.size){ effectors[i].prepare(context); i++ } }
    fun add(effector: PanelEffector) { effectors.add(effector); context?.let(effector::prepare) }
    fun remove(effector: PanelEffector): Boolean { val removed=effectors.remove(effector); if(removed) effector.release(); return removed }
    fun clear() { var i=0; while(i<effectors.size){ effectors[i].release(); i++ }; effectors.clear() }
    fun apply(panel: Panel3D, input: EffectInput) { panel.renderTransform.setFrom(panel.baseTransform); var i=0; while(i<effectors.size){ effectors[i].apply(panel,input,panel.renderTransform); i++ } }
}
class SpbPrismEffector : PanelEffector { override fun apply(panel: Panel3D, input: EffectInput, output: Transform3D) = Unit }
