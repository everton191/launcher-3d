package br.com.ne3d.spbshellmodern.shell3d.widgets.weather

import br.com.ne3d.spbshellmodern.model.WeatherInfo
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.LatestWidgetDataSource
import java.util.concurrent.atomic.AtomicLong

/** Converts repository models into immutable snapshots without exposing repository work to GL. */
class WeatherWidgetDataSource : LatestWidgetDataSource<WeatherSnapshot>() {
    private val revision = AtomicLong()

    fun publishWeather(weather: WeatherInfo?, nowMillis: Long = System.currentTimeMillis()): WeatherSnapshot {
        val snapshot = weather?.let { info ->
            WeatherSnapshot(
                revision = revision.incrementAndGet(),
                available = true,
                city = info.city,
                temperature = info.temperature,
                description = info.description,
                code = info.code,
                condition = WeatherConditionMapper.fromCode(info.code),
                forecast = info.daily.take(5).map { day ->
                    WeatherForecastValue(day.date, day.min, day.max, day.code, WeatherConditionMapper.fromCode(day.code))
                },
                updatedAtMillis = nowMillis
            )
        } ?: WeatherSnapshot(
            revision = revision.incrementAndGet(), available = false, city = "", temperature = null,
            description = "", code = null, condition = WeatherCondition.UNKNOWN, forecast = emptyList(), updatedAtMillis = nowMillis
        )
        publish(snapshot)
        return snapshot
    }
}
