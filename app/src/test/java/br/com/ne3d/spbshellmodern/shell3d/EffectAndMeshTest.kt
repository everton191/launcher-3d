package br.com.ne3d.spbshellmodern.shell3d

import br.com.ne3d.spbshellmodern.shell3d.effects.EffectInput
import br.com.ne3d.spbshellmodern.shell3d.effects.EffectStack
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffector
import br.com.ne3d.spbshellmodern.shell3d.effects.EffectContext
import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D
import br.com.ne3d.spbshellmodern.shell3d.scene.Transform3D
import br.com.ne3d.spbshellmodern.shell3d.core.ShellEngine
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
        val add = object : PanelEffector { override fun apply(panel: Panel3D, input: EffectInput, out: Transform3D) { out.x += 10f } }
        val multiply = object : PanelEffector { override fun apply(panel: Panel3D, input: EffectInput, out: Transform3D) { out.x *= 2f } }
        EffectStack().apply { add(add); add(multiply) }.apply(p, EffectInput())
        assertEquals(22f, p.renderTransform.x, 0f)
    }
    @Test fun `plane and segmented meshes have finite uv geometry`() {
        listOf(MeshFactory.plane(1f), MeshFactory.segmentedPlane(1, 1), MeshFactory.segmentedPlane(2, 2), MeshFactory.segmentedPlane(4, 4)).forEach { mesh ->
            assertTrue(mesh.vertexCount >= 4)
            mesh.vertices.duplicate().apply { position(0) }.let { buffer -> while (buffer.hasRemaining()) assertTrue(buffer.get().isFinite()) }
        }
        assertEquals(25, MeshFactory.segmentedPlane(4, 4).vertexCount)
        assertEquals(24, MeshFactory.segmentedPlane(2, 2).indexCount)
        assertEquals(96, MeshFactory.segmentedPlane(4, 4).indexCount)
    }
    @Test fun `stack supports add remove clear and lifecycle`() {
        var prepares = 0; var releases = 0
        val effect = object : PanelEffector {
            override fun prepare(context: EffectContext) { prepares++ }
            override fun apply(panel: Panel3D, input: EffectInput, output: Transform3D) = Unit
            override fun release() { releases++ }
        }
        val stack = EffectStack(); stack.prepare(EffectContext(2)); stack.add(effect)
        assertEquals(1, stack.size); assertEquals(1, prepares); assertTrue(stack.remove(effect)); assertEquals(1, releases)
        stack.add(effect); stack.clear(); assertTrue(stack.isEmpty()); assertEquals(2, releases)
    }
    @Test fun `default panels share plane and may select distinct meshes`() {
        val a = Panel3D("a", "A", 0); val b = Panel3D("b", "B", 0)
        org.junit.Assert.assertSame(a.mesh, b.mesh)
        b.mesh = MeshFactory.segmentedPlane(2, 2)
        assertTrue(a.mesh !== b.mesh)
    }
    @Test fun `engine prepares real context and releases effects once`() {
        var prepares = 0; var releases = 0; var context: EffectContext? = null
        val effect = object : PanelEffector {
            override fun prepare(value: EffectContext) { prepares++; context = value }
            override fun apply(panel: Panel3D, input: EffectInput, output: Transform3D) = Unit
            override fun release() { releases++ }
        }
        val first = Panel3D("home", "Home", 0).also { it.effectStack.add(effect) }
        val second = Panel3D("apps", "Apps", 0)
        val engine = ShellEngine(panels = listOf(first, second))
        assertEquals(1, prepares); assertEquals(2, context!!.panelCount); org.junit.Assert.assertSame(engine.spec, context!!.motionSpec)
        repeat(100) { first.effectStack.apply(first, EffectInput()) }; assertEquals(1, prepares)
        engine.releaseEffects(); engine.releaseEffects(); assertEquals(1, releases); assertTrue(first.effectStack.isEmpty())
    }
}
