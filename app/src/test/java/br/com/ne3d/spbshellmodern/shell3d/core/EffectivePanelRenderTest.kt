package br.com.ne3d.spbshellmodern.shell3d.core

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectivePanelRenderTest {
    @Test fun `exit target scale is shared by panel and mirror`() {
        assertEquals(1.06f, EffectivePanelRender.scale(1f, true, .5f), 0.0001f)
        assertEquals(1f, EffectivePanelRender.scale(1f, false, .5f), 0.0001f)
    }

    @Test fun `non target exit fade is also reflected`() {
        val alpha = EffectivePanelRender.alpha(.8f, false, true, .5f, 1f)
        assertEquals(.4f, alpha, 0.0001f)
        assertEquals(.12f, alpha * .30f, 0.0001f)
    }
}
