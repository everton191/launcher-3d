package br.com.ne3d.spbshellmodern

import br.com.ne3d.spbshellmodern.data.WorkspaceCodec
import br.com.ne3d.spbshellmodern.model.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceCodecTest {
    @Test fun intentionallyEmptyPanelStaysEmptyAfterRestart() {
        val workspace = ShellWorkspace(panels = initialPanels().map {
            if (it.id == "home") it.copy(customized = true) else it
        })
        val restored = WorkspaceCodec.decode(WorkspaceCodec.encode(workspace))
        assertTrue(restored.panels.first().customized)
        assertTrue(restored.panels.first().widgets.isEmpty())
    }
    @Test fun schema2RoundTripPreservesPanelsWidgetsStoredTrayAndDock() {
        val shortcut = ShellItem("shortcut", PanelType.APPS, WidgetPresentation.ICON, "example.app", "example.app.Main", "Meu app")
        val folder = ShellItem("folder", PanelType.FOLDER, WidgetPresentation.ROW, title = "Utilitários", children = listOf(shortcut))
        val workspace = ShellWorkspace(
            panels = initialPanels().map { if (it.id == "home") it.copy(title = "Início personalizado", hue = 240f, widgets = listOf(folder)) else it },
            activePanelId = "weather",
            homePanelId = "photos",
            storedPanels = listOf(panelTemplate(PanelType.MOON, "stored-moon").copy(hue = -1f)),
            tray = listOf(ShellItem("tray-clock", PanelType.CLOCK, WidgetPresentation.THREE_D)),
            dock = listOf(ShellItem("dock-app", PanelType.APPS, appPackage = "example.other", appClass = "example.other.Main"))
        )
        assertEquals(workspace, WorkspaceCodec.decode(WorkspaceCodec.encode(workspace)))
    }

    @Test fun partialLegacyOrderIsRepairedWithoutReplacingCustomPanels() {
        val workspace = WorkspaceCodec.decode(null, "MOON:mine|BROKEN:invalid|PHOTOS:album|MOON:mine", "album")
        assertEquals(5, workspace.panels.size)
        assertEquals("album", workspace.activePanelId)
        assertEquals(2, workspace.panels.count { it.type == PanelType.MOON })
        assertEquals(workspace.panels.size, workspace.panels.map { it.id }.toSet().size)
        assertEquals(workspace, WorkspaceCodec.decode(WorkspaceCodec.encode(workspace)))
    }

    @Test fun corruptJsonFallsBackToExistingLegacyOrder() {
        val workspace = WorkspaceCodec.decode("{invalid", "APPS:my-apps|HOME:my-home|WEATHER:my-weather", "my-weather")
        assertEquals(listOf("my-apps", "my-home", "my-weather"), workspace.panels.map { it.id })
        assertEquals("my-weather", workspace.activePanelId)
        assertEquals("my-home", workspace.homePanelId)
    }
}
