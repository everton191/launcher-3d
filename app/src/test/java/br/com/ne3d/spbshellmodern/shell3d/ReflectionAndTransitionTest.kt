package br.com.ne3d.spbshellmodern.shell3d

import br.com.ne3d.spbshellmodern.shell3d.carousel.ReflectionMath
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffectMode
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelTransitionController
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelTransitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflectionAndTransitionTest {
    @Test fun `reflection focus follows face angle independently of selection`() {
        assertEquals(1f, ReflectionMath.focus(0f, 6), 0f)
        assertEquals(0f, ReflectionMath.focus(30f, 6), .0001f)
        assertEquals(0f, ReflectionMath.focus(150f, 6), 0f)
        assertEquals(1f, ReflectionMath.focus(360f, 6), 0f)
    }

    @Test fun `transition waits for texture and blocks input only while handing off`() {
        val controller = PanelTransitionController()
        controller.requestOpen(2, PanelEffectMode.FOLD)
        assertEquals(PanelTransitionState.CAPTURE_PENDING, controller.state)
        assertTrue(controller.composeVisible); assertTrue(controller.blocksInput)
        controller.onTextureReady(); assertEquals(PanelTransitionState.GL_OPENING, controller.state)
        controller.tick(.32f); assertEquals(PanelTransitionState.COMPOSE_LIVE, controller.state)
        assertFalse(controller.blocksInput)
        controller.requestClose(); assertEquals(PanelTransitionState.CAPTURE_PENDING, controller.state)
        controller.onTextureReady(); controller.tick(.32f)
        assertEquals(PanelTransitionState.IDLE, controller.state)
    }
}
