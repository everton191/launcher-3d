package br.com.ne3d.spbshellmodern.shell3d

import br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffectMode
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelTransitionController
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelTransitionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelTransitionControllerTest {
    @Test fun `opening and closing wait for texture and use their own effect duration`() {
        val controller = PanelTransitionController()
        controller.requestOpen(2, PanelEffectMode.FOLD)
        assertTrue(controller.blocksInput); assertTrue(controller.composeVisible)
        controller.onTextureReady(); controller.tick(.16f)
        assertEquals(PanelTransitionState.GL_OPENING, controller.state); assertEquals(.5f, controller.progress, .01f)
        controller.tick(.16f); assertEquals(PanelTransitionState.COMPOSE_LIVE, controller.state); assertFalse(controller.blocksInput)
        controller.requestClose(); controller.onTextureReady(); controller.tick(.32f)
        assertEquals(PanelTransitionState.IDLE, controller.state)
    }
}
