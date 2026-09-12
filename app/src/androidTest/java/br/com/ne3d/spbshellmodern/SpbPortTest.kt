package br.com.ne3d.spbshellmodern

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import br.com.ne3d.spbshellmodern.data.PanelPreferencesRepository
import br.com.ne3d.spbshellmodern.data.WidgetRepository
import br.com.ne3d.spbshellmodern.model.*
import br.com.ne3d.spbshellmodern.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises the port on Android, including the empty-workspace and gesture cancellation regressions. */
class SpbPortTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repository get() = PanelPreferencesRepository(context)
    private fun mode() = rule.onNodeWithTag("wheel").fetchSemanticsNode().config[LauncherModeKey]
    private fun load(workspace: ShellWorkspace) {
        rule.waitForIdle()
        runBlocking { repository.saveWorkspace(workspace) }
        rule.activityRule.scenario.recreate()
        val order = workspace.panels.joinToString("|") { it.id }
        rule.waitUntil(10_000) { rule.onNodeWithTag("wheel").fetchSemanticsNode().config[PanelOrderKey] == order }
        rule.waitForIdle()
    }
    private fun select(index: Int) {
        if (mode() == "NORMAL") rule.onNodeWithTag("handle").performClick()
        rule.waitUntil(5_000) { rule.onNodeWithTag("wheel").fetchSemanticsNode().config[CarouselProgressKey] == 1f }
        rule.waitForIdle()
        // Semantics selects even a rear card; separate gesture tests cover physical picking.
        rule.onNodeWithTag("panel-$index").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { mode() == "NORMAL" }
        rule.waitForIdle()
    }
    private fun capture(name: String) {
        val output = File(context.getExternalFilesDir(null), "spb-validation").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun everyCardOpensAndMagicStopsOnTouch() {
        val original = runBlocking { repository.loadWorkspace() }
        try {
            val types = PanelType.entries.filter { it != PanelType.HOME && it != PanelType.APPS }
            types.chunked(14).forEach { batch ->
                val panels = listOf(panelTemplate(PanelType.HOME), panelTemplate(PanelType.APPS)) + batch.map { panelTemplate(it) }
                load(ShellWorkspace(panels = panels))
                panels.indices.forEach { index ->
                    select(index)
                    assertEquals(index, rule.onNodeWithTag("wheel").fetchSemanticsNode().config[SelectedPanelKey])
                    capture("card-${panels[index].type.name}")
                }
            }
            load(ShellWorkspace(panels = listOf(panelTemplate(PanelType.HOME), panelTemplate(PanelType.APPS), panelTemplate(PanelType.MOON)), activePanelId = "moon"))
            rule.onNodeWithTag("handle").performClick()
            rule.waitUntil(12_000) { rule.onNodeWithTag("wheel").fetchSemanticsNode().config[MagicActiveKey] }
            capture("magic-moon")
            rule.onNodeWithTag("wheel").performTouchInput { down(center) }
            rule.waitUntil(2_000) { !rule.onNodeWithTag("wheel").fetchSemanticsNode().config[MagicActiveKey] }
            // Real delay verifies idle cannot restart while the finger remains down.
            val holdUntil = System.nanoTime() + 5_200_000_000L
            rule.waitUntil(6_000) { System.nanoTime() >= holdUntil }
            assertFalse(rule.onNodeWithTag("wheel").fetchSemanticsNode().config[MagicActiveKey])
            rule.onNodeWithTag("wheel").performTouchInput { up() }
        } finally { rule.activityRule.scenario.close(); runBlocking { repository.saveWorkspace(original) } }
    }

    @Test fun removeLastItemAndRestoreFromTrayAfterRestart() {
        val original = runBlocking { repository.loadWorkspace() }
        try {
            val clock = ShellItem("test-clock", PanelType.CLOCK, WidgetPresentation.COMPACT, title = "Relógio de teste")
            load(ShellWorkspace(panels = initialPanels().map { if (it.id == "home") it.copy(widgets = listOf(clock), customized = true) else it }))
            rule.onNodeWithContentDescription("Editar").performClick()
            rule.onNodeWithTag("item-test-clock").performTouchInput { longClick() }
            rule.onNodeWithText("Guardar na bandeja").performClick()
            rule.onNodeWithText("Concluir").performClick()
            rule.onNodeWithTag("item-test-clock").assertDoesNotExist()
            rule.waitUntil(5_000) { runBlocking { repository.loadWorkspace() }.tray.any { it.id == clock.id } }
            rule.activityRule.scenario.recreate(); rule.waitForIdle()
            rule.onNodeWithTag("item-test-clock").assertDoesNotExist()
            rule.onNodeWithContentDescription("Editar").performClick()
            rule.onNodeWithText("↑ Relógio de teste").performClick()
            rule.onNodeWithTag("item-test-clock").assertExists()
            rule.onNodeWithText("Concluir").performClick()
            capture("editor-restored-clock")
        } finally { rule.activityRule.scenario.close(); runBlocking { repository.saveWorkspace(original) } }
    }

    @Test fun draggingItemAcrossTwoPanelEdgesKeepsGestureAlive() {
        val original = runBlocking { repository.loadWorkspace() }
        try {
            val item = ShellItem("drag-test", PanelType.CLOCK, WidgetPresentation.COMPACT, title = "Mover relógio")
            val panels = listOf(panelTemplate(PanelType.HOME).copy(widgets = listOf(item), customized = true),
                panelTemplate(PanelType.FAVORITES).copy(customized = true), panelTemplate(PanelType.NOTES).copy(customized = true), panelTemplate(PanelType.APPS))
            load(ShellWorkspace(panels = panels))
            rule.onNodeWithContentDescription("Editar").performClick()
            val itemNode = rule.onNodeWithTag("item-drag-test").fetchSemanticsNode()
            val wheel = rule.onNodeWithTag("wheel").fetchSemanticsNode().boundsInRoot
            rule.onRoot().performTouchInput {
                down(itemNode.boundsInRoot.center); advanceEventTime(700)
                moveTo(Offset(wheel.right - 15f, wheel.center.y), 500)
            }
            rule.waitUntil(6_000) { rule.onNodeWithTag("wheel").fetchSemanticsNode().config[SelectedPanelKey] == 2 }
            rule.onRoot().performTouchInput { moveTo(wheel.center, 100); up() }
            rule.waitUntil(5_000) { runBlocking { repository.loadWorkspace() }.panels[2].widgets.any { it.id == item.id } }
            val workspace = runBlocking { repository.loadWorkspace() }
            assertFalse(workspace.panels[0].widgets.any { it.id == item.id })
            assertEquals(1, workspace.panels.flatMap { it.widgets }.count { it.id == item.id })
            capture("editor-edge-drop")
        } finally { rule.activityRule.scenario.close(); runBlocking { repository.saveWorkspace(original) } }
    }

    @Test fun realForecastFitsExpandedItemAndGlobeKeepsItsSettings() {
        val original = runBlocking { repository.loadWorkspace() }
        val originalCity = runBlocking { repository.loadWeatherCity() }
        try {
            runBlocking { repository.saveWeatherCity("Fortaleza") }
            val forecast = ShellItem("forecast-test", PanelType.WEATHER_GRAPH, WidgetPresentation.EXPANDED, title = "Previsão")
            load(ShellWorkspace(panels = initialPanels().map {
                if (it.id == "home") it.copy(customized = true, widgets = listOf(forecast)) else it
            }))
            rule.waitUntil(30_000) { rule.onAllNodesWithText("Atualizar").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Cidade").performScrollTo().assertIsDisplayed()
            capture("expanded-live-forecast")
            load(ShellWorkspace(panels = listOf(panelTemplate(PanelType.HOME), panelTemplate(PanelType.APPS), panelTemplate(PanelType.WORLD_TIME)), activePanelId = "world_time"))
            rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Globo terrestre").fetchSemanticsNodes().isNotEmpty() }
            capture("corrected-world-time")
            rule.onNodeWithContentDescription("Editar").performClick()
            rule.onNodeWithTag("item-world_time").assertExists()
            rule.waitUntil(5_000) { runBlocking { repository.loadWorkspace() }.panels.last().widgets.singleOrNull()?.id == "world_time" }
        } finally {
            rule.activityRule.scenario.close()
            runBlocking { repository.saveWorkspace(original); repository.saveWeatherCity(originalCity) }
        }
    }

    @Test fun mediaStoreFixtureIsDecodedAsRealPhoto() {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "spb-fixture-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SpbValidation")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        val fixture = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(37, 123, 211)) }
        try {
            resolver.openOutputStream(uri)!!.use { fixture.compress(Bitmap.CompressFormat.PNG, 100, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            val decoded = WidgetRepository(context).loadRecentPhotos(24)
            assertTrue("A real MediaStore image must reach the gallery", decoded.any {
                val pixel = it.getPixel(it.width / 2, it.height / 2)
                kotlin.math.abs(Color.red(pixel) - 37) <= 3 && kotlin.math.abs(Color.blue(pixel) - 211) <= 3
            })
            decoded.forEach { it.recycle() }
        } finally { resolver.delete(uri, null, null); fixture.recycle() }
    }
}

