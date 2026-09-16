package br.com.ne3d.spbshellmodern.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import br.com.ne3d.spbshellmodern.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.ne3d.spbshellmodern.model.AppItem
import br.com.ne3d.spbshellmodern.model.PanelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val SpbPaper = Color(0xFFF2F0E9)
private val SpbInk = Color(0xFF24313A)
private val SpbAccent = Color(0xFF5281A0)
private val SpbPaperColors = lightColorScheme(
    primary = Color(0xFF315F7A), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E5ED), onPrimaryContainer = SpbInk,
    secondary = Color(0xFF425968), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE5E8), onSecondaryContainer = SpbInk,
    background = SpbPaper, onBackground = SpbInk,
    surface = SpbPaper, onSurface = SpbInk,
    surfaceVariant = Color(0xFFE4E4DB), onSurfaceVariant = Color(0xFF49545C),
    outline = Color(0xFF667178), error = Color(0xFF9C3029), onError = Color.White
)

/** Utility cards keep their own state by panel id, separately from the Android widget host. */
@Composable
fun SpbUtilityWidget(
    type: PanelType,
    apps: List<AppItem>,
    interactive: Boolean,
    onLaunch: (AppItem) -> Unit,
    panelId: String = type.name,
    preview: Boolean = false
) {
    // FULL_PANEL owns the real GL scene (MUSIC / SYSTEM). Carousel previews mirror its
    // centered, area-filling composition: no scroll, uniform padding, same components.
    val carouselPreview = preview || !interactive
    if (carouselPreview && (type == PanelType.MEDIA || type == PanelType.INDICATORS)) {
        Column(
            Modifier.fillMaxSize().padding(SpbCarouselPreview.previewPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = SpbCarouselPreview.contentAlignment
        ) {
            when (type) {
                PanelType.MEDIA -> UtilityExternalAction("Música", "Escolha o que ouvir no seu reprodutor.",
                    "Abrir reprodutor", false, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
                    Intent(Intent.ACTION_VIEW).setDataAndType(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio/*"))
                PanelType.INDICATORS -> { UtilityBattery(); UtilityConnections(false) }
                else -> Unit
            }
        }
        return
    }
    val backdrop = if (type == PanelType.NOTES) {
        val cork = ImageBitmap.imageResource(R.drawable.spb_note_cork)
        Modifier.background(remember(cork) { ShaderBrush(ImageShader(cork, TileMode.Repeated, TileMode.Repeated)) })
    } else Modifier
    Column(Modifier.fillMaxSize().then(backdrop).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (type) {
            PanelType.AGENDA, PanelType.CALENDAR -> UtilityCalendar(interactive)
            PanelType.NOTES -> UtilityNotes(panelId, interactive)
            PanelType.CALCULATOR -> UtilityCalculator(interactive)
            PanelType.BATTERY -> UtilityBattery()
            PanelType.INDICATORS -> { UtilityBattery(); UtilityConnections(interactive) }
            PanelType.WIRELESS -> UtilityConnections(interactive)
            PanelType.BACKLIGHT -> UtilityBrightness(interactive)
            PanelType.OPERATOR -> UtilityOperator(interactive)
            PanelType.CONTACT -> UtilityContact(panelId, interactive)
            PanelType.BIRTHDAYS -> UtilityBirthdays(panelId, interactive)
            PanelType.SEARCH -> UtilitySearch(apps, interactive, onLaunch)
            PanelType.NEWS -> UtilityNews(panelId, interactive)
            PanelType.MESSAGES -> UtilityExternalAction("Mensagens", "Abra suas conversas no aplicativo de mensagens.",
                "Abrir mensagens", interactive, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
            PanelType.LAST_CALL -> UtilityExternalAction("Chamadas", "Consulte as chamadas recentes no telefone.",
                "Abrir histórico", interactive, Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI), Intent(Intent.ACTION_DIAL))
            PanelType.MEDIA -> UtilityExternalAction("Música", "Escolha o que ouvir no seu reprodutor.",
                "Abrir reprodutor", interactive, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
                Intent(Intent.ACTION_VIEW).setDataAndType(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio/*"))
            PanelType.TRAFFIC -> UtilityTraffic(panelId, interactive)
            PanelType.BOOKMARK, PanelType.SOCIAL, PanelType.AFISHA, PanelType.TV -> UtilityWebShortcut(type, panelId, interactive)
            PanelType.FAVORITES, PanelType.FOLDER -> UtilityFolder(panelId, apps, interactive, onLaunch)
            else -> Unit // Time, weather, photos and Android host are rendered by their dedicated components.
        }
    }
}

@Composable
private fun Paper(dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    // Material controls read the color scheme, independently from Surface.contentColor.
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color.White, secondaryContainer = Color(0xFF45454C), onSecondaryContainer = Color.White) else SpbPaperColors) {
        Surface(color = if (dark) Color(0xFF202027) else SpbPaper, contentColor = if (dark) Color.White else SpbInk, shape = RoundedCornerShape(1.dp), shadowElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun UtilityCalendar(interactive: Boolean) {
    val context = LocalContext.current
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    val today = LocalDate.now()
    Paper {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = interactive, onClick = { monthText = month.minusMonths(1).toString() }) { Text("‹", fontSize = 24.sp) }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())).replaceFirstChar { it.titlecase() },
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            TextButton(enabled = interactive, onClick = { monthText = month.plusMonths(1).toString() }) { Text("›", fontSize = 24.sp) }
        }
        Row { listOf("D", "S", "T", "Q", "Q", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = SpbAccent) } }
        val offset = month.atDay(1).dayOfWeek.value % 7
        repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
            Row {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - offset + 1
                    val date = if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
                    val highlighted = date == selected
                    Box(Modifier.weight(1f).height(34.dp)
                        .background(if (highlighted) SpbAccent else Color.Transparent, RoundedCornerShape(3.dp))
                        .clickable(enabled = interactive && date != null) { selectedText = requireNotNull(date).toString() }, contentAlignment = Alignment.Center) {
                        Text(date?.dayOfMonth?.toString().orEmpty(), color = if (highlighted) Color.White else if (date == today) Color(0xFFC05143) else SpbInk,
                            fontWeight = if (date == today || highlighted) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = interactive, onClick = {
                val epoch = selected.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                error = openUtilityIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$epoch")))
            }) { Text("Agenda") }
            TextButton(enabled = interactive, onClick = {
                val epoch = selected.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                error = openUtilityIntent(context, Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, epoch)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, epoch + 3_600_000L))
            }) { Text("Novo evento") }
            TextButton(enabled = interactive, onClick = { monthText = YearMonth.now().toString(); selectedText = LocalDate.now().toString() }) { Text("Hoje") }
        }
        error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun UtilityNotes(panelId: String, interactive: Boolean) {
    val prefs = rememberUtilityPreferences()
    var note by remember(panelId) { mutableStateOf(prefs.getString("note.$panelId", "").orEmpty()) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    MaterialTheme(colorScheme = SpbPaperColors) {
        Surface(modifier = Modifier.padding(30.dp).graphicsLayer { rotationZ = -3f }, color = Color(0xFFFFF0B0), contentColor = SpbInk, shape = RoundedCornerShape(1.dp), shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 220.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("NOTAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(note.ifBlank { "Toque para escrever uma nota." }, Modifier.fillMaxWidth(), fontSize = 18.sp, maxLines = 14, overflow = TextOverflow.Ellipsis)
                HorizontalDivider(color = Color(0xFFD8C879))
                TextButton(enabled = interactive, onClick = { draft = note; editing = true }) { Text(if (note.isBlank()) "Escrever" else "Editar") }
            }
        }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Nota") },
        text = { OutlinedTextField(draft, { draft = it.take(8_000) }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), label = { Text("Escreva aqui") }) },
        confirmButton = { TextButton(onClick = { note = draft; prefs.edit().putString("note.$panelId", note).apply(); editing = false }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } })
}

@Composable
private fun UtilityCalculator(interactive: Boolean) {
    var display by rememberSaveable { mutableStateOf("0") }
    var operandText by rememberSaveable { mutableStateOf("") }
    var operator by rememberSaveable { mutableStateOf("") }
    var replace by rememberSaveable { mutableStateOf(true) }
    fun calculate() {
        val left = operandText.toDoubleOrNull() ?: return
        val right = display.toDoubleOrNull() ?: return
        display = SpbCalculator.calculate(left, right, operator)
        operandText = ""; operator = ""; replace = true
    }
    Paper(dark = true) {
        Text(operator, Modifier.fillMaxWidth(), color = SpbAccent, textAlign = TextAlign.End)
        Text(display.replace('.', ','), Modifier.fillMaxWidth().background(Color(0xFF34343B)).padding(12.dp),
            fontSize = 30.sp, textAlign = TextAlign.End, maxLines = 2)
        listOf(listOf("C", "±", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"), listOf("⌫", "0", ",", "=")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    FilledTonalButton(enabled = interactive, onClick = {
                        when (key) {
                            "C" -> { display = "0"; operandText = ""; operator = ""; replace = true }
                            "±" -> { display.toDoubleOrNull()?.let { display = SpbCalculator.format(-it) } }
                            "%" -> { display.toDoubleOrNull()?.let { display = SpbCalculator.format(it / 100) } }
                            "⌫" -> { display = display.dropLast(1).takeUnless { it.isEmpty() || it == "-" } ?: "0" }
                            "=" -> calculate()
                            "+", "−", "×", "÷" -> {
                                if (operator.isNotEmpty() && !replace) calculate()
                                if (display.toDoubleOrNull() != null) { operandText = display; operator = key; replace = true }
                            }
                            "," -> { if (replace) { display = "0."; replace = false } else if (!display.contains('.')) display += "." }
                            else -> { if (replace || display == "0" || display.toDoubleOrNull() == null) { display = key; replace = false }
                                else if (display.length < 14) display += key }
                        }
                    }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(4.dp), contentPadding = PaddingValues(0.dp)) { Text(key, fontSize = 19.sp) }
                }
            }
        }
    }
}

internal object SpbCalculator {
    fun calculate(left: Double, right: Double, operator: String): String = when (operator) {
        "+" -> format(left + right)
        "−" -> format(left - right)
        "×" -> format(left * right)
        "÷" -> if (right == 0.0) "Não definido" else format(left / right)
        else -> format(right)
    }
    fun format(value: Double): String = if (!value.isFinite()) "Não definido" else java.math.BigDecimal.valueOf(value)
        .round(java.math.MathContext(12)).stripTrailingZeros().toPlainString()
}

@Composable
private fun UtilityBattery() {
    val context = LocalContext.current
    var battery by remember { mutableStateOf<Intent?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { battery = intent } }
        battery = ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) (level * 100f / scale).toInt().coerceIn(0, 100) else null
    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    Paper {
        Text("Bateria", fontWeight = FontWeight.Bold)
        Text(percent?.let { "$it%" } ?: "—", fontSize = 54.sp, color = SpbAccent)
        LinearProgressIndicator(progress = { (percent ?: 0) / 100f }, Modifier.fillMaxWidth().height(14.dp), color = if ((percent ?: 100) < 20) Color(0xFFB85042) else Color(0xFF669E46))
        Text(when (status) { BatteryManager.BATTERY_STATUS_CHARGING -> "Carregando"; BatteryManager.BATTERY_STATUS_FULL -> "Carga completa"; BatteryManager.BATTERY_STATUS_DISCHARGING -> "Usando bateria"; else -> "" })
    }
}

@Composable
private fun UtilityConnections(interactive: Boolean) {
    val context = LocalContext.current
    var networkState by remember { mutableStateOf("Consultando conexão…") }
    var error by remember { mutableStateOf<String?>(null) }
    val refresh = {
        networkState = runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            when {
                capabilities == null -> "Sem conexão ativa"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi conectado"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados móveis conectados"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet conectada"
                else -> "Rede conectada"
            }
        }.getOrDefault("Estado da conexão indisponível")
    }
    OnUtilityResume(refresh)
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { android.os.Handler(context.mainLooper).post { refresh() } }
            override fun onLost(network: android.net.Network) { android.os.Handler(context.mainLooper).post { refresh() } }
            override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) { android.os.Handler(context.mainLooper).post { refresh() } }
        }
        val registered = runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
        onDispose { if (registered) runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    Paper {
        Text(networkState, fontWeight = FontWeight.Bold)
        listOf("Internet" to Settings.Panel.ACTION_INTERNET_CONNECTIVITY, "Wi-Fi" to Settings.Panel.ACTION_WIFI,
            "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS, "Modo avião" to Settings.ACTION_AIRPLANE_MODE_SETTINGS).forEach { (label, action) ->
            TextButton(enabled = interactive, onClick = { error = openUtilityIntent(context, Intent(action)) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    }
}

@Composable
private fun UtilityBrightness(interactive: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.utilityActivity() }
    var brightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 }
        ?: (runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128) / 255f)) }
    var error by remember { mutableStateOf<String?>(null) }
    Paper {
        Text("Brilho", fontWeight = FontWeight.Bold)
        Text("${(brightness * 100).toInt()}%", fontSize = 44.sp, color = SpbAccent)
        Slider(value = brightness.coerceIn(.05f, 1f), onValueChange = { value ->
            brightness = value
            activity?.window?.let { window -> window.attributes = window.attributes.apply { screenBrightness = value } }
        }, enabled = interactive && activity != null, valueRange = .05f..1f)
        Text("Ajuste aplicado ao launcher.", fontSize = 12.sp)
        TextButton(enabled = interactive, onClick = {
            activity?.window?.let { window -> window.attributes = window.attributes.apply { screenBrightness = -1f } }
            error = openUtilityIntent(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }) { Text("Brilho do aparelho") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun UtilityOperator(interactive: Boolean) {
    val context = LocalContext.current
    var operator by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    OnUtilityResume { operator = runCatching { context.getSystemService(TelephonyManager::class.java).networkOperatorName }.getOrDefault("") }
    Paper {
        Text(operator.ifBlank { "Operadora indisponível" }, fontSize = 25.sp)
        TextButton(enabled = interactive, onClick = { error = openUtilityIntent(context, Intent(Settings.ACTION_WIRELESS_SETTINGS)) }) { Text("Rede móvel") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun UtilityContact(panelId: String, interactive: Boolean) {
    val context = LocalContext.current
    val prefs = rememberUtilityPreferences()
    var name by remember(panelId) { mutableStateOf(prefs.getString("contact.name.$panelId", "").orEmpty()) }
    var contactUri by remember(panelId) { mutableStateOf(prefs.getString("contact.uri.$panelId", "").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val selected = runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            if (selected != null) {
                name = selected; contactUri = uri.toString()
                prefs.edit().putString("contact.name.$panelId", name).putString("contact.uri.$panelId", contactUri).apply()
                error = null
            } else error = "Não foi possível ler esse contato. Selecione novamente."
        }
    }
    Paper {
        Box(Modifier.size(74.dp).background(SpbAccent, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text(name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "+" }, fontSize = 32.sp, color = Color.White)
        }
        Text(name.ifBlank { "Contato favorito" }, fontSize = 22.sp)
        if (contactUri.isNotBlank()) TextButton(enabled = interactive, onClick = { error = openUtilityIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(contactUri))) }) { Text("Abrir contato") }
        TextButton(enabled = interactive, onClick = { runCatching { picker.launch(null) }.onFailure { error = "Nenhum aplicativo de contatos disponível." } }) { Text(if (name.isBlank()) "Escolher contato" else "Trocar contato") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun UtilityBirthdays(panelId: String, interactive: Boolean) {
    val context = LocalContext.current
    val prefs = rememberUtilityPreferences()
    var name by remember(panelId) { mutableStateOf(prefs.getString("birthday.name.$panelId", "").orEmpty()) }
    var date by remember(panelId) { mutableStateOf(prefs.getString("birthday.date.$panelId", "").orEmpty()) }
    var editing by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var dateDraft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val birthday = runCatching { MonthDay.parse(date, DateTimeFormatter.ofPattern("dd/MM")) }.getOrNull()
    val today = LocalDate.now()
    val next = birthday?.atYear(today.year)?.let { if (it.isBefore(today)) birthday.atYear(today.year + 1) else it }
    Paper {
        Text(name.ifBlank { "Aniversários" }, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(if (next != null) { val days = ChronoUnit.DAYS.between(today, next); if (days == 0L) "Hoje!" else "Em $days dias · $date" } else "Adicione uma data para acompanhar.")
        TextButton(enabled = interactive, onClick = { nameDraft = name; dateDraft = date; error = null; editing = true }) { Text(if (name.isBlank()) "Adicionar aniversário" else "Editar aniversário") }
        if (next != null) TextButton(enabled = interactive, onClick = {
            val epoch = next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            error = openUtilityIntent(context, Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, "Aniversário de $name")
                .putExtra(CalendarContract.Events.ALL_DAY, true).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, epoch)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, epoch + 86_400_000L))
        }) { Text("Adicionar à agenda") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Aniversário") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(nameDraft, { nameDraft = it.take(80) }, label = { Text("Nome") }, singleLine = true)
            OutlinedTextField(dateDraft, { dateDraft = it.take(5) }, label = { Text("Dia/mês (25/12)") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val valid = runCatching { MonthDay.parse(dateDraft, DateTimeFormatter.ofPattern("dd/MM")) }.isSuccess
        if (!valid || nameDraft.isBlank()) error = "Informe nome e uma data válida (dia/mês)."
        else { name = nameDraft.trim(); date = dateDraft; prefs.edit().putString("birthday.name.$panelId", name).putString("birthday.date.$panelId", date).apply(); editing = false; error = null }
    }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = { editing = false; error = null }) { Text("Cancelar") } })
}

@Composable
private fun UtilityExternalAction(title: String, explanation: String, action: String, interactive: Boolean, intent: Intent, fallback: Intent? = null) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    Paper {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(explanation)
        Button(enabled = interactive, onClick = { error = openUtilityIntent(context, intent, fallback) }) { Text(action) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun UtilitySearch(apps: List<AppItem>, interactive: Boolean, onLaunch: (AppItem) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Paper {
        OutlinedTextField(query, { query = it.take(200) }, enabled = interactive, singleLine = true, label = { Text("Pesquisar") }, modifier = Modifier.fillMaxWidth())
        val matching = if (query.isBlank()) emptyList() else apps.filter { it.label.contains(query, ignoreCase = true) }.take(6)
        matching.forEach { app -> UtilityAppRow(app, interactive) { onLaunch(app) } }
        Button(enabled = interactive && query.isNotBlank(), onClick = { error = openUtilityIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))) }) { Text("Buscar na Web") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun UtilityWebShortcut(type: PanelType, panelId: String, interactive: Boolean) {
    val context = LocalContext.current
    val prefs = rememberUtilityPreferences()
    var url by remember(panelId) { mutableStateOf(prefs.getString("url.$panelId", "").orEmpty()) }
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val title = when (type) { PanelType.TV -> "TV e programação"; PanelType.AFISHA -> "Eventos"; PanelType.SOCIAL -> "Rede social"; else -> "Favorito da Web" }
    Paper {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(if (url.isBlank()) "Escolha o site que deseja abrir neste card." else Uri.parse(url).host.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (url.isNotBlank()) Button(enabled = interactive, onClick = { error = openUtilityIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text("Abrir") }
        TextButton(enabled = interactive, onClick = { draft = url; editing = true; error = null }) { Text(if (url.isBlank()) "Escolher site" else "Editar site") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text(title) }, text = {
        Column { OutlinedTextField(draft, { draft = it.take(2_000) }, label = { Text("Endereço https://") }, singleLine = true); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, confirmButton = { TextButton(onClick = {
        val normalized = normalizedHttpsUrl(draft)
        if (normalized == null) error = "Informe um endereço HTTPS válido."
        else { url = normalized; prefs.edit().putString("url.$panelId", url).apply(); editing = false; error = null }
    }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = { editing = false; error = null }) { Text("Cancelar") } })
}

@Composable
private fun UtilityTraffic(panelId: String, interactive: Boolean) {
    val context = LocalContext.current
    val prefs = rememberUtilityPreferences()
    var destination by remember(panelId) { mutableStateOf(prefs.getString("traffic.$panelId", "").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    Paper {
        Text("Trânsito", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(destination, { destination = it.take(200) }, enabled = interactive, singleLine = true, label = { Text("Destino") }, modifier = Modifier.fillMaxWidth())
        Text("Veja rota e condições no seu aplicativo de mapas.", fontSize = 13.sp)
        Button(enabled = interactive && destination.isNotBlank(), onClick = {
            prefs.edit().putString("traffic.$panelId", destination).apply()
            error = openUtilityIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}")),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(destination)}")))
        }) { Text("Abrir mapa") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun UtilityFolder(panelId: String, apps: List<AppItem>, interactive: Boolean, onLaunch: (AppItem) -> Unit) {
    val prefs = rememberUtilityPreferences()
    var selected by remember(panelId) { mutableStateOf(prefs.getStringSet("folder.$panelId", emptySet()).orEmpty().toSet()) }
    var editing by remember { mutableStateOf(false) }
    Paper {
        val members = apps.filter { "${it.packageName}/${it.className}" in selected }
        if (members.isEmpty()) Text("Adicione seus aplicativos a esta pasta.")
        members.forEach { app -> UtilityAppRow(app, interactive) { onLaunch(app) } }
        TextButton(enabled = interactive, onClick = { editing = true }) { Text("Escolher aplicativos") }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Aplicativos da pasta") }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            apps.forEach { app ->
                val key = "${app.packageName}/${app.className}"
                Row(Modifier.fillMaxWidth().clickable {
                    selected = if (key in selected) selected - key else selected + key
                    prefs.edit().putStringSet("folder.$panelId", selected).apply()
                }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = key in selected, onCheckedChange = null)
                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { editing = false }) { Text("Concluir") } })
}

@Composable
private fun UtilityAppRow(app: AppItem, enabled: Boolean, launch: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = launch).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        app.icon?.let { icon -> val bitmap = remember(icon) { icon.toBitmap(48, 48).asImageBitmap() }; Image(bitmap, null, Modifier.size(34.dp)) }
        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UtilityNews(panelId: String, interactive: Boolean) {
    val context = LocalContext.current
    val prefs = rememberUtilityPreferences()
    val scope = rememberCoroutineScope()
    var url by remember(panelId) { mutableStateOf(prefs.getString("news.url.$panelId", "").orEmpty()) }
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var articles by remember(panelId) { mutableStateOf(SpbNewsFeed.decode(prefs.getString("news.cache.$panelId", "").orEmpty())) }
    var updated by remember(panelId) { mutableLongStateOf(prefs.getLong("news.updated.$panelId", 0)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        if (loading || url.isBlank()) return
        loading = true; error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { SpbNewsFeed.fetch(url) } }
            result.onSuccess { items ->
                articles = items; updated = System.currentTimeMillis()
                prefs.edit().putString("news.cache.$panelId", SpbNewsFeed.encode(items)).putLong("news.updated.$panelId", updated).apply()
                if (items.isEmpty()) error = "Este feed não contém notícias com links HTTPS."
            }.onFailure { error = "Não foi possível atualizar. Confira a conexão e o endereço do feed." }
            loading = false
        }
    }
    Paper {
        Text("Notícias", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        if (url.isBlank()) Text("Adicione um feed RSS ou Atom para acompanhar notícias.")
        else Text(Uri.parse(url).host.orEmpty(), fontSize = 12.sp, color = SpbAccent)
        articles.take(8).forEach { item ->
            Text(item.title, Modifier.fillMaxWidth().clickable(enabled = interactive) {
                error = openUtilityIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
            }.padding(vertical = 7.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = Color(0xFFD6D4CE))
        }
        if (updated > 0) Text("Atualizado ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(updated))}", fontSize = 11.sp)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        Row {
            TextButton(enabled = interactive && url.isNotBlank() && !loading, onClick = ::refresh) { Text("Atualizar") }
            TextButton(enabled = interactive && !loading, onClick = { draft = url; editing = true; error = null }) { Text("Escolher feed") }
        }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Feed de notícias") }, text = {
        Column { OutlinedTextField(draft, { draft = it.take(2_000) }, label = { Text("URL HTTPS do RSS/Atom") }, singleLine = true); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, confirmButton = { TextButton(onClick = {
        val normalized = normalizedHttpsUrl(draft)
        if (normalized == null) error = "Informe um endereço HTTPS válido."
        else {
            if (url != normalized) { articles = emptyList(); updated = 0; prefs.edit().remove("news.cache.$panelId").remove("news.updated.$panelId").apply() }
            url = normalized; prefs.edit().putString("news.url.$panelId", url).apply(); editing = false; refresh()
        }
    }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = { editing = false; error = null }) { Text("Cancelar") } })
}

@Composable
private fun rememberUtilityPreferences() = LocalContext.current.let { context -> remember(context) { context.getSharedPreferences("spb_utility_widgets", Context.MODE_PRIVATE) } }

@Composable
private fun OnUtilityResume(action: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestAction by rememberUpdatedState(action)
    DisposableEffect(lifecycle) {
        latestAction()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) latestAction() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

internal fun openUtilityIntent(context: Context, intent: Intent, fallback: Intent? = null): String? {
    val result = runCatching { context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    if (result.isSuccess) return null
    if (fallback != null && runCatching { context.startActivity(Intent(fallback).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return null
    return "Nenhum aplicativo disponível para abrir esta ação."
}

internal tailrec fun Context.utilityActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.utilityActivity()
    else -> null
}

internal fun normalizedHttpsUrl(input: String): String? {
    val value = input.trim().let { if (it.contains("://")) it else "https://$it" }
    return runCatching { java.net.URI(value) }.getOrNull()?.takeIf {
        it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() && it.rawUserInfo == null && value.length <= 2_000
    }?.toASCIIString()
}
