package br.com.ne3d.spbshellmodern.shell3d

import br.com.ne3d.spbshellmodern.shell3d.effects.EffectContext
import br.com.ne3d.spbshellmodern.shell3d.effects.EffectInput
import br.com.ne3d.spbshellmodern.shell3d.effects.MeshDeformer
import br.com.ne3d.spbshellmodern.shell3d.effects.OrigamiEffector
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffectSpec
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffectMode
import br.com.ne3d.spbshellmodern.shell3d.effects.configureEffect
import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshDeformerStackTest {
    @Test fun `none and stack keep base mesh while fold and origami use a working mesh`() {
        val panel = Panel3D("p", "P", 0)
        panel.configureEffect(PanelEffectMode.NONE); assertSame(panel.mesh, panel.renderMesh)
        panel.configureEffect(PanelEffectMode.STACK); assertSame(panel.mesh, panel.renderMesh)
        panel.configureEffect(PanelEffectMode.FOLD); panel.deformerStack.apply(panel, EffectInput().apply { progress = 1f }); assertTrue(panel.renderMesh === panel.workingMesh)
        panel.configureEffect(PanelEffectMode.ORIGAMI); panel.deformerStack.apply(panel, EffectInput().apply { progress = 1f }); assertTrue(panel.renderMesh === panel.workingMesh)
    }
    @Test fun `deformer lifecycle restores base mesh and is idempotent`() {
        var prepares = 0; var releases = 0
        val deformer = object : MeshDeformer {
            override fun prepare(context: EffectContext) { prepares++ }
            override fun apply(panel: Panel3D, input: EffectInput, mesh: br.com.ne3d.spbshellmodern.shell3d.scene.MutableMesh) = Unit
            override fun release() { releases++ }
        }
        val panel = Panel3D("p", "P", 0)
        panel.deformerStack.prepare(EffectContext(1)); panel.deformerStack.add(deformer)
        panel.deformerStack.apply(panel, EffectInput())
        assertEquals(1, prepares); assertTrue(panel.renderMesh === panel.workingMesh)
        assertTrue(panel.deformerStack.remove(deformer)); assertSame(panel.mesh, panel.renderMesh)
        panel.deformerStack.release(); panel.deformerStack.release()
        assertEquals(1, releases); assertTrue(panel.deformerStack.isEmpty())
    }
    @Test fun `working mesh is recreated for a replacement base mesh`() {
        val panel = Panel3D("p", "P", 0)
        panel.deformerStack.add(OrigamiEffector(PanelEffectSpec()))
        panel.deformerStack.apply(panel, EffectInput().apply { progress = 1f })
        val first = checkNotNull(panel.workingMesh)
        panel.mesh = MeshFactory.segmentedPlane(8, 4)
        panel.deformerStack.apply(panel, EffectInput().apply { progress = 1f })
        assertTrue(first !== panel.workingMesh); assertTrue(panel.workingMesh!!.baseMesh === panel.mesh)
    }
}
