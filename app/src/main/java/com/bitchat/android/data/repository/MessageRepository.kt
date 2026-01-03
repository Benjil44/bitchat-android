package com.bitchat.android.data.repository

import android.content.Context
import android.util.Log
import com.bitchat.android.data.MessageDatabase
import com.bitchat.android.data.entity.MessageEntity
import com.bitchat.android.model.BitchatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing persistent message storage
 * Provides high-level API for saving/loading messages from Room Database
 */
class MessageRepository(private val context: Context) {

    companion object {
        private const val TAG = "MessageRepository"
        private const val MAX_MESSAGES_PER_CHAT = 1000  // Limit messages to prevent database bloat
        private const val MESSAGE_RETENTION_DAYS = 30   // Auto-delete messages older than 30 days

        @Volatile
        private var INSTANCE: MessageRepository? = null

        fun getInstance(context: Context): MessageRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MessageRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val database by lazy { MessageDatabase.getInstance(context) }
    private val dao by lazy { database.privateChatDao() }

    /**
     * Save a message to the database
     * @param message The message to save
     * @param peerID The conversation ID (peer ID for private chats)
     */
    suspend fun saveMessage(message: BitchatMessage, peerID: String) {
        withContext(Dispatchers.IO) {
            try {
                val entity = MessageEntity.from(message, peerID)
                dao.insertMessage(entity)
                Log.d(TAG, "Saved message ${message.id} to database (peer: $peerID)")

                // Cleanup old messages if exceeding limit
                cleanupOldMessagesIfNeeded(peerID)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save message: ${e.message}")
            }
        }
    }

    /**
     * Load all messages for a conversation
     * @param peerID The conversation ID
     * @return List of messages, sorted chronologically
     */
    suspend fun loadMessages(peerID: String): List<BitchatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val entities = dao.getAllMessages(peerID)
                entities.map { it.toBitchatMessage() }.also {
                    Log.d(TAG, "Loaded ${it.size} messages from database (peer: $peerID)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load messages: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Get all peer IDs that have stored conversations
     * @return List of peer IDs
     */
    suspend fun getAllConversationPeerIDs(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                dao.getAllPeerIDs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get peer IDs: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Delete a conversation
     * @param peerID The conversation to delete
     */
    suspend fun deleteConversation(peerID: String) {
        withContext(Dispatchers.IO) {
            try {
                val deleted = dao.deleteConversation(peerID)
                Log.d(TAG, "Deleted $deleted messages for peer $peerID")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete conversation: ${e.message}")
            }
        }
    }

    /**
     * Delete all messages (panic button / emergency clear)
     */
    suspend fun deleteAllMessages() {
        withContext(Dispatchers.IO) {
            try {
                val deleted = dao.deleteAllMessages()
                Log.d(TAG, "Deleted all $deleted messages from database")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete all messages: ${e.message}")
            }
        }
    }

    /**
     * Cleanup old messages to prevent database bloat
     * Deletes messages if:
     * - More than MAX_MESSAGES_PER_CHAT exist for this peer
     * - Messages are older than MESSAGE_RETENTION_DAYS
     */
    private suspend fun cleanupOldMessagesIfNeeded(peerID: String) {
        try {
            // Check message count
            val count = dao.getMessageCount(peerID)
            if (count > MAX_MESSAGES_PER_CHAT) {
                // Keep newest MAX_MESSAGES_PER_CHAT, delete rest
                val messages = dao.getAllMessages(peerID)
                val toDelete = messages.take(count - MAX_MESSAGES_PER_CHAT)
                toDelete.forEach { dao.deleteMessage(it.id) }
                Log.d(TAG, "Cleaned up ${toDelete.size} old messages for $peerID (limit: $MAX_MESSAGES_PER_CHAT)")
            }

            // Delete messages older than retention period
            val retentionMillis = System.currentTimeMillis() - (MESSAGE_RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            val deletedOld = dao.deleteMessagesOlderThan(retentionMillis)
            if (deletedOld > 0) {
                Log.d(TAG, "Deleted $deletedOld messages older than $MESSAGE_RETENTION_DAYS days")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old messages: ${e.message}")
        }
    }

    /**
     * Search messages by content
     * @param query Search query
     * @param peerID Optional peer ID to limit search to specific conversation
     * @return List of matching messages
     */
    suspend fun searchMessages(query: String, peerID: String? = null): List<BitchatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val entities = dao.searchMessages(query, peerID)
                entities.map { it.toBitchatMessage() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search messages: ${e.message}")
                emptyList()
            }
        }
    }
}
