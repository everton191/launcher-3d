package br.com.ne3d.spbshellmodern

import android.os.Bundle
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.ne3d.spbshellmodern.ui.HomeScreen
import br.com.ne3d.spbshellmodern.shell3d.ShellPrototypeScreen
import br.com.ne3d.spbshellmodern.model.initialPanels
import br.com.ne3d.spbshellmodern.shell3d.effects.PanelEffectDebug

class MainActivity : ComponentActivity() {
    private var homeRequestId by mutableIntStateOf(0)
    private var shell3dPrototype by mutableStateOf(false)
    private var shell3dRealPanel by mutableStateOf(true)
    private var shell3dDebugWidgetScene by mutableStateOf(false)
    private var prototypePanelId by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.BLACK

        applyLaunchIntent(intent)
        setContent {
            Log.i("Shell3D.Capture", "prototype=$shell3dPrototype realPanel=$shell3dRealPanel")
            if (shell3dPrototype || shell3dDebugWidgetScene) {
                // The explicit prototype intent is also used for device validation.
                // Its cards must therefore leave the overview just like HomeScreen's carousel.
                ShellPrototypeScreen(initialPanels(), includeRealPanels = shell3dRealPanel, debugWidgetScene = shell3dDebugWidgetScene, onExit = { id ->
                    prototypePanelId = id
                    shell3dPrototype = false
                })
            }
            else HomeScreen(homeRequestId, prototypePanelId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) homeRequestId++
    }

    private fun applyLaunchIntent(intent: Intent) {
        shell3dPrototype = intent.getBooleanExtra(EXTRA_SHELL3D_PROTOTYPE, false)
        shell3dRealPanel = intent.getBooleanExtra(EXTRA_SHELL3D_REAL_PANEL, true)
        shell3dDebugWidgetScene = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_SHELL3D_DEBUG_WIDGET_SCENE, false)
        if (BuildConfig.DEBUG) PanelEffectDebug.select(intent.getStringExtra(EXTRA_SHELL3D_EFFECT)) else PanelEffectDebug.select(null)
    }
}

const val EXTRA_SHELL3D_PROTOTYPE = "br.com.ne3d.spbshellmodern.extra.SHELL3D_PROTOTYPE"
const val EXTRA_SHELL3D_REAL_PANEL = "br.com.ne3d.spbshellmodern.extra.SHELL3D_REAL_PANEL"
const val EXTRA_SHELL3D_EFFECT = "shell3d_effect"
const val EXTRA_SHELL3D_DEBUG_WIDGET_SCENE = "br.com.ne3d.spbshellmodern.extra.SHELL3D_DEBUG_WIDGET_SCENE"
