package br.com.ne3d.spbshellmodern.model

import org.junit.Assert.*
import org.junit.Test

class WorkspaceIntegrityTest {
    @Test fun overflowAndMissingAnchorsKeepEveryCustomPanel() {
        val custom = (1..18).map { panelTemplate(PanelType.MOON, "moon-$it").copy(title = "Lua $it") }
        val result = normalizeWorkspace(ShellWorkspace(panels = custom, activePanelId = "removed", homePanelId = "removed"))

        assertEquals(16, result.panels.size)
        assertEquals(20, result.panels.size + result.storedPanels.size)
        assertTrue(result.panels.any { it.type == PanelType.HOME && !it.removable })
        assertTrue(result.panels.any { it.type == PanelType.APPS && !it.removable })
        assertEquals(custom.map { it.id }.toSet(), (result.panels + result.storedPanels).filter { it.type == PanelType.MOON }.map { it.id }.toSet())
        assertEquals(result.homePanelId, result.activePanelId)
    }

    @Test fun duplicateIdsAreRepairedWithoutLosingContentAndThenRemainStable() {
        val duplicate = ShellItem("same-item", PanelType.CLOCK, title = "Relógio")
        val workspace = ShellWorkspace(
            panels = initialPanels() + listOf(
                panelTemplate(PanelType.MOON, "same-panel").copy(title = "Primeira", widgets = listOf(duplicate)),
                panelTemplate(PanelType.MOON, "same-panel").copy(title = "Segunda", widgets = listOf(duplicate))
            ),
            tray = listOf(duplicate),
            dock = listOf(duplicate.copy(type = PanelType.FOLDER, children = listOf(duplicate)))
        )
        val result = normalizeWorkspace(workspace)
        assertEquals(8, result.panels.map { it.id }.toSet().size)
        assertEquals(listOf("Primeira", "Segunda"), result.panels.takeLast(2).map { it.title })
        val items = result.panels.flatMap { it.widgets } + result.tray + result.dock + result.dock.flatMap { it.children }
        assertEquals(items.size, items.map { it.id }.toSet().size)
        assertEquals(result, normalizeWorkspace(result))
    }

    @Test fun configuredHomeAndFolderPresentationKeepTheirIdentity() {
        val folder = ShellItem("folder", PanelType.FOLDER, WidgetPresentation.ROW,
            children = listOf(ShellItem("shortcut", PanelType.APPS, appPackage = "example.app", appClass = "example.app.Main")))
        val workspace = ShellWorkspace(
            panels = initialPanels().map { if (it.id == "photos") it.copy(title = "Minhas fotos", hue = -1f, widgets = listOf(folder)) else it },
            homePanelId = "photos",
            activePanelId = "photos"
        )
        val result = normalizeWorkspace(workspace)
        assertEquals(workspace, result)
        assertEquals(folder.children, folder.copy(presentation = WidgetPresentation.EXPANDED).children)
        assertEquals(folder.id, folder.copy(presentation = WidgetPresentation.EXPANDED).id)
    }

    @Test fun removedThenAddedPanelsCannotReuseCountBasedIds() {
        val ids = (1..100).map { freshPanelId(PanelType.CLOCK) }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("clock-") })
    }
}
