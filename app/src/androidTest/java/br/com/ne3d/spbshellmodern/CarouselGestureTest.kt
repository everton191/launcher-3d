package br.com.ne3d.spbshellmodern

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import br.com.ne3d.spbshellmodern.engine.*
import br.com.ne3d.spbshellmodern.ui.RotationAngleKey
import br.com.ne3d.spbshellmodern.ui.SelectedPanelKey
import br.com.ne3d.spbshellmodern.ui.CarouselProgressKey
import br.com.ne3d.spbshellmodern.ui.LauncherModeKey
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import br.com.ne3d.spbshellmodern.data.PanelPreferencesRepository
import br.com.ne3d.spbshellmodern.model.ShellWorkspace
import kotlinx.coroutines.runBlocking
import kotlin.math.abs

class CarouselGestureTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private var savedWorkspace: ShellWorkspace? = null
    @Before fun useSixPanelFixture() {
        val repository = PanelPreferencesRepository(rule.activity)
        savedWorkspace = runBlocking { repository.loadWorkspace() }
        runBlocking { repository.saveWorkspace(ShellWorkspace()) }
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5_000) { rule.onNodeWithTag("wheel").fetchSemanticsNode().config[br.com.ne3d.spbshellmodern.ui.PanelOrderKey] == "home|apps|favorites|agenda|photos|weather" }
        rule.waitForIdle()
    }
    @After fun restoreUserWorkspace() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        rule.activityRule.scenario.close()
        savedWorkspace?.let { runBlocking { PanelPreferencesRepository(context).saveWorkspace(it) } }
    }
    private fun angle() = rule.onNodeWithTag("wheel").fetchSemanticsNode().config[RotationAngleKey]
    private fun assertSnapped() {
        val node = rule.onNodeWithTag("wheel").fetchSemanticsNode()
        assertEquals(targetRotationForPanel(node.config[SelectedPanelKey], 6), node.config[RotationAngleKey], .01f)
    }

    @Test fun fiftyContinuousSelectionCycles() {
        repeat(50) { cycle ->
            val initial = angle()
            rule.mainClock.autoAdvance = false
            rule.onNodeWithTag("handle").performTouchInput { click() }
            rule.mainClock.advanceTimeBy(160)
            val progress = rule.onNodeWithTag("wheel").fetchSemanticsNode().config[CarouselProgressKey]
            assertTrue("Entry must interpolate, not cut", progress > 0f && progress < 1f)
            assertEquals(initial, angle(), .01f)
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
            rule.onNodeWithTag("wheel").performTouchInput {
                swipe(Offset(150f, centerY), Offset(width - 150f, centerY), 180)
            }
            rule.waitForIdle(); assertSnapped()
            val target = rule.onNodeWithTag("wheel").fetchSemanticsNode().config[SelectedPanelKey]
            rule.onNodeWithTag("panel-$target").performClick()
            rule.waitForIdle()
            val node = rule.onNodeWithTag("wheel").fetchSemanticsNode()
            assertEquals(target, node.config[SelectedPanelKey])
            assertEquals("NORMAL", node.config[LauncherModeKey])
            assertEquals(0f, node.config[CarouselProgressKey], .001f)
            assertSnapped()
        }
        rule.onNodeWithTag("handle").performTouchInput { click() }; rule.waitForIdle()
        val selected = rule.onNodeWithTag("wheel").fetchSemanticsNode().config[SelectedPanelKey]
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        val node = rule.onNodeWithTag("wheel").fetchSemanticsNode()
        assertEquals("NORMAL", node.config[LauncherModeKey])
        assertEquals(selected, node.config[SelectedPanelKey])
    }

    @Test fun midpointAndDirectionReversal() {
        rule.onNodeWithTag("handle").performTouchInput { click() }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("wheel").performTouchInput { down(center); moveBy(Offset(100f, 0f), 300) }
        rule.mainClock.advanceTimeByFrame()
        val remaining = (30f - angle()) / DefaultCarouselConfig.dragSensitivity
        rule.onNodeWithTag("wheel").performTouchInput { moveBy(Offset(remaining, 0f), 400); advanceEventTime(300) }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(30f, angle(), .5f)
        rule.onNodeWithTag("wheel").performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle(); assertSnapped()
        repeat(8) { cycle ->
            rule.mainClock.autoAdvance = false
            rule.onNodeWithTag("wheel").performTouchInput {
                val forward = cycle % 2 == 0
                swipe(Offset(if (forward) 150f else width - 150f, centerY),
                    Offset(if (forward) width - 150f else 150f, centerY), 80)
            }
            rule.mainClock.advanceTimeBy(64)
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle(); assertSnapped()
    }

    @Test fun dragFlickHandleAndTwentyTurnsEachDirection() {
        rule.onNodeWithTag("handle").performTouchInput { click() }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val before = angle()
        rule.onNodeWithTag("wheel").performTouchInput {
            down(center); moveBy(Offset(100f, 0f), 100); moveBy(Offset(120f, 0f), 100)
        }
        rule.mainClock.advanceTimeByFrame()
        val halfway = angle()
        rule.onNodeWithTag("wheel").performTouchInput { moveBy(Offset(100f, 0f), 100) }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(100f * DefaultCarouselConfig.dragSensitivity, angle() - halfway, .5f)
        assertTrue(angle() > before)
        rule.onNodeWithTag("wheel").performTouchInput { advanceEventTime(300); up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle(); assertSnapped()

        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("wheel").performTouchInput {
            swipe(Offset(150f, centerY), Offset(width - 150f, centerY), 80)
        }
        rule.mainClock.advanceTimeByFrame()
        val released = angle()
        rule.mainClock.advanceTimeBy(100)
        assertTrue("Flick must keep moving after release", abs(angle() - released) > 5f)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle(); assertSnapped()

        rule.onNodeWithTag("handle").performTouchInput {
            swipe(Offset(width * .15f, height * .25f), Offset(width * .85f, height * .25f), 300)
        }
        rule.waitForIdle(); assertSnapped()

        // At least 20 full revolutions each way, measuring actual direct drag travel.
        for (direction in listOf(1f, -1f)) {
            var total = 0f
            var gestures = 0
            while (total < 7200f && gestures < 100) {
                rule.mainClock.autoAdvance = false
                val initial = angle()
                rule.onNodeWithTag("wheel").performTouchInput {
                    down(Offset(if (direction > 0) 100f else width - 100f, centerY))
                    repeat(8) { moveBy(Offset(direction * 85f, 0f), 100) }
                }
                rule.mainClock.advanceTimeByFrame()
                val travel = angle() - initial
                assertTrue("Wrong drag direction", travel * direction > 0)
                total += abs(travel)
                rule.onNodeWithTag("wheel").performTouchInput { advanceEventTime(300); up() }
                rule.mainClock.autoAdvance = true
                rule.waitForIdle(); assertSnapped()
                gestures++
            }
            assertTrue("Must complete 20 full turns", total >= 7200f)
        }
    }
}
