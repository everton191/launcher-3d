package br.com.ne3d.spbshellmodern.model

/** Repairs imported/legacy data while keeping every panel and its content. */
fun normalizeWorkspace(workspace: ShellWorkspace): ShellWorkspace {
    val panelIds = mutableSetOf<String>()
    val itemIds = mutableSetOf<String>()

    fun uniqueId(value: String, type: PanelType, used: MutableSet<String>): String {
        if (value.isNotBlank() && used.add(value)) return value
        var id: String
        do { id = freshPanelId(type) } while (!used.add(id))
        return id
    }

    fun normalizeItems(items: List<ShellItem>): List<ShellItem> = items.map { item ->
        item.copy(
            id = uniqueId(item.id, item.type, itemIds),
            children = normalizeItems(item.children)
        )
    }

    fun normalizePanel(panel: LauncherPanel): LauncherPanel = panel.copy(
        id = uniqueId(panel.id, panel.type, panelIds),
        removable = panel.removable && panel.type != PanelType.HOME && panel.type != PanelType.APPS,
        hue = when {
            !panel.hue.isFinite() -> 180f
            panel.hue == -1f -> -1f // Original theme uses -1 for the wallpaper's own hue.
            else -> ((panel.hue % 360f) + 360f) % 360f
        },
        widgets = normalizeItems(panel.widgets)
    )

    val active = workspace.panels.map(::normalizePanel).toMutableList()
    val stored = workspace.storedPanels.map(::normalizePanel).toMutableList()
    for (required in listOf(PanelType.HOME, PanelType.APPS)) {
        if (active.none { it.type == required }) {
            val storedIndex = stored.indexOfFirst { it.type == required }
            val panel = if (storedIndex >= 0) stored.removeAt(storedIndex)
                else normalizePanel(panelTemplate(required))
            active.add(panel)
        }
    }

    // Overflow is stored, never silently deleted. Keep the two original anchors active.
    val anchors = setOf(active.first { it.type == PanelType.HOME }.id, active.first { it.type == PanelType.APPS }.id)
    val overflow = mutableListOf<LauncherPanel>()
    while (active.size > MAX_PANELS) {
        val index = active.indexOfLast { it.id !in anchors }
        overflow.add(0, active.removeAt(index))
    }
    stored.addAll(0, overflow)
    val home = workspace.homePanelId.takeIf { id -> active.any { it.id == id } }
        ?: active.first { it.type == PanelType.HOME }.id
    val selected = workspace.activePanelId.takeIf { id -> active.any { it.id == id } } ?: home
    return workspace.copy(
        panels = active,
        storedPanels = stored,
        activePanelId = selected,
        homePanelId = home,
        tray = normalizeItems(workspace.tray),
        dock = normalizeItems(workspace.dock)
    )
}
