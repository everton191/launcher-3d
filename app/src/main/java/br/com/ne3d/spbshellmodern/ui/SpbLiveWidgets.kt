package br.com.ne3d.spbshellmodern.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import br.com.ne3d.spbshellmodern.R
import br.com.ne3d.spbshellmodern.model.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

internal val SpbSilver = Color(0xFFD6D8DE)

@Composable
internal fun SpbHomeClockWeather(weather: WeatherInfo?, state: WidgetRenderState) {
    val now = rememberSpbTime(state)
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(112.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(Modifier.weight(1.55f)) {
                Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White,
                    fontSize = (maxWidth.value / 3.3f).sp, lineHeight = (maxWidth.value / 3f).sp,
                    fontWeight = FontWeight.Light, maxLines = 1)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Image(painterResource(weatherDrawable(weather?.code ?: 2)), weather?.description ?: "Clima",
                    Modifier.size(82.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(now.format(DateTimeFormatter.ofPattern("EEE, d MMMM")), Modifier.weight(1.55f),
                color = Color.White, fontSize = 13.sp, lineHeight = 17.sp)
            Text(weather?.let { "${it.temperature}°  ${it.city}" } ?: "Escolha sua cidade no Clima",
                Modifier.weight(1f), color = SpbSilver, fontSize = 12.sp, lineHeight = 16.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun rememberSpbTime(state: WidgetRenderState): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    val resumed = rememberSpbResumed()
    LaunchedEffect(resumed, state) {
        if (resumed) while (true) {
            now = ZonedDateTime.now()
            delay(if (state == WidgetRenderState.CAROUSEL_PREVIEW) 30_000 else 1_000)
        }
    }
    return now
}

@Composable
internal fun SpbAnalogClock(now: ZonedDateTime, modifier: Modifier = Modifier, seconds: Boolean = true) {
    val face = ImageBitmap.imageResource(R.drawable.spb_time_clock)
    val hands = ImageBitmap.imageResource(R.drawable.spb_time_hands)
    Canvas(modifier.aspectRatio(1f)) {
        val diameter = min(size.width, size.height)
        val origin = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        drawImage(face, dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()), dstSize = IntSize(diameter.toInt(), diameter.toInt()))
        val center = origin + Offset(diameter / 2, diameter / 2)
        val scale = diameter / 320f
        fun hand(sourceY: Int, sourceWidth: Int, degrees: Float) {
            rotate(degrees - 90f, center) {
                drawImage(hands, srcOffset = IntOffset(0, sourceY), srcSize = IntSize(sourceWidth, 21),
                    dstOffset = IntOffset((center.x - 11f * scale).toInt(), (center.y - 11f * scale).toInt()),
                    dstSize = IntSize((sourceWidth * scale).toInt(), (21 * scale).toInt()))
            }
        }
        hand(0, 93, ((now.hour % 12) + now.minute / 60f) * 30f)
        hand(21, 136, (now.minute + now.second / 60f) * 6f)
        if (seconds) {
            val radians = Math.toRadians(now.second * 6.0 - 90.0)
            drawLine(Color(0xFFD94330), center,
                center + Offset(cos(radians).toFloat(), sin(radians).toFloat()) * diameter * .36f,
                strokeWidth = max(1f, diameter / 120f), cap = StrokeCap.Round)
            drawCircle(SpbSilver, diameter * .018f, center)
        }
    }
}

@Composable
internal fun SpbClockCard(id: String, presentation: WidgetPresentation, state: WidgetRenderState, interactive: Boolean) {
    val now = rememberSpbTime(state)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("spb_widget_settings", 0) }
    var skin by remember(id) { mutableStateOf(prefs.getString("clock_$id", "analog") ?: "analog") }
    var settings by remember { mutableStateOf(false) }
    val compact = presentation in listOf(WidgetPresentation.ICON, WidgetPresentation.COMPACT, WidgetPresentation.ROW)
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val clockFooter = if (interactive) 140.dp else 92.dp
    val clockSize = minOf(maxWidth * .80f, (maxHeight - clockFooter).coerceAtLeast(0.dp), 270.dp)
    val wideClockSize = minOf(maxWidth * .45f, (maxHeight - if (interactive) 48.dp else 0.dp).coerceAtLeast(0.dp))
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (compact || skin == "compact") {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = if (compact) 30.sp else 64.sp, fontWeight = FontWeight.Light, color = Color.White)
            Text(now.format(DateTimeFormatter.ofPattern("EEE, dd MMM")), fontSize = 12.sp, color = SpbSilver)
        } else if (skin == "wide") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SpbAnalogClock(now, Modifier.size(wideClockSize), state != WidgetRenderState.CAROUSEL_PREVIEW)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 30.sp, color = Color.White)
                    Text(now.format(DateTimeFormatter.ofPattern("EEEE\ndd MMMM")), color = SpbSilver, fontSize = 13.sp)
                }
            }
        } else {
            SpbAnalogClock(now, Modifier.size(clockSize), state != WidgetRenderState.CAROUSEL_PREVIEW)
            Spacer(Modifier.height(18.dp))
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM")), color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")), color = SpbSilver, fontSize = 20.sp)
        }
        if (interactive && !compact) TextButton(onClick = { settings = true }) { Text("Aparência", color = SpbSilver) }
    }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Relógio") }, text = {
        Column {
            listOf("analog" to "Analógico", "compact" to "Digital", "wide" to "Relógio e data").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().clickable { skin = value; prefs.edit().putString("clock_$id", value).apply(); settings = false }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = skin == value, onClick = null); Text(label)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Fechar") } })
}

private val worldCities = listOf(
    "Fortaleza" to "America/Fortaleza", "São Paulo" to "America/Sao_Paulo", "Manaus" to "America/Manaus",
    "Nova York" to "America/New_York", "Los Angeles" to "America/Los_Angeles", "Londres" to "Europe/London",
    "Paris" to "Europe/Paris", "Lisboa" to "Europe/Lisbon", "Tóquio" to "Asia/Tokyo", "Sydney" to "Australia/Sydney"
)

@Composable
internal fun SpbWorldTimeCard(id: String, presentation: WidgetPresentation, state: WidgetRenderState, interactive: Boolean) {
    val now = rememberSpbTime(state)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("spb_widget_settings", 0) }
    var cities by remember(id) {
        mutableStateOf((0..2).map { slot ->
            val zone = prefs.getString("city_${id}_$slot", listOf("America/Fortaleza", "Europe/London", "Asia/Tokyo")[slot])
            worldCities.firstOrNull { it.second == zone } ?: worldCities[slot]
        })
    }
    var editing by remember { mutableIntStateOf(-1) }
    val compact = presentation in listOf(WidgetPresentation.ICON, WidgetPresentation.COMPACT, WidgetPresentation.ROW)
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val showDates = !compact && maxHeight >= 280.dp
    val globeSize = minOf(maxWidth * .77f, maxHeight * .43f,
        (maxHeight - if (showDates) 184.dp else 132.dp).coerceAtLeast(0.dp))
    val rowPadding = if (maxHeight < 340.dp) 3.dp else 10.dp
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!compact) {
            Box(Modifier.size(globeSize), contentAlignment = Alignment.Center) {
                SpbOrb(false, longitude = now.hour / 24.0 * 2 * PI, state = state, modifier = Modifier.fillMaxSize(), description = "Globo terrestre")
            }
            Spacer(Modifier.height(12.dp))
        }
        cities.forEachIndexed { slot, city ->
            val time = now.withZoneSameInstant(ZoneId.of(city.second))
            Row(Modifier.fillMaxWidth().clickable(enabled = interactive) { editing = slot }.padding(vertical = if (compact) 2.dp else rowPadding), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(city.first, fontSize = if (compact) 12.sp else 16.sp, color = Color.White, maxLines = 1)
                    if (showDates) Text(time.format(DateTimeFormatter.ofPattern("EEE, dd MMM")), fontSize = 12.sp, color = SpbSilver)
                }
                Text(time.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = if (compact) 16.sp else 26.sp, color = Color.White, fontWeight = FontWeight.Light)
            }
            if (slot < 2) HorizontalDivider(color = Color.White.copy(alpha = .15f))
        }
    }
    }
    if (editing >= 0) AlertDialog(onDismissRequest = { editing = -1 }, title = { Text("Escolher cidade") }, text = {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            worldCities.forEach { city ->
                Text(city.first, Modifier.fillMaxWidth().clickable {
                    cities = cities.toMutableList().also { it[editing] = city }
                    prefs.edit().putString("city_${id}_$editing", city.second).apply()
                    editing = -1
                }.padding(14.dp))
            }
        }
    }, confirmButton = { TextButton(onClick = { editing = -1 }) { Text("Cancelar") } })
}

@Composable
internal fun SpbMoonCard(presentation: WidgetPresentation, state: WidgetRenderState, interactive: Boolean) {
    val now = rememberSpbTime(state)
    var offset by remember { mutableLongStateOf(0L) }
    val date = now.toLocalDate().plusDays(offset)
    val days = Duration.between(Instant.parse("2000-01-06T18:14:00Z"), date.atTime(12, 0).atZone(now.zone).toInstant()).seconds / 86400.0
    val age = ((days % 29.530588853) + 29.530588853) % 29.530588853
    val phase = age / 29.530588853
    val name = when {
        phase < .03 || phase > .97 -> "Lua nova"
        phase < .22 -> "Lua crescente"
        phase < .28 -> "Quarto crescente"
        phase < .47 -> "Gibosa crescente"
        phase < .53 -> "Lua cheia"
        phase < .72 -> "Gibosa minguante"
        phase < .78 -> "Quarto minguante"
        else -> "Lua minguante"
    }
    val compact = presentation in listOf(WidgetPresentation.ICON, WidgetPresentation.COMPACT, WidgetPresentation.ROW)
    if (state == WidgetRenderState.MAGIC_ANIMATION) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val clockSize = minOf(maxWidth * .90f, maxHeight * .74f)
            Box(Modifier.size(clockSize), contentAlignment = Alignment.Center) {
                SpbAnalogClock(now, Modifier.fillMaxSize(), seconds = false)
                val orbit = phase * 2 * PI
                Box(Modifier.size(clockSize * .30f).offset(x = clockSize * (cos(orbit).toFloat() * .40f),
                    y = clockSize * (sin(orbit).toFloat() * .40f))) {
                    SpbOrb(true, phase, longitude = orbit, state = state, modifier = Modifier.fillMaxSize(), description = "$name em $date")
                }
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF02021B)).padding(if (compact) 4.dp else 12.dp)) {
    val orbSize = minOf(maxWidth * if (compact) .60f else .58f, maxHeight * .48f)
    Canvas(Modifier.fillMaxSize()) {
        repeat(42) { index ->
            val x = ((index * 73 + 19) % 307) / 307f
            val y = ((index * 47 + 11) % 281) / 281f
            drawCircle(Color(0xFFB7C7FF).copy(alpha = .22f + (index % 4) * .10f),
                radius = if (index % 6 == 0) 1.2.dp.toPx() else .55.dp.toPx(), center = Offset(size.width * x, size.height * y))
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (!compact) {
            Text(name, Modifier.fillMaxWidth(), fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text("${age.toInt() + 1}º dia lunar · ${date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
                Modifier.fillMaxWidth(), fontSize = 12.sp, lineHeight = 16.sp, color = Color(0xFF989CFF))
            Spacer(Modifier.weight(1f))
        }
        Box(Modifier.size(orbSize * 1.45f), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.spb_moon_glow), null, Modifier.fillMaxSize())
            SpbOrb(true, phase, longitude = phase * .18, state = state, modifier = Modifier.size(orbSize), description = "$name em $date")
        }
        if (compact) Text(name, fontSize = 12.sp, color = Color.White)
        if (!compact) {
            Spacer(Modifier.weight(1f))
            Text("Iluminação ≈ ${((1 - cos(phase * 2 * PI)) * 50).roundToInt()}%", fontSize = 12.sp, color = SpbSilver)
            if (interactive) Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { offset-- }) { Text("‹", fontSize = 30.sp, color = Color.White) }
                TextButton(onClick = { offset = 0 }) { Text("Hoje", color = SpbSilver) }
                TextButton(onClick = { offset++ }) { Text("›", fontSize = 30.sp, color = Color.White) }
            }
        }
    }
    }
}

@Composable
internal fun SpbPhotoCard(photos: List<Bitmap>, type: PanelType, state: WidgetRenderState, presentation: WidgetPresentation, interactive: Boolean, onRequest: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    val count = if (LocalConfiguration.current.smallestScreenWidthDp >= 600) 9 else 7
    val magic = state in listOf(WidgetRenderState.MAGIC_ANIMATION, WidgetRenderState.ACTIVE_3D) && rememberSpbResumed()
    val spread by animateFloatAsState(if (magic) 1f else .12f, tween(1100), label = "gallerySpread")
    val compact = presentation in listOf(WidgetPresentation.ICON, WidgetPresentation.COMPACT, WidgetPresentation.ROW)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (photos.isEmpty()) {
            Text("Suas fotos", fontSize = if (compact) 14.sp else 24.sp, color = Color.White)
            if (interactive) TextButton(onClick = onRequest) { Text("Escolher fotos", color = SpbSilver) }
            else Text("Nenhuma foto selecionada", color = SpbSilver, fontSize = 12.sp)
        } else {
            val index = Math.floorMod(selected, photos.size)
            if (type == PanelType.GALLERY || magic) {
                Box(Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(.95f).clickable(enabled = interactive) { selected = (selected + 1) % photos.size }, contentAlignment = Alignment.Center) {
                    val shown = min(count, photos.size)
                    for (layer in (shown - 1) downTo 0) {
                        val photo = photos[(index + layer) % photos.size]
                        val normalized = if (shown > 1) layer.toFloat() / (shown - 1) else 0f
                        Image(photo.asImageBitmap(), if (layer == 0) "Foto ${index + 1} de ${photos.size}" else null,
                            Modifier.fillMaxSize(.76f).graphicsLayer {
                                rotationZ = (normalized - .45f) * 26f * spread
                                rotationY = normalized * -30f * spread
                                translationX = normalized * size.width * .23f * spread
                                translationY = -normalized * size.height * .15f * spread
                                cameraDistance = 12f * density
                            }.background(Color(0xFFE9E8E0)).padding(5.dp), contentScale = ContentScale.Crop)
                    }
                }
            } else {
                Image(photos[index].asImageBitmap(), "Foto ${index + 1} de ${photos.size}",
                    Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(.85f).border(4.dp, Color(0xFFE9E8E0)).padding(4.dp).clickable(enabled = interactive) { selected = (selected + 1) % photos.size }, contentScale = ContentScale.Crop)
            }
            if (!compact) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                    photos.take(4).forEachIndexed { i, photo -> Image(photo.asImageBitmap(), "Foto ${i + 1}", Modifier.padding(3.dp).size(46.dp).border(1.dp, SpbSilver).clickable(enabled = interactive) { selected = i }, contentScale = ContentScale.Crop) }
                }
                if (interactive) TextButton(onClick = onRequest) { Text("Álbum · ${photos.size} fotos", color = SpbSilver) }
            }
        }
    }
}

private fun weatherDrawable(code: Int): Int = when (code) {
    0 -> R.drawable.spb_weather_2
    1 -> R.drawable.spb_weather_3
    2 -> R.drawable.spb_weather_4
    3 -> R.drawable.spb_weather_5
    45, 48 -> R.drawable.spb_weather_0
    51, 53, 55, 56, 57 -> R.drawable.spb_weather_7
    61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.spb_weather_8
    71, 73, 75, 77, 85, 86 -> R.drawable.spb_weather_10
    95, 96, 99 -> R.drawable.spb_weather_13
    else -> R.drawable.spb_weather_5
}

@Composable
internal fun SpbWeatherCard(weather: WeatherInfo?, city: String, onCity: (String) -> Unit, onRefresh: (String) -> Unit, graph: Boolean, state: WidgetRenderState, presentation: WidgetPresentation, interactive: Boolean) {
    var settings by remember { mutableStateOf(false) }
    var input by remember(city) { mutableStateOf(city) }
    val compactPresentation = presentation in listOf(WidgetPresentation.ICON, WidgetPresentation.COMPACT, WidgetPresentation.ROW)
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // An expanded item is shorter than a full panel. Keep actions reachable there,
    // and fit non-interactive previews without relying on an invisible scroll area.
    val compact = compactPresentation || (!interactive && maxHeight < 300.dp)
    val shortCard = maxHeight < 400.dp
    val graphHeight = minOf(if (graph) 190.dp else 125.dp, maxHeight * if (shortCard) .22f else .30f).coerceAtLeast(40.dp)
    val artworkSize = if (shortCard) 48.dp else if (graph) 80.dp else 124.dp
    val scroll = rememberScrollState()
    val contentModifier = if (interactive && !compact && maxHeight < 480.dp)
        Modifier.fillMaxWidth().verticalScroll(scroll) else Modifier.fillMaxSize()
    Column(contentModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (weather == null) {
            Image(painterResource(R.drawable.spb_weather_3), null, Modifier.size(if (compact) 40.dp else 100.dp))
            Text("Clima", color = Color.White, fontSize = if (compact) 14.sp else 24.sp)
            if (!compact) Text("Escolha uma cidade para ver a previsão", color = SpbSilver, textAlign = TextAlign.Center, fontSize = 14.sp)
            if (interactive) TextButton(onClick = { settings = true }) { Text("Escolher cidade", color = SpbSilver) }
        } else {
            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(weatherDrawable(weather.code)), weather.description, Modifier.size(48.dp))
                    Text("${weather.temperature}°", color = Color.White, fontSize = 34.sp)
                }
            } else {
                Text(weather.city.ifBlank { city }, fontSize = if (shortCard) 15.sp else 19.sp, lineHeight = if (shortCard) 20.sp else 24.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Image(painterResource(weatherDrawable(weather.code)), weather.description, Modifier.size(artworkSize))
                    Text("${weather.temperature}°", fontSize = if (shortCard) 32.sp else if (graph) 50.sp else 68.sp,
                        lineHeight = if (shortCard) 40.sp else 76.sp, fontWeight = FontWeight.Light, color = Color.White)
                }
            }
            Text(weather.description, color = SpbSilver, fontSize = if (compact) 11.sp else if (shortCard) 13.sp else 16.sp,
                lineHeight = if (shortCard) 16.sp else 22.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!compact && weather.daily.isNotEmpty()) {
                Spacer(Modifier.height(if (shortCard) 4.dp else 14.dp))
                SpbWeatherGraph(weather.daily, state, Modifier.fillMaxWidth().height(graphHeight))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    weather.daily.take(5).forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(runCatching { LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE")) }.getOrDefault(day.date.takeLast(5)), color = SpbSilver, fontSize = 10.sp, lineHeight = 12.sp)
                            Image(painterResource(weatherDrawable(day.code)), null, Modifier.size(if (shortCard) 22.dp else 30.dp))
                            if (shortCard) Text("${day.min}°/${day.max}°", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp)
                            else {
                                Text("${day.max}°", color = Color.White, fontSize = 12.sp)
                                Text("${day.min}°", color = SpbSilver, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            if (interactive && !compact) Row {
                TextButton(onClick = { onRefresh(city) }) { Text("Atualizar", color = SpbSilver) }
                TextButton(onClick = { settings = true }) { Text("Cidade", color = SpbSilver) }
            }
        }
    }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Clima da cidade") }, text = {
        OutlinedTextField(input, { input = it }, label = { Text("Cidade") }, singleLine = true)
    }, confirmButton = { TextButton(onClick = { onCity(input.trim()); onRefresh(input.trim()); settings = false }, enabled = input.isNotBlank()) { Text("Atualizar") } }, dismissButton = { TextButton(onClick = { settings = false }) { Text("Cancelar") } })
}

@Composable
private fun SpbWeatherGraph(days: List<WeatherDay>, state: WidgetRenderState, modifier: Modifier) {
    val shown = days.take(5)
    if (shown.isEmpty()) return
    val spatial by animateFloatAsState(if (state in listOf(WidgetRenderState.MAGIC_ANIMATION, WidgetRenderState.ACTIVE_3D)) 1f else .25f, tween(800), label = "weatherDepth")
    Canvas(modifier) {
        val low = shown.minOf { it.min } - 2
        val high = shown.maxOf { it.max } + 2
        val range = (high - low).coerceAtLeast(1)
        val floor = size.height * .88f
        val xInset = size.width * .06f
        val step = (size.width - 2 * xInset) / shown.size
        val depth = Offset(14.dp.toPx() * spatial, -10.dp.toPx() * spatial)
        fun point(index: Int, value: Int) = Offset(xInset + step * (index + .5f), floor - (value - low) / range.toFloat() * size.height * .73f)
        repeat(4) { row ->
            val y = size.height * (.18f + row * .23f)
            drawLine(Color.White.copy(alpha = .13f), Offset(xInset, y), Offset(size.width - xInset, y), 1.dp.toPx())
        }
        shown.forEachIndexed { i, day ->
            val top = point(i, day.max)
            val bottom = point(i, day.min)
            val half = step * .16f
            drawRect(Brush.verticalGradient(listOf(Color(0xFFF5BC60), Color(0xFF3C83B8))), Offset(top.x - half, top.y), Size(half * 2, (bottom.y - top.y).coerceAtLeast(2f)))
            val side = Path().apply { moveTo(top.x + half, top.y); lineTo(top.x + half + depth.x, top.y + depth.y); lineTo(bottom.x + half + depth.x, bottom.y + depth.y); lineTo(bottom.x + half, bottom.y); close() }
            drawPath(side, Color(0xFF6A7689))
            val cap = Path().apply { moveTo(top.x - half, top.y); lineTo(top.x + half, top.y); lineTo(top.x + half + depth.x, top.y + depth.y); lineTo(top.x - half + depth.x, top.y + depth.y); close() }
            drawPath(cap, Color(0xFFFFD58B))
            if (i > 0) drawLine(Color(0xFFFFCE88), point(i - 1, shown[i - 1].max), top, 2.dp.toPx())
            drawCircle(Color.White, 2.dp.toPx(), top)
        }
    }
}
