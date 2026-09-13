package br.com.ne3d.spbshellmodern.shell3d.effects

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelEffectDebugTest {
    @Test fun `selector accepts effect names case insensitively and defaults to none`() {
        PanelEffectDebug.select("oRiGaMi")
        assertEquals(PanelEffectMode.ORIGAMI, PanelEffectDebug.mode)
        PanelEffectDebug.select("unknown")
        assertEquals(PanelEffectMode.NONE, PanelEffectDebug.mode)
    }
}
