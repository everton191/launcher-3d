package br.com.ne3d.spbshellmodern.shell3d.effects

import br.com.ne3d.spbshellmodern.shell3d.scene.MutableMesh
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D
import br.com.ne3d.spbshellmodern.shell3d.scene.Transform3D
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

interface MeshDeformer { fun prepare(context: EffectContext) {}; fun apply(panel: Panel3D, input: EffectInput, mesh: MutableMesh); fun release() {} }

/** Owns only per-panel working geometry; base meshes remain immutable. */
class MeshDeformerStack {
    private val deformers = ArrayList<MeshDeformer>()
    private var context: EffectContext? = null
    private var lastPanel: Panel3D? = null
    val size: Int get() = deformers.size
    fun isEmpty(): Boolean = deformers.isEmpty()
    fun prepare(context: EffectContext) { this.context = context; deformers.forEach { it.prepare(context) } }
    fun add(deformer: MeshDeformer) { deformers += deformer; context?.let(deformer::prepare) }
    fun remove(deformer: MeshDeformer): Boolean {
        val removed = deformers.remove(deformer)
        if (removed) deformer.release()
        if (deformers.isEmpty()) lastPanel?.resetRenderMesh()
        return removed
    }
    fun clear() { deformers.forEach { it.release() }; deformers.clear(); lastPanel?.resetRenderMesh() }
    fun release() { clear(); context = null; lastPanel = null }
    fun apply(panel: Panel3D, input: EffectInput) {
        lastPanel = panel
        if (deformers.isEmpty()) { panel.resetRenderMesh(); return }
        panel.ensureWorkingMesh()
        val work = checkNotNull(panel.workingMesh)
        work.reset()
        deformers.forEach { it.apply(panel, input, work) }
        work.commit()
        panel.renderMesh = work
    }
}

enum class PanelEffectMode { NONE, STACK, FOLD, ORIGAMI }
enum class EffectPhase { NONE, OPENING, CLOSING, PREVIEW }
data class PanelEffectSpec(val stackDepth:Float=.35f,val stackScale:Float=.92f,val foldAngleDegrees:Float=68f,val origamiFoldCount:Int=4,val origamiAngleDegrees:Float=56f)

class StackEffector(private val spec:PanelEffectSpec):PanelEffector {
    override fun apply(panel:Panel3D,input:EffectInput,output:Transform3D) {
        val p=input.progress
        if(input.panelIndex==input.selectedIndex){ output.z+=spec.stackDepth*p; output.scaleX+=.06f*p; output.scaleY+=.06f*p }
        else { output.z-=spec.stackDepth*p; output.scaleX*=1f-(1f-spec.stackScale)*p; output.scaleY*=1f-(1f-spec.stackScale)*p; output.alpha*=1f-.2f*p }
    }
}

class FoldEffector(private val spec:PanelEffectSpec):MeshDeformer {
    override fun apply(panel:Panel3D,input:EffectInput,mesh:MutableMesh) {
        val theta=Math.toRadians((spec.foldAngleDegrees*input.progress*input.direction).toDouble())
        val c=cos(theta).toFloat(); val s=sin(theta).toFloat()
        for(i in 0 until mesh.vertexCount) { val x=mesh.x(i); if(x>0f) mesh.setPosition(i,x*c,mesh.y(i),x*s) }
    }
}

/** Alternating rigid strips joined at their transformed hinges. */
class OrigamiEffector(private val spec:PanelEffectSpec):MeshDeformer {
    override fun apply(panel:Panel3D,input:EffectInput,mesh:MutableMesh) {
        val folds=spec.origamiFoldCount.coerceAtLeast(1)
        val segmentLength=2f/folds
        val theta=Math.toRadians((spec.origamiAngleDegrees*input.progress*input.direction).toDouble()).toFloat()
        for(i in 0 until mesh.vertexCount) {
            val sourceX=mesh.x(i)
            val segment=floor(((sourceX+1f)/segmentLength).toDouble()).toInt().coerceIn(0,folds-1)
            var hingeX=-1f; var hingeZ=0f; var orientation=0f
            for(part in 0 until segment) {
                orientation += if(part % 2 == 0) theta else -theta
                hingeX += segmentLength*cos(orientation)
                hingeZ += segmentLength*sin(orientation)
            }
            orientation += if(segment % 2 == 0) theta else -theta
            val segmentStart=-1f+segment*segmentLength
            val local=(sourceX-segmentStart).coerceIn(0f,segmentLength)
            mesh.setPosition(i,hingeX+local*cos(orientation),mesh.y(i),hingeZ+local*sin(orientation))
        }
    }
}
