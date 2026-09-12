package br.com.ne3d.spbshellmodern

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import br.com.ne3d.spbshellmodern.data.PanelPreferencesRepository
import br.com.ne3d.spbshellmodern.model.initialPanels
import br.com.ne3d.spbshellmodern.model.ShellWorkspace
import br.com.ne3d.spbshellmodern.ui.LauncherModeKey
import br.com.ne3d.spbshellmodern.ui.PanelOrderKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After

class OrganizePersistenceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var original: ShellWorkspace
    private val repository get() = PanelPreferencesRepository(rule.activity)
    @Before fun backupWorkspace() {
        original = runBlocking { repository.loadWorkspace() }
        runBlocking { repository.saveWorkspace(ShellWorkspace()) }
        rule.activityRule.scenario.recreate(); rule.waitForIdle()
    }
    @After fun restoreWorkspace() {
        val repo = repository
        rule.activityRule.scenario.close()
        runBlocking { repo.saveWorkspace(original) }
    }

    @Test fun reorderAddRemoveAndRestoreAfterRecreate() {
        runBlocking { PanelPreferencesRepository(rule.activity).save(initialPanels(), "home") }
        rule.activityRule.scenario.recreate(); rule.waitForIdle()
        rule.onNodeWithTag("handle").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("panel-2").performSemanticsAction(SemanticsActions.OnLongClick); rule.waitForIdle()
        assertEquals("ORGANIZE", rule.onNodeWithTag("wheel").fetchSemanticsNode().config[LauncherModeKey])
        rule.onNodeWithText("← Mover").performClick(); rule.waitForIdle()
        val reordered = rule.onNodeWithTag("wheel").fetchSemanticsNode().config[PanelOrderKey]
        assertEquals("home|favorites|apps|agenda|photos|weather", reordered)
        rule.onNodeWithText("+ Adicionar painel").performClick()
        rule.onNodeWithTag("add-AGENDA").performClick(); rule.waitForIdle()
        val added = rule.onNodeWithTag("wheel").fetchSemanticsNode().config[PanelOrderKey]
        assertEquals(7, added.split('|').size)
        rule.onNodeWithText("Remover").performClick(); rule.waitForIdle()
        assertEquals(6, rule.onNodeWithTag("wheel").fetchSemanticsNode().config[PanelOrderKey].split('|').size)
        rule.onNodeWithText("+ Adicionar painel").performClick()
        rule.onNodeWithTag("widget-catalog").performScrollToNode(hasTestTag("add-PHOTOS"))
        rule.onNodeWithTag("add-PHOTOS").performClick(); rule.waitForIdle()
        val persisted = rule.onNodeWithTag("wheel").fetchSemanticsNode().config[PanelOrderKey]
        rule.activityRule.scenario.recreate(); rule.waitUntil(5_000) {
            rule.onNodeWithTag("wheel").fetchSemanticsNode().config[PanelOrderKey] == persisted
        }
        assertEquals(persisted, rule.onNodeWithTag("wheel").fetchSemanticsNode().config[PanelOrderKey])
        runBlocking { PanelPreferencesRepository(rule.activity).save(initialPanels(), "home") }
    }
}
