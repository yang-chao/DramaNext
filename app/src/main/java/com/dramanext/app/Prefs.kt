package com.dramanext.app

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("dramanext", Context.MODE_PRIVATE)

    var actEnabled: Boolean
        get() = sp.getBoolean(KEY_ACT, true)
        set(v) = sp.edit().putBoolean(KEY_ACT, v).apply()

    var probeEnabled: Boolean
        get() = sp.getBoolean(KEY_PROBE, true)
        set(v) = sp.edit().putBoolean(KEY_PROBE, v).apply()

    val nextCount: Int get() = sp.getInt(KEY_COUNT, 0)

    val lastEvent: String get() = sp.getString(KEY_LAST, "") ?: ""

    fun recordNext(event: String) {
        sp.edit().putInt(KEY_COUNT, nextCount + 1).putString(KEY_LAST, event).apply()
    }

    fun recordEvent(event: String) {
        sp.edit().putString(KEY_LAST, event).apply()
    }

    companion object {
        private const val KEY_ACT = "act_enabled"
        private const val KEY_PROBE = "probe_enabled"
        private const val KEY_COUNT = "next_count"
        private const val KEY_LAST = "last_event"
    }
}
