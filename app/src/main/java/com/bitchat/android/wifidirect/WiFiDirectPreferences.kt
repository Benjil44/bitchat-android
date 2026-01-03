package com.bitchat.android.wifidirect

import android.content.Context
import android.content.SharedPreferences

/**
 * WiFi Direct preferences manager
 * Stores user preference for WiFi Direct enable/disable
 */
object WiFiDirectPreferences {
    private const val PREFS_NAME = "wifi_direct_prefs"
    private const val KEY_WIFI_DIRECT_ENABLED = "wifi_direct_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if WiFi Direct is enabled by user
     * @param context Application context
     * @return true if WiFi Direct enabled, false otherwise (default: false)
     */
    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WIFI_DIRECT_ENABLED, false)
    }

    /**
     * Enable or disable WiFi Direct
     * @param context Application context
     * @param enabled true to enable, false to disable
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_WIFI_DIRECT_ENABLED, enabled)
            .apply()
    }
}
