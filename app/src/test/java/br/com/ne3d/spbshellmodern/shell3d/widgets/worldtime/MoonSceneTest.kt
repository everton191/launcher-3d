package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import org.junit.Assert.*
import org.junit.Test

class MoonSceneTest {
    private fun prepared(): MoonScene {
        val scene = MoonScene()
        scene.prepare(WidgetSceneContext(1f, 1, 1, {}))
        return scene
    }

    @Test fun `moon id is registered for the moon panel`() {
        assertEquals("moon", MoonWidgetIds.MOON)
        val scene = prepared()
        assertEquals("moon", scene.id)
        scene.release()
    }

    @Test fun `moon sphere uses the original surface texture, never spb assets`() {
        val scene = prepared()
        val moon = requireNotNull(scene.graph.find("moon"))
        val key = requireNotNull(moon.material?.textureRef?.key)
        assertEquals(MoonTexture.KEY, key)
        assertTrue(key.startsWith("moon-surface-"))
        assertFalse(key.contains("spb"))
        assertFalse(key.contains("yandex"))
        scene.release()
    }

    @Test fun `discreet glow plane sits behind the sphere with its own texture`() {
        val scene = prepared()
        val glow = requireNotNull(scene.graph.find("moon-glow"))
        assertEquals(MoonGlowTexture.KEY, glow.material?.textureRef?.key)
        assertTrue(glow.local.z < 0f)
        assertTrue("glow must survive presentation culling", glow.local.z >= -0.05f)
        scene.release()
    }

    @Test fun `moon rotates slowly and settles for render on demand`() {
        val scene = prepared()
        val moon = requireNotNull(scene.graph.find("moon"))
        val before = moon.local.rotationY
        assertTrue(scene.tick(.5f))
        assertTrue("moon must turn, $before -> ${moon.local.rotationY}", moon.local.rotationY != before)
        scene.pause()
        assertFalse(scene.tick(.5f))
        scene.resume()
        assertTrue(scene.tick(.5f))
        scene.release()
        scene.release()
    }
}
