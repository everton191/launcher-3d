package br.com.ne3d.spbshellmodern.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.text.Collator
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.ne3d.spbshellmodern.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import java.net.URL
import java.net.HttpURLConnection
import java.io.IOException
import org.json.JSONObject
import org.json.JSONException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import java.net.URLEncoder

private val Context.launcherDataStore by preferencesDataStore("launcher_panels")

class AppRepository(context: Context) {
    private val context = context.applicationContext

    fun load(): List<AppItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance()
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.exported && it.activityInfo.enabled &&
                it.activityInfo.applicationInfo.enabled && it.activityInfo.packageName != context.packageName }
            .distinctBy { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName,
                it.activityInfo.name, it.loadIcon(pm)) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun launch(app: AppItem) {
        context.startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.className)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        })
    }
}

class PanelPreferencesRepository(private val context: Context) {
    private val workspaceKey = stringPreferencesKey("workspace_v2")
    private val orderKey = stringPreferencesKey("panel_order")
    private val activeKey = stringPreferencesKey("active_panel")
    private val weatherCityKey = stringPreferencesKey("weather_city")

    suspend fun loadWorkspace(): ShellWorkspace {
        // Persist migration immediately so repaired duplicate IDs remain stable on the next launch.
        val prefs = context.launcherDataStore.edit { prefs ->
            val workspace = WorkspaceCodec.decode(prefs[workspaceKey], prefs[orderKey], prefs[activeKey])
            prefs[workspaceKey] = WorkspaceCodec.encode(workspace)
            prefs[activeKey] = workspace.activePanelId
        }
        return WorkspaceCodec.decode(prefs[workspaceKey])
    }

    suspend fun load(): Pair<List<LauncherPanel>, String> = loadWorkspace().let { it.panels to it.activePanelId }

    suspend fun saveWorkspace(workspace: ShellWorkspace) {
        val valid = normalizeWorkspace(workspace)
        context.launcherDataStore.edit { prefs ->
            prefs[workspaceKey] = WorkspaceCodec.encode(valid)
            prefs[orderKey] = valid.panels.joinToString("|") { "${it.type.name}:${it.id}" }
            prefs[activeKey] = valid.activePanelId
        }
    }

    suspend fun save(panels: List<LauncherPanel>, activePanelId: String) {
        context.launcherDataStore.edit { prefs ->
            val previous = WorkspaceCodec.decode(prefs[workspaceKey], prefs[orderKey], prefs[activeKey])
            val activeIds = panels.map { it.id }.toSet()
            val valid = normalizeWorkspace(previous.copy(
                panels = panels,
                activePanelId = activePanelId,
                storedPanels = previous.storedPanels.filterNot { it.id in activeIds }
            ))
            prefs[workspaceKey] = WorkspaceCodec.encode(valid)
            prefs[orderKey] = valid.panels.joinToString("|") { "${it.type.name}:${it.id}" }
            prefs[activeKey] = valid.activePanelId
        }
    }

    suspend fun loadWeatherCity(): String = context.launcherDataStore.data.map { it[weatherCityKey].orEmpty() }.first()

    suspend fun saveWeatherCity(city: String) {
        context.launcherDataStore.edit { it[weatherCityKey] = city.trim() }
    }
}

class WidgetRepository(private val context: Context) {
    fun loadRecentPhotos(limit: Int = 6): List<android.graphics.Bitmap> {
        val requested = limit.coerceIn(0, 24)
        if (requested == 0) return emptyList()
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        val hasImages = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val hasSelectedImages = Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasImages && !hasSelectedImages) return emptyList()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val result = mutableListOf<android.graphics.Bitmap>()
        try {
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var inspected = 0
                // A broken/deleted thumbnail must not cause a scan of the entire user's library.
                while (cursor.moveToNext() && result.size < requested && inspected < requested * 4) {
                    inspected++
                    val item = android.content.ContentUris.withAppendedId(uri, cursor.getLong(idColumn))
                    try {
                        result.add(context.contentResolver.loadThumbnail(item, Size(320, 320), null))
                    } catch (_: IOException) {
                        // Photo may have been deleted or its cloud provider may be offline.
                    } catch (_: SecurityException) {
                        // Selected-photo access can change while the gallery is open.
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission may be revoked after the check above.
        } catch (_: IllegalArgumentException) {
            // A temporarily unavailable provider can reject the query.
        }
        return result
    }

    @Suppress("MissingPermission")
    suspend fun loadWeather(city: String? = null): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            var resolvedCity = city?.trim().orEmpty()
            val coordinates = if (resolvedCity.isNotEmpty()) {
                val query = URLEncoder.encode(resolvedCity, Charsets.UTF_8.name())
                val result = readJson("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=1&language=pt&format=json")
                    .optJSONArray("results")?.optJSONObject(0) ?: return@withContext null
                resolvedCity = result.optString("name", resolvedCity)
                result.getDouble("latitude") to result.getDouble("longitude")
            } else {
                val location = currentLocation() ?: return@withContext null
                location.latitude to location.longitude
            }
            val root = readJson(
                "https://api.open-meteo.com/v1/forecast?latitude=${coordinates.first}&longitude=${coordinates.second}" +
                    "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=7&timezone=auto"
            )
            val current = root.getJSONObject("current")
            val code = current.getInt("weather_code")
            val temperature = current.getDouble("temperature_2m")
            if (!temperature.isFinite()) return@withContext null
            val daily = root.optJSONObject("daily")
            val dates = daily?.optJSONArray("time")
            val minima = daily?.optJSONArray("temperature_2m_min")
            val maxima = daily?.optJSONArray("temperature_2m_max")
            val codes = daily?.optJSONArray("weather_code")
            val days = (0 until (dates?.length() ?: 0).coerceAtMost(7)).mapNotNull { index ->
                val low = minima?.optDouble(index) ?: Double.NaN
                val high = maxima?.optDouble(index) ?: Double.NaN
                val dayCode = codes?.optInt(index, -1) ?: -1
                val date = dates?.optString(index).orEmpty()
                if (low.isFinite() && high.isFinite() && dayCode >= 0 && date.isNotBlank()) {
                    WeatherDay(date, low.roundToInt(), high.roundToInt(), dayCode)
                } else null
            }
            WeatherInfo(temperature.roundToInt(), weatherDescription(code), code, days, resolvedCity)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        } catch (_: JSONException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    @Suppress("MissingPermission")
    private suspend fun currentLocation(): android.location.Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = manager.getProviders(true)
        val recent = providers.mapNotNull { provider ->
            try { manager.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
        }.filter { System.currentTimeMillis() - it.time in 0..7_200_000L }.maxByOrNull { it.time }
        if (recent != null || Build.VERSION.SDK_INT < 30) return recent
        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { continuation ->
                val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
                    ?: providers.firstOrNull() ?: run { continuation.resume(null); return@suspendCancellableCoroutine }
                val signal = android.os.CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) {
                        if (continuation.isActive) continuation.resume(it)
                    }
                } catch (_: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                } catch (_: IllegalArgumentException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private suspend fun readJson(address: String): JSONObject {
        coroutineContext.ensureActive()
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode !in 200..299) throw IOException("Weather provider unavailable")
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(4_096)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (output.length + count > 262_144) throw IOException("Weather response too large")
                    output.append(buffer, 0, count)
                }
                output.toString()
            }
            coroutineContext.ensureActive()
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }
}

fun weatherDescription(code: Int): String = when (code) {
    0 -> "Céu limpo"
    1 -> "Predominantemente limpo"
    2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Neblina"
    in 51..57 -> "Garoa"
    in 61..67 -> "Chuva"
    in 71..77, 85, 86 -> "Neve"
    in 80..82 -> "Pancadas de chuva"
    in 95..99 -> "Tempestade"
    else -> "Condição atual"
}
