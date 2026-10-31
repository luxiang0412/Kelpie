package dev.herdr.core.data

import android.content.Context

/** 应用级开关（M4：后台监控）。 */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("herdr-app", Context.MODE_PRIVATE)

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean("monitoring", false)
        set(value) = prefs.edit().putBoolean("monitoring", value).apply()
}
