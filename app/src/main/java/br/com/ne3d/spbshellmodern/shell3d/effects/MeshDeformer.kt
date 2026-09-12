package br.com.ne3d.spbshellmodern.shell3d.effects

import br.com.ne3d.spbshellmodern.shell3d.scene.MutableMesh
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D

interface MeshDeformer { fun prepare(context: EffectContext) {}; fun apply(panel: Panel3D, input: EffectInput, mesh: MutableMesh); fun release() {} }
class MeshDeformerStack {
    private val deformers=ArrayList<MeshDeformer>()
    fun add(value: MeshDeformer){deformers.add(value)}
    fun apply(panel:Panel3D,input:EffectInput){ val work=panel.workingMesh ?: return; work.reset(); var i=0;while(i<deformers.size){deformers[i].apply(panel,input,work);i++};work.commit();panel.renderMesh=work }
    fun clear(){var i=0;while(i<deformers.size){deformers[i].release();i++};deformers.clear()}
}
enum class PanelEffectMode { NONE, STACK, FOLD, ORIGAMI }
enum class EffectPhase { NONE, OPENING, CLOSING, PREVIEW }
data class PanelEffectSpec(val stackDepth:Float=.35f,val stackScale:Float=.92f,val foldAngleDegrees:Float=68f,val origamiFoldCount:Int=4,val origamiAngleDegrees:Float=56f)
class StackEffector(private val spec:PanelEffectSpec):PanelEffector { override fun apply(panel:Panel3D,input:EffectInput,output:br.com.ne3d.spbshellmodern.shell3d.scene.Transform3D){ val p=input.progress; if(input.panelIndex==input.selectedIndex){output.z+=spec.stackDepth*p;output.scaleX+=.06f*p;output.scaleY+=.06f*p}else{output.z-=spec.stackDepth*p;output.scaleX*=1f-(1f-spec.stackScale)*p;output.scaleY*=1f-(1f-spec.stackScale)*p;output.alpha*=1f-.2f*p} } }
class FoldEffector(private val spec:PanelEffectSpec):MeshDeformer { override fun apply(panel:Panel3D,input:EffectInput,mesh:MutableMesh){ val theta=Math.toRadians((spec.foldAngleDegrees*input.progress*input.direction).toDouble());val c=kotlin.math.cos(theta).toFloat();val s=kotlin.math.sin(theta).toFloat();var i=0;while(i<mesh.vertexCount){val x=mesh.x(i);if(x>0f)mesh.setPosition(i,x*c,mesh.y(i),x*s);i++} } }
class OrigamiEffector(private val spec:PanelEffectSpec):MeshDeformer { override fun apply(panel:Panel3D,input:EffectInput,mesh:MutableMesh){ val folds=spec.origamiFoldCount; val theta=Math.toRadians((spec.origamiAngleDegrees*input.progress).toDouble());val c=kotlin.math.cos(theta).toFloat();val s=kotlin.math.sin(theta).toFloat();var i=0;while(i<mesh.vertexCount){val x=mesh.x(i);val band=(((x+1f)*.5f*folds).toInt()).coerceIn(0,folds-1);val sign=if(band%2==0)1f else -1f; val local=((x+1f)*folds*.5f)%1f-.5f;mesh.setPosition(i,x,mesh.y(i),sign*local*s*.55f);i++} } }
