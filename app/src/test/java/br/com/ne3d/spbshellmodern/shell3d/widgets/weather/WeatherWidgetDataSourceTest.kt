package br.com.ne3d.spbshellmodern.shell3d.widgets.weather

import br.com.ne3d.spbshellmodern.model.WeatherDay
import br.com.ne3d.spbshellmodern.model.WeatherInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherWidgetDataSourceTest {
    @Test fun `weather is mapped and forecast is limited`() {
        val source = WeatherWidgetDataSource()
        val snapshot = source.publishWeather(WeatherInfo(25, "Limpo", 0, List(7) { WeatherDay("2026-09-${it + 10}", 16, 29, if (it == 0) 63 else 3) }, "São Paulo"), 42L)
        assertTrue(snapshot.available); assertEquals("São Paulo", snapshot.city); assertEquals(25, snapshot.temperature)
        assertEquals(WeatherCondition.CLEAR, snapshot.condition); assertEquals(5, snapshot.forecast.size)
        assertEquals(WeatherCondition.RAIN, snapshot.forecast.first().condition); assertEquals(42L, snapshot.updatedAtMillis)
    }
    @Test fun `null weather publishes safe latest snapshot`() {
        val source = WeatherWidgetDataSource(); val first = source.publishWeather(null, 1L); val second = source.publishWeather(null, 2L)
        assertFalse(second.available); assertNull(second.temperature); assertEquals(WeatherCondition.UNKNOWN, second.condition)
        assertTrue(second.forecast.isEmpty()); assertTrue(second.revision > first.revision); assertEquals(second, source.latest())
    }
}
