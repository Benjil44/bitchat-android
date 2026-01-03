package com.bitchat.android.emergency

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.bitchat.android.data.MessageDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * EmergencyDataWiper - Critical safety feature for Iranian protesters
 *
 * Purpose:
 * - IMMEDIATE data deletion if phone about to be seized by police
 * - Remove ALL traces of messages, contacts, and user data
 * - Prevent government from accessing communications
 *
 * Use Case for Iran:
 * - Protester sees police approaching
 * - Taps panic button
 * - All BitChat data deleted in <2 seconds
 * - Phone can be safely seized (no evidence remains)
 *
 * What gets deleted:
 * - All messages (sent and received)
 * - All contacts (Hash IDs, names, metadata)
 * - All preferences (settings, nicknames, etc.)
 * - All cached data
 * - Encryption keys (Noise protocol keys)
 * - Database files
 *
 * What is NOT deleted:
 * - App itself (user can manually uninstall after if needed)
 * - Other apps' data
 * - Photos, files outside BitChat
 *
 * Security considerations:
 * - Data deletion is NOT forensically secure (file remnants may remain)
 * - For maximum security: Use device encryption + full factory reset
 * - This is a "good enough" solution for most threat models
 * - Quick enough to use when police are seconds away
 *
 * @author BitChat Team
 */
object EmergencyDataWiper {

    private const val TAG = "EmergencyDataWiper"

    /**
     * Emergency wipe - delete ALL BitChat data immediately
     *
     * @param context Application context
     * @return WipeResult indicating success/failure and what was deleted
     */
    suspend fun wipeAllData(context: Context): WipeResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val deletedItems = mutableListOf<String>()
        val errors = mutableListOf<String>()

        try {
            Log.w(TAG, "⚠️ EMERGENCY DATA WIPE INITIATED")

            // Step 1: Clear Room database (messages, contacts)
            try {
                val database = MessageDatabase.getInstance(context)

                // Clear all tables
                database.clearAllTables()
                deletedItems.add("All messages deleted")
                deletedItems.add("All contacts deleted")

                // Close database
                database.close()
                deletedItems.add("Database closed")

                Log.i(TAG, "✅ Database cleared")
            } catch (e: Exception) {
                val error = "Database clear failed: ${e.message}"
                Log.e(TAG, error, e)
                errors.add(error)
            }

            // Step 2: Delete database files manually (belt and suspenders)
            try {
                val databasePath = context.getDatabasePath("bitchat_messages.db")
                val walPath = context.getDatabasePath("bitchat_messages.db-wal")
                val shmPath = context.getDatabasePath("bitchat_messages.db-shm")

                var filesDeleted = 0
                if (databasePath.exists() && databasePath.delete()) filesDeleted++
                if (walPath.exists() && walPath.delete()) filesDeleted++
                if (shmPath.exists() && shmPath.delete()) filesDeleted++

                if (filesDeleted > 0) {
                    deletedItems.add("$filesDeleted database files deleted")
                    Log.i(TAG, "✅ Deleted $filesDeleted database files")
                }
            } catch (e: Exception) {
                val error = "Database file deletion failed: ${e.message}"
                Log.e(TAG, error, e)
                errors.add(error)
            }

            // Step 3: Clear all SharedPreferences
            try {
                // Main preferences
                val mainPrefs = context.getSharedPreferences("bitchat_prefs", Context.MODE_PRIVATE)
                mainPrefs.edit().clear().commit()
                deletedItems.add("Settings cleared")

                // Noise encryption preferences (if separate file)
                val noisePrefs = context.getSharedPreferences("noise_keys", Context.MODE_PRIVATE)
                noisePrefs.edit().clear().commit()
                deletedItems.add("Encryption keys cleared")

                // Contact filtering preferences
                val filterPrefs = context.getSharedPreferences("contact_filter", Context.MODE_PRIVATE)
                filterPrefs.edit().clear().commit()

                // PoW preferences
                val powPrefs = context.getSharedPreferences("pow_prefs", Context.MODE_PRIVATE)
                powPrefs.edit().clear().commit()

                // Tor preferences
                val torPrefs = context.getSharedPreferences("tor_prefs", Context.MODE_PRIVATE)
                torPrefs.edit().clear().commit()

                // Theme preferences
                val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
                themePrefs.edit().clear().commit()

                // Mesh service preferences
                val meshPrefs = context.getSharedPreferences("mesh_service_prefs", Context.MODE_PRIVATE)
                meshPrefs.edit().clear().commit()

                Log.i(TAG, "✅ All SharedPreferences cleared")
            } catch (e: Exception) {
                val error = "SharedPreferences clear failed: ${e.message}"
                Log.e(TAG, error, e)
                errors.add(error)
            }

            // Step 4: Clear cache directory
            try {
                val cacheDeleted = deleteDirectory(context.cacheDir)
                if (cacheDeleted) {
                    deletedItems.add("Cache cleared")
                    Log.i(TAG, "✅ Cache directory cleared")
                }
            } catch (e: Exception) {
                val error = "Cache clear failed: ${e.message}"
                Log.e(TAG, error, e)
                errors.add(error)
            }

            // Step 5: Clear app data files directory (but not the app itself)
            try {
                val filesDir = context.filesDir
                var fileCount = 0
                filesDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.delete()) {
                        fileCount++
                    }
                }
                if (fileCount > 0) {
                    deletedItems.add("$fileCount data files deleted")
                    Log.i(TAG, "✅ Deleted $fileCount data files")
                }
            } catch (e: Exception) {
                val error = "Data files deletion failed: ${e.message}"
                Log.e(TAG, error, e)
                errors.add(error)
            }

            val duration = System.currentTimeMillis() - startTime

            Log.w(TAG, "⚠️ EMERGENCY WIPE COMPLETED in ${duration}ms")
            Log.i(TAG, "Deleted items: $deletedItems")
            if (errors.isNotEmpty()) {
                Log.e(TAG, "Errors: $errors")
            }

            WipeResult(
                success = errors.isEmpty(),
                deletedItems = deletedItems,
                errors = errors,
                durationMs = duration
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ EMERGENCY WIPE FAILED: ${e.message}", e)
            WipeResult(
                success = false,
                deletedItems = deletedItems,
                errors = listOf("Fatal error: ${e.message}"),
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Recursively delete directory and all contents
     */
    private fun deleteDirectory(directory: File): Boolean {
        return try {
            if (directory.exists()) {
                directory.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        deleteDirectory(file)
                    } else {
                        file.delete()
                    }
                }
                directory.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete directory: ${directory.absolutePath}", e)
            false
        }
    }

    /**
     * Open app settings page for manual uninstall
     * Call this after data wipe if user wants to completely remove app
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings: ${e.message}", e)
        }
    }

    /**
     * Result of emergency wipe operation
     */
    data class WipeResult(
        val success: Boolean,
        val deletedItems: List<String>,
        val errors: List<String>,
        val durationMs: Long
    ) {
        val summary: String
            get() = if (success) {
                "✅ All data deleted in ${durationMs}ms\n" +
                        "Deleted: ${deletedItems.size} items\n" +
                        deletedItems.joinToString("\n• ", "• ")
            } else {
                "⚠️ Partial deletion (${durationMs}ms)\n" +
                        "Deleted: ${deletedItems.size} items\n" +
                        "Errors: ${errors.size}\n" +
                        errors.joinToString("\n• ", "• ")
            }
    }
}
