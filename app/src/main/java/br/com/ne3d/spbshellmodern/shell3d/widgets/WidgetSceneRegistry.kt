package br.com.ne3d.spbshellmodern.shell3d.widgets

import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WidgetIds
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WorldTimeScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.weather.WeatherScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.weather.WeatherWidgetIds
import br.com.ne3d.spbshellmodern.shell3d.widgets.music.MusicScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.CalendarScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.PhotosScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.ContactsScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.system.NotificationScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.system.SystemScene

/** Registry maps a future widget type to a scene factory without teaching the renderer widget names. */
class WidgetSceneRegistry {
    private val factories = HashMap<String, () -> WidgetScene>()
    fun register(id: String, factory: () -> WidgetScene) { require(id !in factories) { "Widget scene already registered: $id" }; factories[id]=factory }
    fun create(id: String): WidgetScene? = factories[id]?.invoke()
    companion object {
        const val DEBUG_SCENE = "debug-widget-scene"
        const val MUSIC = "music"; const val CALENDAR = "calendar"; const val PHOTOS = "photos"; const val CONTACTS = "contacts"; const val NOTIFICATIONS = "notifications"; const val SYSTEM = "system"
        fun production(): WidgetSceneRegistry = WidgetSceneRegistry().apply {
            register(WidgetIds.WORLD_TIME) { WorldTimeScene() }
            register(WeatherWidgetIds.WEATHER) { WeatherScene() }
            register(MUSIC) { MusicScene() }
            register(CALENDAR) { CalendarScene() }
            register(PHOTOS) { PhotosScene() }
            register(CONTACTS) { ContactsScene() }
            register(NOTIFICATIONS) { NotificationScene() }
            register(SYSTEM) { SystemScene() }
        }
    }
}
