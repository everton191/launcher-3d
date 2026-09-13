package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

import java.time.ZoneId

data class WorldTimeCity(val id: String, val displayName: String, val zoneId: ZoneId, val latitude: Float, val longitude: Float, val countryCode: String)
object WorldTimeCities {
    val defaults = listOf(
        WorldTimeCity("sao-paulo", "São Paulo", ZoneId.of("America/Sao_Paulo"), -23.55f, -46.63f, "BR"),
        WorldTimeCity("new-york", "Nova York", ZoneId.of("America/New_York"), 40.71f, -74.01f, "US"),
        WorldTimeCity("london", "Londres", ZoneId.of("Europe/London"), 51.51f, -0.13f, "GB"),
        WorldTimeCity("paris", "Paris", ZoneId.of("Europe/Paris"), 48.86f, 2.35f, "FR"),
        WorldTimeCity("tokyo", "Tóquio", ZoneId.of("Asia/Tokyo"), 35.68f, 139.69f, "JP"),
        WorldTimeCity("sydney", "Sydney", ZoneId.of("Australia/Sydney"), -33.87f, 151.21f, "AU"),
    )
}
