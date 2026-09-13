package br.com.ne3d.spbshellmodern.shell3d.widgets.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherConditionMapperTest {
    @Test fun mapsWmoCodes() {
        assertEquals(WeatherCondition.CLEAR, WeatherConditionMapper.fromCode(0))
        assertEquals(WeatherCondition.MOSTLY_CLEAR, WeatherConditionMapper.fromCode(1))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherConditionMapper.fromCode(2))
        assertEquals(WeatherCondition.CLOUDY, WeatherConditionMapper.fromCode(3))
        assertEquals(WeatherCondition.FOG, WeatherConditionMapper.fromCode(45))
        assertEquals(WeatherCondition.FOG, WeatherConditionMapper.fromCode(48))
        assertEquals(WeatherCondition.DRIZZLE, WeatherConditionMapper.fromCode(51))
        assertEquals(WeatherCondition.DRIZZLE, WeatherConditionMapper.fromCode(53))
        assertEquals(WeatherCondition.DRIZZLE, WeatherConditionMapper.fromCode(55))
        assertEquals(WeatherCondition.DRIZZLE, WeatherConditionMapper.fromCode(56))
        assertEquals(WeatherCondition.DRIZZLE, WeatherConditionMapper.fromCode(57))
        assertEquals(WeatherCondition.RAIN, WeatherConditionMapper.fromCode(61))
        assertEquals(WeatherCondition.RAIN, WeatherConditionMapper.fromCode(63))
        assertEquals(WeatherCondition.RAIN, WeatherConditionMapper.fromCode(65))
        assertEquals(WeatherCondition.RAIN, WeatherConditionMapper.fromCode(66))
        assertEquals(WeatherCondition.RAIN, WeatherConditionMapper.fromCode(67))
        assertEquals(WeatherCondition.SNOW, WeatherConditionMapper.fromCode(71))
        assertEquals(WeatherCondition.SNOW, WeatherConditionMapper.fromCode(73))
        assertEquals(WeatherCondition.SNOW, WeatherConditionMapper.fromCode(75))
        assertEquals(WeatherCondition.SNOW, WeatherConditionMapper.fromCode(77))
        assertEquals(WeatherCondition.SNOW, WeatherConditionMapper.fromCode(85))
        assertEquals(WeatherCondition.SNOW, WeatherConditionMapper.fromCode(86))
        assertEquals(WeatherCondition.SHOWERS, WeatherConditionMapper.fromCode(80))
        assertEquals(WeatherCondition.SHOWERS, WeatherConditionMapper.fromCode(81))
        assertEquals(WeatherCondition.SHOWERS, WeatherConditionMapper.fromCode(82))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherConditionMapper.fromCode(95))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherConditionMapper.fromCode(96))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherConditionMapper.fromCode(99))
        assertEquals(WeatherCondition.UNKNOWN, WeatherConditionMapper.fromCode(999))
        assertEquals(WeatherCondition.UNKNOWN, WeatherConditionMapper.fromCode(-1))
    }
}
