package br.com.ne3d.spbshellmodern.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Host identity and widget ids are stable across launcher restarts. Internal SPB cards never enter this host. */
private object SpbWidgetHostRegistry {
    private const val HOST_ID = 16430
    private var host: AppWidgetHost? = null
    private var listeners = 0
    fun get(context: Context): AppWidgetHost = host ?: object : AppWidgetHost(context.applicationContext, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView = SpbHostedView(context)
    }.also { host = it }
    fun start(context: Context) {
        if (listeners == 0) runCatching { get(context).startListening() }
        listeners++
    }
    fun stop() {
        listeners = (listeners - 1).coerceAtLeast(0)
        if (listeners == 0) runCatching { host?.stopListening() }
    }
}

private class SpbHostedView(context: Context) : AppWidgetHostView(context) {
    var allowInteraction = true
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean = !allowInteraction || super.onInterceptTouchEvent(event)
    override fun onTouchEvent(event: MotionEvent?): Boolean = !allowInteraction || super.onTouchEvent(event)
}

/** Call when the user actually removes an Android widget panel, not when a carousel preview disappears. */
fun removeSpbAndroidWidget(context: Context, panelId: String) {
    val prefs = context.getSharedPreferences("spb_android_widgets", Context.MODE_PRIVATE)
    val host = SpbWidgetHostRegistry.get(context)
    listOf("id.$panelId", "pending.$panelId").forEach { key ->
        val id = prefs.getInt(key, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) runCatching { host.deleteAppWidgetId(id) }
        prefs.edit().remove(key).apply()
    }
}

@Composable
fun SpbAndroidWidget(panelId: String, interactive: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val host = remember(context) { SpbWidgetHostRegistry.get(context) }
    val prefs = remember(context) { context.getSharedPreferences("spb_android_widgets", Context.MODE_PRIVATE) }
    var widgetId by remember(panelId) { mutableIntStateOf(prefs.getInt("id.$panelId", AppWidgetManager.INVALID_APPWIDGET_ID)) }
    var pendingId by remember(panelId) { mutableIntStateOf(prefs.getInt("pending.$panelId", AppWidgetManager.INVALID_APPWIDGET_ID)) }
    var showProviders by remember { mutableStateOf(false) }
    var providers by remember { mutableStateOf(emptyList<AppWidgetProviderInfo>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var providerRevision by remember { mutableIntStateOf(0) }
    val info = remember(widgetId, providerRevision) { manager.getAppWidgetInfo(widgetId) }

    fun cancelPending() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID && pendingId != widgetId) runCatching { host.deleteAppWidgetId(pendingId) }
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        prefs.edit().remove("pending.$panelId").apply()
    }
    fun finishPending(id: Int) {
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || manager.getAppWidgetInfo(id) == null) {
            error = "O widget não foi adicionado. Escolha novamente."; cancelPending(); return
        }
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && widgetId != id) runCatching { host.deleteAppWidgetId(widgetId) }
        widgetId = id; pendingId = AppWidgetManager.INVALID_APPWIDGET_ID; providerRevision++
        prefs.edit().putInt("id.$panelId", id).remove("pending.$panelId").apply()
        error = null
    }
    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) finishPending(result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId)
        else cancelPending()
    }
    fun configureOrFinish(id: Int) {
        val provider = manager.getAppWidgetInfo(id)
        if (provider == null) { error = "O aplicativo não disponibilizou esse widget."; cancelPending(); return }
        if (provider.configure == null) finishPending(id)
        else runCatching {
            configure.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(provider.configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        }.onFailure { error = "Não foi possível abrir a configuração do widget."; cancelPending() }
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) configureOrFinish(result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId)
        else cancelPending()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) configureOrFinish(result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId)
        else cancelPending()
    }
    fun allocate(): Int {
        cancelPending()
        val id = host.allocateAppWidgetId()
        pendingId = id
        prefs.edit().putInt("pending.$panelId", id).apply()
        return id
    }
    fun showProviderList() {
        providers = runCatching { manager.installedProviders.sortedBy { it.loadLabel(context.packageManager) } }.getOrDefault(emptyList())
        showProviders = true
    }

    DisposableEffect(lifecycle, context) {
        var listening = false
        fun sync() {
            val shouldListen = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (shouldListen && !listening) { SpbWidgetHostRegistry.start(context); listening = true; providerRevision++ }
            else if (!shouldListen && listening) { SpbWidgetHostRegistry.stop(); listening = false }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer); sync()
        onDispose { lifecycle.removeObserver(observer); if (listening) SpbWidgetHostRegistry.stop() }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (info != null) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val width = maxWidth.value.toInt().coerceAtLeast(1)
                val height = maxHeight.value.toInt().coerceAtLeast(1)
                key(widgetId) {
                    AndroidView(factory = { viewContext ->
                        host.createView(viewContext, widgetId, info).apply { setAppWidget(widgetId, info) }
                    }, modifier = Modifier.fillMaxSize(), update = { view ->
                        (view as? SpbHostedView)?.allowInteraction = interactive
                        view.updateAppWidgetSize(Bundle().apply {
                            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                        }, width, height, width, height)
                    })
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
            Text(if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) "Widgets Android" else "Widget indisponível", style = MaterialTheme.typography.headlineSmall)
            Text(if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) "Escolha um widget dos aplicativos instalados." else "O aplicativo deste widget pode ter sido removido. Escolha outro widget.")
            Spacer(Modifier.weight(1f))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (interactive) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                error = null
                runCatching {
                    val id = allocate()
                    picker.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                }.onFailure { cancelPending(); showProviderList() }
            }) { Text(if (info == null) "Adicionar widget" else "Trocar widget") }
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) TextButton(onClick = {
                removeSpbAndroidWidget(context, panelId); widgetId = AppWidgetManager.INVALID_APPWIDGET_ID; pendingId = AppWidgetManager.INVALID_APPWIDGET_ID; error = null
            }) { Text("Remover") }
        }
    }
    if (showProviders) AlertDialog(onDismissRequest = { showProviders = false }, title = { Text("Widgets Android") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (providers.isEmpty()) Text("Nenhum provedor de widgets está disponível neste aparelho.")
            providers.forEach { provider ->
                Text(provider.loadLabel(context.packageManager), Modifier.fillMaxWidth().clickable {
                    showProviders = false
                    runCatching {
                        val id = allocate()
                        val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) }
                        if (manager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, options)) configureOrFinish(id)
                        else bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options))
                    }.onFailure { error = "Não foi possível adicionar este widget."; cancelPending() }
                }.padding(vertical = 14.dp))
                HorizontalDivider()
            }
        }
    }, confirmButton = { TextButton(onClick = { showProviders = false }) { Text("Cancelar") } })
}
