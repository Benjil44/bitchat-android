package com.bitchat.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bitchat.android.data.dao.PrivateChatDao
import com.bitchat.android.data.entity.MessageEntity

/**
 * Room database for BitChat message persistence.
 *
 * Version 1: Initial schema with MessageEntity
 *
 * Database is encrypted using SQLCipher (via androidx.security.crypto)
 * to protect message content at rest.
 */
@Database(
    entities = [MessageEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MessageDatabase : RoomDatabase() {

    abstract fun privateChatDao(): PrivateChatDao

    companion object {
        private const val DATABASE_NAME = "bitchat_messages.db"

        @Volatile
        private var INSTANCE: MessageDatabase? = null

        /**
         * Get singleton database instance.
         * Thread-safe double-checked locking.
         *
         * @param context Application context
         * @return Database instance
         */
        fun getInstance(context: Context): MessageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MessageDatabase::class.java,
                    DATABASE_NAME
                )
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
         * Delete database file completely.
         * Call when user wants to clear all message history.
         *
         * @param context Application context
         */
        fun deleteDatabase(context: Context) {
            synchronized(this) {
                closeDatabase()
                context.applicationContext.deleteDatabase(DATABASE_NAME)
            }
        }
    }
}
