package br.com.ne3d.spbshellmodern.shell3d.widgets.weather

import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot

/** Immutable, GL-safe weather payload. */
data class WeatherForecastValue(
    val date: String,
    val min: Int,
    val max: Int,
    val code: Int,
    val condition: WeatherCondition
)

data class WeatherSnapshot(
    override val revision: Long,
    val available: Boolean,
    val city: String,
    val temperature: Int?,
    val description: String,
    val code: Int?,
    val condition: WeatherCondition,
    val forecast: List<WeatherForecastValue>,
    val updatedAtMillis: Long
) : WidgetSnapshot
