package br.com.ne3d.spbshellmodern.data

import br.com.ne3d.spbshellmodern.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Schema 2 keeps item identity independent of panel order and visual presentation. */
object WorkspaceCodec {
    const val SCHEMA_VERSION = 2

    fun encode(workspace: ShellWorkspace): String {
        val valid = normalizeWorkspace(workspace)
        return JSONObject().apply {
            put("schema", SCHEMA_VERSION)
            put("panels", panelArray(valid.panels))
            put("activePanelId", valid.activePanelId)
            put("homePanelId", valid.homePanelId)
            put("storedPanels", panelArray(valid.storedPanels))
            put("tray", itemArray(valid.tray))
            put("dock", itemArray(valid.dock))
        }.toString()
    }

    fun decode(json: String?, legacyOrder: String? = null, legacyActive: String? = null): ShellWorkspace {
        val restored = json?.let { raw ->
            runCatching {
                val root = JSONObject(raw)
                require(root.getInt("schema") == SCHEMA_VERSION)
                ShellWorkspace(
                    panels = readPanels(root.getJSONArray("panels")),
                    activePanelId = root.optString("activePanelId", "home"),
                    homePanelId = root.optString("homePanelId", "home"),
                    storedPanels = readPanels(root.optJSONArray("storedPanels")),
                    tray = readItems(root.optJSONArray("tray")),
                    dock = readItems(root.optJSONArray("dock"))
                )
            }.getOrNull()
        }
        if (restored != null) return normalizeWorkspace(restored)
        val legacyPanels = legacyOrder?.split('|')?.mapNotNull { entry ->
            val parts = entry.split(':', limit = 2)
            runCatching {
                val type = PanelType.valueOf(parts[0])
                panelTemplate(type, parts.getOrElse(1) { type.name.lowercase() })
            }.getOrNull()
        }.orEmpty().ifEmpty { initialPanels() }
        return normalizeWorkspace(ShellWorkspace(panels = legacyPanels, activePanelId = legacyActive ?: "home"))
    }

    private fun panelArray(panels: List<LauncherPanel>) = JSONArray().apply {
        panels.forEach { panel ->
            put(JSONObject().apply {
                put("id", panel.id)
                put("title", panel.title)
                put("type", panel.type.name)
                put("removable", panel.removable)
                put("hue", panel.hue.toDouble())
                put("widgets", itemArray(panel.widgets))
                put("customized", panel.customized)
            })
        }
    }

    private fun itemArray(items: List<ShellItem>): JSONArray = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("type", item.type.name)
                put("presentation", item.presentation.name)
                put("appPackage", item.appPackage ?: JSONObject.NULL)
                put("appClass", item.appClass ?: JSONObject.NULL)
                put("title", item.title)
                put("children", itemArray(item.children))
            })
        }
    }

    private fun readPanels(array: JSONArray?): List<LauncherPanel> = objects(array).mapNotNull { value ->
        runCatching {
            val type = PanelType.valueOf(value.getString("type"))
            val template = panelTemplate(type)
            template.copy(
                id = value.optString("id", template.id),
                title = value.optString("title", template.title),
                removable = value.optBoolean("removable", template.removable),
                hue = value.optDouble("hue", 180.0).toFloat(),
                widgets = readItems(value.optJSONArray("widgets")),
                customized = value.optBoolean("customized", false)
            )
        }.getOrNull()
    }

    private fun readItems(array: JSONArray?): List<ShellItem> = objects(array).mapNotNull { value ->
        runCatching {
            ShellItem(
                id = value.optString("id"),
                type = PanelType.valueOf(value.getString("type")),
                presentation = runCatching {
                    WidgetPresentation.valueOf(value.optString("presentation"))
                }.getOrDefault(WidgetPresentation.COMPACT),
                appPackage = value.nullableString("appPackage"),
                appClass = value.nullableString("appClass"),
                title = value.optString("title"),
                children = readItems(value.optJSONArray("children"))
            )
        }.getOrNull()
    }

    private fun objects(array: JSONArray?): List<JSONObject> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull(array::optJSONObject)

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }
}
