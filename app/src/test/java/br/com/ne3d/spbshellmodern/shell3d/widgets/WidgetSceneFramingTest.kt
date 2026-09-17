package br.com.ne3d.spbshellmodern.shell3d.widgets

import org.junit.Assert.*
import org.junit.Test

class WidgetSceneFramingTest {
    private fun assertValidFraming(framing: WidgetSceneFraming) {
        assertTrue(framing.fov > 0f)
        assertTrue(framing.fov < 120f)
        assertTrue(framing.cameraZ.isFinite())
        assertTrue(framing.cameraY.isFinite())
        assertTrue(framing.lookAtY.isFinite())
        assertTrue(framing.sceneScale.isFinite())
        assertTrue(framing.sceneScale > 0f)
    }

    @Test fun `world time framing is calibrated and valid`() {
        assertTrue(WidgetFramingProvider.hasOverride("world-time"))
        assertValidFraming(WidgetFramingProvider.framingFor("world-time"))
    }

    @Test fun `weather framing is calibrated and valid`() {
        assertTrue(WidgetFramingProvider.hasOverride("weather"))
        assertValidFraming(WidgetFramingProvider.framingFor("weather"))
    }

    @Test fun `system framing is calibrated and valid`() {
        assertTrue(WidgetFramingProvider.hasOverride("system"))
        assertValidFraming(WidgetFramingProvider.framingFor("system"))
    }

    @Test fun `scene without override keeps the shared default`() {
        assertFalse(WidgetFramingProvider.hasOverride("music"))
        assertEquals(WidgetSceneFraming(), WidgetFramingProvider.framingFor("music"))
        assertValidFraming(WidgetFramingProvider.framingFor("music"))
    }
}
