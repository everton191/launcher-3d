package br.com.ne3d.spbshellmodern.shell3d.widgets.personal

import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import org.junit.Assert.*
import org.junit.Test

class CalendarSceneTest {
    private fun context() = WidgetSceneContext(1f, 1, 1, {})

    private fun snapshot(events: Int) = CalendarSnapshot(
        revision = 1L,
        available = true,
        events = List(events) { CalendarEventValue("$it", "event $it", it * 3_600_000L) },
    )

    @Test fun `cards are textured planes, never bare cubes`() {
        val scene = CalendarScene()
        scene.prepare(context())
        scene.update(snapshot(3))
        for (i in 0 until 6) {
            val card = requireNotNull(scene.graph.find("card-$i"))
            // Plane mesh has 4 vertices; the old cube had 24.
            assertEquals("card-$i must be a plane", 4, card.mesh?.vertexCount)
            val key = requireNotNull(card.material?.textureRef?.key)
            assertTrue("card-$i must carry an agenda texture, got $key", key.startsWith("agenda-card-"))
            assertFalse(key.contains("spb"))
        }
        scene.release()
    }

    @Test fun `first card is the highlighted one`() {
        val scene = CalendarScene()
        scene.prepare(context())
        val first = requireNotNull(scene.graph.find("card-0"))
        assertTrue(first.material?.textureRef?.key!!.contains("selected"))
        val second = requireNotNull(scene.graph.find("card-1"))
        assertFalse(second.material?.textureRef?.key!!.contains("selected"))
        scene.release()
    }

    @Test fun `header card is always visible with its own texture`() {
        val scene = CalendarScene()
        scene.prepare(context())
        scene.update(snapshot(0))
        val header = requireNotNull(scene.graph.find("agenda-header"))
        assertEquals(CalendarTextures.HEADER_KEY, header.material?.textureRef?.key)
        assertEquals(1f, header.local.alpha, 0f)
        scene.release()
    }

    @Test fun `visible cards follow the event count with depth stacking`() {
        val scene = CalendarScene()
        scene.prepare(context())
        scene.update(snapshot(3))
        val alphas = (0 until 6).map { requireNotNull(scene.graph.find("card-$it")).local.alpha }
        assertEquals(listOf(1f, 1f, 1f, 0f, 0f, 0f), alphas)
        val depths = (0 until 3).map { requireNotNull(scene.graph.find("card-$it")).local.z }
        assertTrue("cards must stack in depth, got $depths", depths[0] > depths[1] && depths[1] > depths[2])
        scene.release()
    }

    @Test fun `entry motion runs on update then settles for render on demand`() {
        val scene = CalendarScene()
        scene.prepare(context())
        scene.update(snapshot(3))
        assertTrue(scene.tick(1f / 60f))
        // Exhaust the entry window: motion must stop asking for frames.
        var frames = 0
        while (scene.tick(1f) && frames++ < 30) Unit
        assertFalse(scene.tick(1f))
        scene.release()
    }

    @Test fun `texture keys are original and distinct`() {
        val keys = setOf(
            CalendarTextures.CARD_AMBER_KEY, CalendarTextures.CARD_TEAL_KEY,
            CalendarTextures.CARD_VIOLET_KEY, CalendarTextures.CARD_SELECTED_KEY,
            CalendarTextures.HEADER_KEY,
        )
        assertEquals(5, keys.size)
        for (key in keys) {
            assertFalse(key.contains("spb"))
            assertFalse(key.contains("yandex"))
        }
    }

    @Test fun `contacts scene keeps the legacy solid blocks`() {
        val scene = ContactsScene()
        scene.prepare(context())
        val card = requireNotNull(scene.graph.find("card-0"))
        assertEquals("contacts must stay cubes", 24, card.mesh?.vertexCount)
        assertNull(card.material?.textureRef)
        scene.release()
    }
}
