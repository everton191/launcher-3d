package br.com.ne3d.spbshellmodern.shell3d.scene

import br.com.ne3d.spbshellmodern.shell3d.effects.EffectStack
import br.com.ne3d.spbshellmodern.shell3d.effects.MeshDeformerStack

enum class PanelTextureKind { STATIC, REAL_SNAPSHOT }
class Panel3D(val id:String,val label:String,val color:Int,val textureKind:PanelTextureKind=PanelTextureKind.STATIC,var baseMesh:Mesh=sharedPlane){
 val material=Material();val baseTransform=Transform3D();val renderTransform=Transform3D();val effectStack=EffectStack();val deformerStack=MeshDeformerStack()
 /** Compatibility alias for callers that provide a custom canonical mesh. */
 var mesh: Mesh
  get() = baseMesh
  set(value) { baseMesh = value; if (effectMesh == null) resetRenderMesh() }
 var effectMesh: Mesh? = null
 var workingMesh:MutableMesh?=null
 var renderMesh:RenderMesh=baseMesh
 /** GL-owned state shared by the panel and its mirror during the current frame. */
 var effectiveScale = 1f
 var effectiveAlpha = 1f
 private val activeMesh: Mesh get() = effectMesh ?: baseMesh
 fun ensureWorkingMesh(){if(workingMesh?.baseMesh !== activeMesh)workingMesh=MutableMesh(activeMesh)}
 fun resetRenderMesh(){renderMesh=baseMesh; effectMesh=null}
 companion object{val sharedPlane:Mesh=MeshFactory.plane(16f/9f)}
}
