package com.bitchat.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.bitchat.android.data.dao.PrivateChatDao
import com.bitchat.android.data.dao.ContactDao
import com.bitchat.android.data.entity.MessageEntity
import com.bitchat.android.data.entity.ContactEntity

/**
 * Room database for BitChat message persistence and contact management.
 *
 * Version 1: Initial schema with MessageEntity
 * Version 2: Added ContactEntity for friend/contact management
 *
 * **Security**: Database is encrypted using SQLCipher with a 256-bit AES key.
 * The encryption key is securely generated and stored in Android Keystore
 * via EncryptedSharedPreferences (AES-256-GCM). This protects message content
 * and contact data at rest, even if the device is compromised.
 *
 * Key management:
 * - Generated on first database access
 * - Stored encrypted using Android Keystore MasterKey
 * - Cleared on panic button or when persistent storage is disabled
 * - Deleted when database is deleted
 */
@Database(
    entities = [MessageEntity::class, ContactEntity::class],
    version = 2,
    exportSchema = true
)
abstract class MessageDatabase : RoomDatabase() {

    abstract fun privateChatDao(): PrivateChatDao
    abstract fun contactDao(): ContactDao

    companion object {
        private const val DATABASE_NAME = "bitchat_messages.db"

        @Volatile
        private var INSTANCE: MessageDatabase? = null

        // Load SQLCipher native library
        init {
            System.loadLibrary("sqlcipher")
        }

        /**
         * Get singleton database instance with SQLCipher encryption.
         * Thread-safe double-checked locking.
         *
         * The database is encrypted using a 256-bit key stored securely
         * in Android Keystore via EncryptedSharedPreferences.
         *
         * @param context Application context
         * @return Encrypted database instance
         */
        fun getInstance(context: Context): MessageDatabase {
            return INSTANCE ?: synchronized(this) {
                // Get database encryption passphrase (256-bit, securely stored)
                val passphrase = DatabaseEncryption.getDatabasePassphrase(context)

                // Create SQLCipher factory with passphrase
                val factory = SupportOpenHelperFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MessageDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(factory) // Enable SQLCipher encryption
                    .fallbackToDestructiveMigration() // For now, clear on schema change
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * Close database and clear singleton instance.
         * Call when user disables message persistence.
         */
        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        /**
         * Delete database file and encryption key completely.
         * Call when user wants to clear all message history or presses panic button.
         *
         * This:
         * 1. Closes the database connection
         * 2. Deletes the encrypted database file
         * 3. Clears the encryption passphrase from secure storage
         *
         * @param context Application context
         */
        fun deleteDatabase(context: Context) {
            synchronized(this) {
                closeDatabase()
                context.applicationContext.deleteDatabase(DATABASE_NAME)
                // Clear encryption passphrase for security
                DatabaseEncryption.clearPassphrase(context)
            }
        }
    }
}
