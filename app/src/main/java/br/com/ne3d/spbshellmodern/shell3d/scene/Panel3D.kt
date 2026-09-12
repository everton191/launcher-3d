package br.com.ne3d.spbshellmodern.shell3d.scene

import br.com.ne3d.spbshellmodern.shell3d.effects.EffectStack
import br.com.ne3d.spbshellmodern.shell3d.effects.MeshDeformerStack

enum class PanelTextureKind { STATIC, REAL_SNAPSHOT }
class Panel3D(val id:String,val label:String,val color:Int,val textureKind:PanelTextureKind=PanelTextureKind.STATIC,var mesh:Mesh=sharedPlane){
 val material=Material();val baseTransform=Transform3D();val renderTransform=Transform3D();val effectStack=EffectStack();val deformerStack=MeshDeformerStack();var workingMesh:MutableMesh?=null;var renderMesh:RenderMesh=mesh
 fun ensureWorkingMesh(){if(workingMesh==null)workingMesh=MutableMesh(mesh)}
 companion object{val sharedPlane:Mesh=MeshFactory.plane(16f/9f)}
}
