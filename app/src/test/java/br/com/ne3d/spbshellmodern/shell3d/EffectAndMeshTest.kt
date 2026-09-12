package br.com.ne3d.spbshellmodern.shell3d

import br.com.ne3d.spbshellmodern.shell3d.effects.EffectInput
import br.com.ne3d.spbshellmodern.shell3d.effects.EffectStack
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffector
import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D
import br.com.ne3d.spbshellmodern.shell3d.scene.Transform3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectAndMeshTest {
    @Test fun `empty stack copies base transform without state leak`() {
        val a = Panel3D("a", "A", 0); val b = Panel3D("b", "B", 0); a.baseTransform.x = 12f
        a.effectStack.apply(a, EffectInput()); b.effectStack.apply(b, EffectInput())
        assertEquals(12f, a.renderTransform.x, 0f); assertEquals(0f, b.renderTransform.x, 0f)
    }
    @Test fun `effect order is deterministic`() {
        val p = Panel3D("p", "P", 0); p.baseTransform.x = 1f
        val add = PanelEffector { _, _, out -> out.x += 10f }
        val multiply = PanelEffector { _, _, out -> out.x *= 2f }
        EffectStack(listOf(add, multiply)).apply(p, EffectInput())
        assertEquals(22f, p.renderTransform.x, 0f)
    }
    @Test fun `plane and segmented meshes have finite uv geometry`() {
        listOf(MeshFactory.plane(1f), MeshFactory.segmentedPlane(1, 1), MeshFactory.segmentedPlane(2, 2), MeshFactory.segmentedPlane(4, 4)).forEach { mesh ->
            assertTrue(mesh.vertexCount >= 4)
            mesh.vertices.duplicate().apply { position(0) }.let { buffer -> while (buffer.hasRemaining()) assertTrue(buffer.get().isFinite()) }
        }
        assertEquals(25, MeshFactory.segmentedPlane(4, 4).vertexCount)
    }
}
