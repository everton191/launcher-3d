package br.com.ne3d.spbshellmodern.shell3d

import br.com.ne3d.spbshellmodern.shell3d.core.ShellEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class ShellEngineInputTest {
    @Test fun `coalesced drags are consumed before gesture end and fling`() {
        val engine = ShellEngine()
        engine.onGestureStart()
        engine.onDrag(10f)
        engine.onDrag(20f)
        engine.onGestureEnd()
        engine.onFling(0f)
        engine.tick(.016f)
        assertEquals(30f * engine.spec.dragToAngleRatio, engine.carousel.angle, .001f)
    }
    @Test fun `pending drag survives gesture end without fling`() {
        val engine = ShellEngine()
        engine.onGestureStart(); engine.onDrag(18f); engine.onGestureEnd(); engine.tick(.016f)
        assertEquals(18f * engine.spec.dragToAngleRatio, engine.carousel.angle, .001f)
    }
}
