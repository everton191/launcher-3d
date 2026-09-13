package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetProjection
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class WorldTimeSceneTest {
    @Test fun `default cities use valid zones and snapshot has local values`() { val source=WorldTimeDataSource();source.publishNow(Instant.parse("2026-06-01T12:00:00Z"));val snapshot=source.latest()!!;assertEquals(6,snapshot.values.size);assertTrue(snapshot.values.all{it.time.matches(Regex("\\d{2}:\\d{2}"))}) }
    @Test fun `latitude longitude positions are finite and lie on requested radius`() { listOf(0f to 0f,90f to 0f,-90f to 0f,0f to 180f,0f to -180f).forEach{(lat,lon)->val point=WorldTimeCoordinates.latLon(lat,lon,1.06f);assertTrue(point.x.isFinite()&&point.y.isFinite()&&point.z.isFinite());assertEquals(1.06f,point.magnitude(),.0001f)} }
    @Test fun `markers are earth children and selection is safe`() { val scene=WorldTimeScene();scene.prepare(WidgetSceneContext(1f,1,1,{}));val marker=scene.graph.find("marker-tokyo")!!;val before=marker.worldMatrix.copyOf();scene.tick(.5f);assertFalse(before.contentEquals(marker.worldMatrix));assertTrue(scene.select("tokyo"));assertFalse(scene.select("unknown"));scene.release();scene.release() }
    @Test fun `latest snapshot wins and offsets use GMT`() {
        val source = WorldTimeDataSource(locale = java.util.Locale.US)
        val instant = java.time.Instant.parse("2026-07-01T12:00:00Z")
        source.publishNow(instant)
        source.select("london", instant.plusSeconds(60))
        source.select("tokyo", instant.plusSeconds(120))
        val latest = requireNotNull(source.latest())
        assertEquals("tokyo", latest.selectedCityId)
        assertEquals("GMT+09:00", latest.selectedValue()?.offset)
        assertEquals("GMT", WorldTimeFormatter.gmtOffset("Z"))
        assertEquals("GMT-03:00", WorldTimeFormatter.gmtOffset("-03:00"))
    }

    @Test fun `hit test selects front marker and rejects rear and empty space`() {
        val scene = WorldTimeScene()
        scene.prepare(WidgetSceneContext(1f, 1_000, 1_000, {}))
        val matrix = FloatArray(16); matrix[0] = 1f; matrix[5] = 1f; matrix[10] = 1f; matrix[15] = 1f
        scene.updateProjection(WidgetProjection().also { it.update(matrix, 1_000, 1_000) })
        val london = WorldTimeCities.defaults.first { it.id == "london" }
        val p = WorldTimeCoordinates.latLon(london.latitude, london.longitude, 1.06f)
        val x = (p.x + 1f) * 500f; val y = (1f - p.y) * 500f
        assertEquals("london", scene.hitTest(x, y))
        assertNull(scene.hitTest(10f, 10f))
        scene.drag(1_500f) // 180 degrees: London's former screen point is now on the rear hemisphere.
        assertNull(scene.hitTest(x, y))
    }
    @Test fun `clock schedule targets the next minute boundary`() {
        assertEquals(60_000L, WorldTimeClockSchedule.delayToNextMinute(10L * 60L * 60_000L + 35L * 60_000L))
        assertEquals(12_500L, WorldTimeClockSchedule.delayToNextMinute(10L * 60L * 60_000L + 35L * 60_000L + 47_500L))
        assertEquals(1L, WorldTimeClockSchedule.delayToNextMinute(10L * 60L * 60_000L + 35L * 60_000L + 59_999L))
    }
}
