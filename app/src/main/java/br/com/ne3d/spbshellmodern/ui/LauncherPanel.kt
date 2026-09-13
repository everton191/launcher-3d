package br.com.ne3d.spbshellmodern.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import br.com.ne3d.spbshellmodern.model.*
import br.com.ne3d.spbshellmodern.shell3d.ShellPrototypeScreen
import br.com.ne3d.spbshellmodern.shell3d.WidgetSceneType

@Composable
fun LauncherPanel(
    panel: LauncherPanel,
    apps: List<AppItem>,
    photos: List<android.graphics.Bitmap> = emptyList(),
    weather: WeatherInfo? = null,
    weatherCity: String = "",
    onWeatherCityChange: (String) -> Unit = {},
    onRequestPhotos: () -> Unit = {},
    onRefreshWeather: (String) -> Unit = {},
    carouselPreview: Boolean = false,
    renderState: WidgetRenderState = if (carouselPreview) WidgetRenderState.CAROUSEL_PREVIEW else WidgetRenderState.NORMAL_2D,
    presentation: WidgetPresentation = WidgetPresentation.FULL_PANEL,
    interactive: Boolean = true,
    onEditItems: () -> Unit = {},
    itemContent: (@Composable (LauncherPanel) -> Unit)? = null,
    workspacePanel: Boolean = false,
    onLaunch: (AppItem) -> Unit
) {
    val compact = presentation in listOf(WidgetPresentation.ICON, WidgetPresentation.COMPACT, WidgetPresentation.ROW)
    val shape = RoundedCornerShape(1.dp)
    val panelTone = Color.hsv(panel.hue.coerceIn(0f, 360f), .23f, .4f)
    val enabled = interactive && !carouselPreview && renderState != WidgetRenderState.EDIT
    val magicObject = renderState == WidgetRenderState.MAGIC_ANIMATION && panel.type == PanelType.MOON
    val framed = (!workspacePanel || carouselPreview) && !magicObject
    val frame = if (framed) Modifier
        .background(Brush.verticalGradient(listOf(panelTone.copy(alpha = .94f), Color(0xED071414))))
        .border(if (workspacePanel) 3.dp else 1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = .32f), panelTone, Color.Black.copy(alpha = .8f))), shape)
        .padding(if (workspacePanel) 4.dp else 1.dp) else Modifier
    Column(Modifier.fillMaxSize().clip(shape).then(frame)) {
        if (!compact && !workspacePanel) {
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(panelTone.copy(alpha = .8f), Color(0x33202328)))).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(panel.title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Normal,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (panel.type == PanelType.HOME && enabled) Text("＋", Modifier.clickable(onClick = onEditItems).padding(horizontal = 8.dp), color = SpbSilver, fontSize = 21.sp)
            }
            HorizontalDivider(color = Color.White.copy(alpha = .13f))
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(if (workspacePanel) 5.dp else if (compact) 7.dp else 10.dp)) {
            if (itemContent != null) itemContent(panel)
            else when (panel.type) {
                PanelType.APPS -> AppsGrid(apps, enabled, onLaunch)
                PanelType.HOME -> Column(Modifier.fillMaxSize()) {
                    SpbHomeClockWeather(weather, renderState)
                    HorizontalDivider(color = Color.White.copy(alpha = .25f))
                    Box(Modifier.fillMaxWidth().weight(1f).padding(top = 18.dp)) {
                        val shortcuts = apps.filter { app -> listOf("câmera", "camera", "calculadora", "agenda", "configurações", "telefone", "mensagen", "fotos", "chrome", "play store", "relógio", "arquivos", "contatos").any { app.label.lowercase().contains(it) } }.take(12)
                        AppsGrid(shortcuts, enabled, onLaunch)
                    }
                }
                PanelType.CLOCK -> SpbClockCard(panel.id, presentation, renderState, enabled)
                PanelType.WORLD_TIME -> if (
                    enabled && !carouselPreview && presentation == WidgetPresentation.FULL_PANEL &&
                    renderState == WidgetRenderState.NORMAL_2D
                ) {
                    ShellPrototypeScreen(
                        panels = emptyList(),
                        includeRealPanels = false,
                        exitOnTap = false,
                        worldTimeWidgetScene = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // The carousel keeps a lightweight Compose preview; the open panel owns the GL scene.
                    SpbWorldTimeCard(panel.id, presentation, renderState, enabled)
                }
                PanelType.MOON -> SpbMoonCard(presentation, renderState, enabled)
                PanelType.GALLERY, PanelType.PICTURE -> SpbPhotoCard(photos, panel.type, renderState, presentation, enabled, onRequestPhotos)
                PanelType.WEATHER -> if (enabled && !carouselPreview && presentation == WidgetPresentation.FULL_PANEL && renderState == WidgetRenderState.NORMAL_2D) {
                    ShellPrototypeScreen(panels = emptyList(), includeRealPanels = false, exitOnTap = false, widgetSceneType = WidgetSceneType.WEATHER, weatherInfo = weather, modifier = Modifier.fillMaxSize())
                } else SpbWeatherCard(weather, weatherCity, onWeatherCityChange, onRefreshWeather, false, renderState, presentation, enabled)
                PanelType.WEATHER_CURRENT, PanelType.WEATHER_GRAPH -> SpbWeatherCard(weather, weatherCity, onWeatherCityChange, onRefreshWeather, panel.type == PanelType.WEATHER_GRAPH, renderState, presentation, enabled)
                PanelType.MEDIA -> if (enabled && !carouselPreview && presentation == WidgetPresentation.FULL_PANEL && renderState == WidgetRenderState.NORMAL_2D) {
                    ShellPrototypeScreen(panels = emptyList(), includeRealPanels = false, exitOnTap = false, widgetSceneType = WidgetSceneType.MUSIC, modifier = Modifier.fillMaxSize())
                } else SpbUtilityWidget(panel.type, apps, enabled, onLaunch, panelId = panel.id)
                PanelType.CALENDAR -> if (enabled && !carouselPreview && presentation == WidgetPresentation.FULL_PANEL && renderState == WidgetRenderState.NORMAL_2D) {
                    ShellPrototypeScreen(panels = emptyList(), includeRealPanels = false, exitOnTap = false, widgetSceneType = WidgetSceneType.CALENDAR, modifier = Modifier.fillMaxSize())
                } else SpbUtilityWidget(panel.type, apps, enabled, onLaunch, panelId = panel.id)
                PanelType.CONTACT -> if (enabled && !carouselPreview && presentation == WidgetPresentation.FULL_PANEL && renderState == WidgetRenderState.NORMAL_2D) {
                    ShellPrototypeScreen(panels = emptyList(), includeRealPanels = false, exitOnTap = false, widgetSceneType = WidgetSceneType.CONTACTS, modifier = Modifier.fillMaxSize())
                } else SpbUtilityWidget(panel.type, apps, enabled, onLaunch, panelId = panel.id)
                PanelType.PHOTOS -> if (enabled && !carouselPreview && presentation == WidgetPresentation.FULL_PANEL && renderState == WidgetRenderState.NORMAL_2D) {
                    ShellPrototypeScreen(panels = emptyList(), includeRealPanels = false, exitOnTap = false, widgetSceneType = WidgetSceneType.PHOTOS, modifier = Modifier.fillMaxSize())
                } else SpbPhotoCard(photos, panel.type, renderState, presentation, enabled, onRequestPhotos)
                PanelType.ANDROID_WIDGET -> SpbAndroidWidget(panel.id, enabled)
                else -> SpbUtilityWidget(panel.type, apps, enabled, onLaunch, panelId = panel.id)
            }
        }
    }
}

@Composable
private fun AppsGrid(apps: List<AppItem>, interactive: Boolean, onLaunch: (AppItem) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(68.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(apps, key = { "${it.packageName}/${it.className}" }) { app ->
            val bitmap = remember(app.icon) { app.icon?.toBitmap(112, 112)?.asImageBitmap() }
            Column(Modifier.fillMaxWidth().clickable(enabled = interactive) { onLaunch(app) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) Image(bitmap, null, Modifier.size(44.dp))
                else Text("▣", fontSize = 32.sp, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(app.label, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    fontSize = 12.sp, lineHeight = 15.sp, color = Color.White)
            }
        }
    }
}

