package com.bitchat.android.data

import android.content.Context

/**
 * Preferences for persistent message storage
 * Controls whether messages are saved to database or kept ephemeral
 */
object StoragePreferences {
    private const val PREFS_NAME = "storage_prefs"
    private const val KEY_PERSISTENT_ENABLED = "persistent_storage_enabled"

    /**
     * Check if persistent storage is enabled
     * Default: false (privacy-first, ephemeral mode)
     */
    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERSISTENT_ENABLED, false)
    }

    /**
     * Enable or disable persistent storage
     * When disabled, existing database is NOT automatically cleared
     * User must manually clear via panic button or settings
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERSISTENT_ENABLED, enabled)
            .apply()
    }
}
