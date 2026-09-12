package br.com.ne3d.spbshellmodern.shell3d

import android.content.Context

/** Internal rollout switch. The Compose carousel remains the default and fallback. */
object Shell3DFeatureFlag {
    private const val PREFS = "shell3d_internal"
    private const val KEY = "useShell3DCarousel"

    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
}
