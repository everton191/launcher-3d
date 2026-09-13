package br.com.ne3d.spbshellmodern.model

import android.graphics.drawable.Drawable
import java.util.UUID

data class AppItem(
    val label: String,
    val packageName: String,
    val className: String,
    val icon: Drawable?
)

data class WeatherDay(val date: String, val min: Int, val max: Int, val code: Int)

data class WeatherInfo(
    val temperature: Int,
    val description: String,
    val code: Int = 0,
    val daily: List<WeatherDay> = emptyList(),
    val city: String = ""
)

enum class LauncherMode { NORMAL, CAROUSEL, ORGANIZE, EDIT_ITEMS }
enum class WidgetPresentation { ICON, COMPACT, ROW, EXPANDED, FULL_PANEL, THREE_D }
enum class WidgetRenderState { NORMAL_2D, ACTIVE_3D, CAROUSEL_PREVIEW, MAGIC_ANIMATION, EDIT }

enum class PanelType {
    HOME, APPS, FAVORITES, AGENDA, PHOTOS, WEATHER,
    CLOCK, WORLD_TIME, MOON, GALLERY, BIRTHDAYS, MESSAGES,
    WEATHER_CURRENT, WEATHER_GRAPH, BACKLIGHT, NEWS, LAST_CALL,
    CALENDAR, CONTACT, OPERATOR, SEARCH, INDICATORS, PICTURE,
    BATTERY, WIRELESS, MEDIA, FOLDER, BOOKMARK, TRAFFIC, SOCIAL,
    AFISHA, TV, NOTES, CALCULATOR, ANDROID_WIDGET
}

data class ShellItem(
    val id: String,
    val type: PanelType,
    val presentation: WidgetPresentation = WidgetPresentation.COMPACT,
    val appPackage: String? = null,
    val appClass: String? = null,
    val title: String = "",
    val children: List<ShellItem> = emptyList()
)

val spbWidgetTypes = listOf(
    PanelType.CLOCK, PanelType.WORLD_TIME, PanelType.MOON, PanelType.PHOTOS, PanelType.GALLERY,
    PanelType.BIRTHDAYS, PanelType.MESSAGES, PanelType.WEATHER,
    PanelType.WEATHER_CURRENT, PanelType.WEATHER_GRAPH, PanelType.BACKLIGHT,
    PanelType.NEWS, PanelType.LAST_CALL, PanelType.CALENDAR, PanelType.CONTACT,
    PanelType.OPERATOR, PanelType.SEARCH, PanelType.INDICATORS, PanelType.PICTURE,
    PanelType.BATTERY, PanelType.WIRELESS, PanelType.MEDIA, PanelType.FOLDER,
    PanelType.BOOKMARK, PanelType.TRAFFIC, PanelType.SOCIAL, PanelType.AFISHA,
    PanelType.TV, PanelType.NOTES, PanelType.CALCULATOR
)

const val MAX_PANELS = 16

data class LauncherPanel(
    val id: String,
    val title: String,
    val type: PanelType,
    val removable: Boolean = true,
    val hue: Float = 180f,
    val widgets: List<ShellItem> = emptyList(),
    val customized: Boolean = false
)

data class ShellWorkspace(
    val panels: List<LauncherPanel> = initialPanels(),
    val activePanelId: String = "home",
    val homePanelId: String = "home",
    val storedPanels: List<LauncherPanel> = emptyList(),
    val tray: List<ShellItem> = emptyList(),
    val dock: List<ShellItem> = emptyList()
)

fun freshPanelId(type: PanelType): String = "${type.name.lowercase()}-${UUID.randomUUID()}"

fun initialPanels() = listOf(
    LauncherPanel("home", "Home", PanelType.HOME, false),
    LauncherPanel("apps", "Apps", PanelType.APPS, false),
    LauncherPanel("favorites", "Favoritos", PanelType.FAVORITES),
    LauncherPanel("agenda", "Agenda", PanelType.AGENDA),
    LauncherPanel("photos", "Fotos", PanelType.PHOTOS),
    LauncherPanel("weather", "Clima", PanelType.WEATHER)
)

fun panelTemplate(type: PanelType, id: String = type.name.lowercase()) = LauncherPanel(
    id = id,
    title = when (type) {
        PanelType.HOME -> "Home"
        PanelType.APPS -> "Apps"
        PanelType.FAVORITES -> "Favoritos"
        PanelType.AGENDA -> "Agenda"
        PanelType.PHOTOS -> "Fotos"
        PanelType.WEATHER -> "Clima"
        PanelType.CLOCK -> "Relógio"
        PanelType.WORLD_TIME -> "Horário mundial"
        PanelType.MOON -> "Fases da Lua"
        PanelType.GALLERY -> "Galeria"
        PanelType.BIRTHDAYS -> "Aniversários"
        PanelType.MESSAGES -> "Mensagens"
        PanelType.WEATHER_CURRENT -> "Clima atual"
        PanelType.WEATHER_GRAPH -> "Previsão do clima"
        PanelType.BACKLIGHT -> "Brilho"
        PanelType.NEWS -> "Notícias"
        PanelType.LAST_CALL -> "Última chamada"
        PanelType.CALENDAR -> "Calendário"
        PanelType.CONTACT -> "Contato"
        PanelType.OPERATOR -> "Operadora"
        PanelType.SEARCH -> "Pesquisa"
        PanelType.INDICATORS -> "Indicadores"
        PanelType.PICTURE -> "Imagem"
        PanelType.BATTERY -> "Bateria"
        PanelType.WIRELESS -> "Conexões"
        PanelType.MEDIA -> "Reprodutor"
        PanelType.FOLDER -> "Pasta"
        PanelType.BOOKMARK -> "Favorito da Web"
        PanelType.TRAFFIC -> "Trânsito"
        PanelType.SOCIAL -> "Redes sociais"
        PanelType.AFISHA -> "Eventos"
        PanelType.TV -> "TV"
        PanelType.NOTES -> "Notas"
        PanelType.CALCULATOR -> "Calculadora"
        PanelType.ANDROID_WIDGET -> "Widget Android"
    },
    type = type,
    removable = type != PanelType.HOME && type != PanelType.APPS
)

