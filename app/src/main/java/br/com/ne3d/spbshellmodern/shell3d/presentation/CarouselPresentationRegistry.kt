package br.com.ne3d.spbshellmodern.shell3d.presentation

/** Maps a carousel panel id to its live presentation.
 *
 * Panel ids look like `world_time-<uuid>` or bare template names (`weather`,
 * `home`). Matchers live in exactly one place; the state machine only asks
 * for an implementation and falls back to [NoPresentation]. Simplifica 3D
 * registers its own modules instead of the launcher set below.
 */
class CarouselPresentationRegistry {
    private data class Entry(val matches: (String) -> Boolean, val factory: () -> PanelPresentation)

    private val entries = ArrayList<Entry>()

    fun register(matches: (String) -> Boolean, factory: () -> PanelPresentation) {
        entries.add(Entry(matches, factory))
    }

    fun presentationFor(panelId: String): PanelPresentation {
        for (entry in entries) {
            if (entry.matches(panelId)) return entry.factory()
        }
        return NoPresentation
    }

    fun clear() {
        entries.clear()
    }
}

private fun normalizedPanelId(panelId: String): String =
    panelId.lowercase().replace('-', '_')

/** Launcher catalog wiring. Kept isolated so other shells can register their own set. */
fun registerLauncherPresentations(registry: CarouselPresentationRegistry) {
    registry.register({ normalizedPanelId(it).startsWith("world_time") }) { WorldTimePresentation() }
    registry.register({ normalizedPanelId(it).startsWith("moon") }) { MoonPresentation() }
    registry.register({ normalizedPanelId(it).startsWith("weather") }) { WeatherPresentation() }
    registry.register({
        val id = normalizedPanelId(it)
        id.startsWith("agenda") || id.startsWith("calendar")
    }) { CalendarPresentation() }
    registry.register({ normalizedPanelId(it).startsWith("media") }) { MediaPresentation() }
    registry.register({
        val id = normalizedPanelId(it)
        id.startsWith("indicators") || id == "system" || id.startsWith("system_")
    }) { SystemPresentation() }
}
