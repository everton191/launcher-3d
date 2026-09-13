package br.com.ne3d.spbshellmodern.shell3d.widgets.scene

import br.com.ne3d.spbshellmodern.shell3d.scene.RenderMesh
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetMaterial
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetRenderItem

class SceneNode(val id: String) {
    val local = SceneTransform()
    internal val localMatrix = FloatArray(16)
    val worldMatrix = FloatArray(16)
    var worldAlpha = 1f; var worldVisible = true
    var mesh: RenderMesh? = null; var material: WidgetMaterial? = null
    var interactionId: String? = null
    internal var parent: SceneNode? = null
    private val children = ArrayList<SceneNode>()
    internal val renderItem = WidgetRenderItem(this)
    fun add(child: SceneNode): SceneNode { require(child.parent == null) { "Node already has a parent" }; child.parent=this; children.add(child); return child }
    fun remove(child: SceneNode): Boolean { if(!children.remove(child)) return false; child.parent=null; return true }
    fun children(): List<SceneNode> = children
}

/** GL-thread scene graph. Its traversal reuses node-owned matrices and render items. */
class SceneGraph(val root: SceneNode = SceneNode("root")) {
    private val items = ArrayList<WidgetRenderItem>(16)
    fun find(id: String): SceneNode? = find(root, id)
    fun updateWorld() { SceneMatrix.identity(root.worldMatrix); root.worldAlpha=1f; root.worldVisible=true; updateChildren(root) }
    fun renderItems(): List<WidgetRenderItem> { items.clear(); collect(root); return items }
    fun clear() { while(root.children().isNotEmpty()) root.remove(root.children().last()) }
    private fun find(node: SceneNode, id: String): SceneNode? { if(node.id==id)return node; var i=0; val children=node.children(); while(i<children.size){ val found=find(children[i],id); if(found!=null)return found; i++ }; return null }
    private fun updateChildren(parent: SceneNode) { val children=parent.children(); var i=0; while(i<children.size){ val node=children[i]; SceneMatrix.local(node.localMatrix,node.local); SceneMatrix.multiply(node.worldMatrix,parent.worldMatrix,node.localMatrix); node.worldAlpha=parent.worldAlpha*node.local.alpha; node.worldVisible=parent.worldVisible && node.local.alpha>0f; updateChildren(node); i++ } }
    private fun collect(node: SceneNode) { if(node.worldVisible && node.mesh!=null && node.material!=null) items.add(node.renderItem); val children=node.children(); var i=0; while(i<children.size){ collect(children[i]); i++ } }
}
