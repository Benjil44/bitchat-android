package com.bitchat.android.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import android.util.Base64

/**
 * Database encryption key management for SQLCipher.
 *
 * Generates and securely stores a 256-bit database encryption key using
 * Android Keystore and EncryptedSharedPreferences.
 *
 * The encryption key is:
 * - Generated once on first use
 * - Stored encrypted using Android Keystore MasterKey
 * - Persistent across app sessions
 * - Cleared on panic button or when persistent storage is disabled
 */
object DatabaseEncryption {
    private const val TAG = "DatabaseEncryption"
    private const val PREFS_NAME = "bitchat_db_encryption"
    private const val KEY_DATABASE_PASSPHRASE = "database_passphrase"
    private const val PASSPHRASE_LENGTH = 32 // 256 bits

    /**
     * Get or generate the database encryption passphrase.
     *
     * @param context Application context
     * @return 256-bit passphrase as ByteArray
     */
    fun getDatabasePassphrase(context: Context): ByteArray {
        try {
            val prefs = getEncryptedPreferences(context)

            // Check if passphrase already exists
            val storedPassphrase = prefs.getString(KEY_DATABASE_PASSPHRASE, null)
            if (storedPassphrase != null) {
                Log.d(TAG, "Using existing database passphrase")
                return Base64.decode(storedPassphrase, Base64.NO_WRAP)
            }

            // Generate new passphrase
            Log.d(TAG, "Generating new database passphrase")
            val passphrase = generatePassphrase()

            // Store encrypted
            prefs.edit()
                .putString(KEY_DATABASE_PASSPHRASE, Base64.encodeToString(passphrase, Base64.NO_WRAP))
                .apply()

            return passphrase
        } catch (e: Exception) {
            Log.e(TAG, "Error getting database passphrase", e)
            throw RuntimeException("Failed to get database encryption key", e)
        }
    }

    /**
     * Clear the database encryption passphrase.
     * Call when user disables persistent storage or presses panic button.
     *
     * @param context Application context
     */
    fun clearPassphrase(context: Context) {
        try {
            val prefs = getEncryptedPreferences(context)
            prefs.edit().remove(KEY_DATABASE_PASSPHRASE).apply()
            Log.d(TAG, "✅ Database passphrase cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing database passphrase", e)
        }
    }

    /**
     * Generate a cryptographically secure random passphrase.
     *
     * @return 256-bit random passphrase
     */
    private fun generatePassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_LENGTH)
        SecureRandom().nextBytes(passphrase)
        return passphrase
    }

    /**
     * Get EncryptedSharedPreferences instance using Android Keystore.
     *
     * @param context Application context
     * @return Encrypted preferences
     */
    private fun getEncryptedPreferences(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
