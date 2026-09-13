package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import br.com.ne3d.spbshellmodern.shell3d.widgets.data.LatestWidgetDataSource
import br.com.ne3d.spbshellmodern.shell3d.widgets.data.WidgetSnapshot
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class WorldTimeCityValue(val id: String, val time: String, val offset: String)
data class WorldTimeSnapshot(override val revision: Long, val selectedCityId: String, val reference: Instant, val values: List<WorldTimeCityValue>) : WidgetSnapshot {
    fun selectedValue(): WorldTimeCityValue? = values.firstOrNull { it.id == selectedCityId }
}
class WorldTimeDataSource(private val cities: List<WorldTimeCity> = WorldTimeCities.defaults, private val locale: Locale = Locale.getDefault()) : LatestWidgetDataSource<WorldTimeSnapshot>() {
    private val revision = AtomicLong(); private var selected = cities.first().id
    fun select(cityId: String, now: Instant = Instant.now()): Boolean { if(cities.none { it.id == cityId }) return false; selected=cityId; publishNow(now); return true }
    fun publishNow(now: Instant = Instant.now()) { val format=java.time.format.DateTimeFormatter.ofPattern("HH:mm",locale); val values=cities.map { city -> val time=ZonedDateTime.ofInstant(now,city.zoneId); WorldTimeCityValue(city.id,time.format(format),WorldTimeFormatter.gmtOffset(time.offset.id)) }; publish(WorldTimeSnapshot(revision.incrementAndGet(),selected,now,values)) }
}

object WorldTimeFormatter {
    fun gmtOffset(zoneOffset: String): String = if (zoneOffset == "Z") "GMT" else "GMT$zoneOffset"
}
