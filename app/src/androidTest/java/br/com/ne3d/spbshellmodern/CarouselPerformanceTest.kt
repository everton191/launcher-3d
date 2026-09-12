package br.com.ne3d.spbshellmodern

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import br.com.ne3d.spbshellmodern.data.PanelPreferencesRepository
import br.com.ne3d.spbshellmodern.engine.targetRotationForPanel
import br.com.ne3d.spbshellmodern.model.*
import br.com.ne3d.spbshellmodern.ui.RotationAngleKey
import br.com.ne3d.spbshellmodern.ui.SelectedPanelKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CarouselPerformanceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun panels(count: Int): List<LauncherPanel> {
        val base = initialPanels().toMutableList()
        var next = 1
        while (base.size < count) {
            val type = listOf(PanelType.FAVORITES, PanelType.AGENDA, PanelType.PHOTOS, PanelType.WEATHER)[next % 4]
            base += panelTemplate(type, "profile-${type.name.lowercase()}-${next++}")
        }
        return base
    }

    @Test fun continuousMotionForSixTenAndTwelvePanels() {
        try {
            for (count in listOf(6, 10, 12)) {
                val set = panels(count)
                runBlocking { PanelPreferencesRepository(rule.activity).save(set, "home") }
                rule.activityRule.scenario.recreate(); rule.waitForIdle()
                rule.onNodeWithTag("handle").performClick(); rule.waitForIdle()
                val start = SystemClock.elapsedRealtime()
                var gestures = 0
                while (SystemClock.elapsedRealtime() - start < 60_000) {
                    val right = gestures % 2 == 0
                    rule.onNodeWithTag("wheel").performTouchInput {
                        swipe(
                            Offset(if (right) 170f else width - 170f, centerY),
                            Offset(if (right) width - 170f else 170f, centerY),
                            160
                        )
                    }
                    gestures++
                }
                rule.waitForIdle()
                val node = rule.onNodeWithTag("wheel").fetchSemanticsNode()
                val selected = node.config[SelectedPanelKey]
                assertEquals(targetRotationForPanel(selected, count), node.config[RotationAngleKey], .01f)
                Log.i("SPB_PROFILE", "panels=$count durationMs=${SystemClock.elapsedRealtime() - start} gestures=$gestures selected=$selected")
            }
        } finally {
            runBlocking { PanelPreferencesRepository(rule.activity).save(initialPanels(), "home") }
        }
    }
}
