package com.mt.organizemessages

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    var isTaggingEnabled: Boolean
        get() = prefs.getBoolean("enable_tagging", true)
        set(value) = prefs.edit().putBoolean("enable_tagging", value).apply()
    var isMetricsEnabled: Boolean
        get() = prefs.getBoolean("enable_metrics", true)
        set(value) = prefs.edit().putBoolean("enable_metrics", value).apply()
}
