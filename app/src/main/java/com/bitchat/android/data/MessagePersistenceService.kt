package com.bitchat.android.data

import android.content.Context
import android.util.Log
import com.bitchat.android.data.entity.MessageEntity
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.ui.DataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Service for persisting and loading private chat messages using Room Database.
 *
 * Features:
 * - Opt-in persistence (user-configurable setting)
 * - Automatic message cap (1000 messages per conversation)
 * - Message retention policy (configurable, default 30 days)
 * - Efficient pagination for large conversations
 */
class MessagePersistenceService(
    private val context: Context,
    private val dataManager: DataManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MessagePersistence"
        private const val MAX_MESSAGES_PER_CHAT = 1000
        private const val DEFAULT_RETENTION_DAYS = 30L
    }

    private val database: MessageDatabase by lazy {
        MessageDatabase.getInstance(context)
    }

    private val dao by lazy {
        database.privateChatDao()
    }

    /**
     * Check if message persistence is enabled by user.
     */
    fun isPersistenceEnabled(): Boolean {
        return dataManager.isMessagePersistenceEnabled()
    }

    /**
     * Save a single private message to database.
     * Only saves if persistence is enabled.
     *
     * @param peerID The peer this message is associated with
     * @param message The message to save
     */
    fun saveMessage(peerID: String, message: BitchatMessage) {
        if (!isPersistenceEnabled()) {
            Log.d(TAG, "Persistence disabled, skipping save")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val entity = MessageEntity.from(message, peerID)
                dao.insertMessage(entity)
                Log.d(TAG, "Saved message ${message.id} for peer $peerID")

                // Enforce message cap
                enforceMessageCap(peerID)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save message: ${e.message}", e)
            }
        }
    }

    /**
     * Save multiple messages in a batch.
     * More efficient than saving one by one.
     *
     * @param peerID The peer these messages are associated with
     * @param messages List of messages to save
     */
    fun saveMessages(peerID: String, messages: List<BitchatMessage>) {
        if (!isPersistenceEnabled() || messages.isEmpty()) {
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val entities = messages.map { MessageEntity.from(it, peerID) }
                dao.insertMessages(entities)
                Log.d(TAG, "Saved ${messages.size} messages for peer $peerID")

                // Enforce message cap
                enforceMessageCap(peerID)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save messages: ${e.message}", e)
            }
        }
    }

    /**
     * Load all messages for a specific peer.
     * Returns empty list if persistence is disabled or no messages exist.
     *
     * @param peerID The peer whose messages to load
     * @return List of messages in chronological order
     */
    suspend fun loadMessages(peerID: String): List<BitchatMessage> {
        if (!isPersistenceEnabled()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            try {
                val entities = dao.getAllMessages(peerID)
                Log.d(TAG, "Loaded ${entities.size} messages for peer $peerID")
                entities.map { it.toBitchatMessage() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load messages: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * Load messages with pagination support.
     *
     * @param peerID The peer whose messages to load
     * @param limit Maximum number of messages to return
     * @param offset Number of messages to skip
     * @return List of messages (newest first)
     */
    suspend fun loadMessagesPaginated(
        peerID: String,
        limit: Int = 50,
        offset: Int = 0
    ): List<BitchatMessage> {
        if (!isPersistenceEnabled()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            try {
                val entities = dao.getMessages(peerID, limit, offset)
                entities.map { it.toBitchatMessage() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load paginated messages: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * Load all conversations (peer IDs with messages).
     *
     * @return List of peer IDs
     */
    suspend fun loadAllConversations(): List<String> {
        if (!isPersistenceEnabled()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            try {
                dao.getAllPeerIDs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversations: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * Update message delivery status.
     *
     * @param messageID The message ID to update
     * @param peerID The peer this message belongs to
     * @param newStatus New delivery status
     */
    fun updateMessageStatus(messageID: String, peerID: String, message: BitchatMessage) {
        if (!isPersistenceEnabled()) {
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val entity = MessageEntity.from(message, peerID)
                dao.updateMessage(entity)
                Log.d(TAG, "Updated message $messageID status")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update message status: ${e.message}", e)
            }
        }
    }

    /**
     * Delete a specific conversation.
     *
     * @param peerID The peer whose conversation to delete
     */
    suspend fun deleteConversation(peerID: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val count = dao.deleteConversation(peerID)
                Log.d(TAG, "Deleted $count messages for peer $peerID")
                count
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete conversation: ${e.message}", e)
                0
            }
        }
    }

    /**
     * Delete all messages from database.
     * Used when user disables persistence or wants to clear history.
     */
    suspend fun deleteAllMessages(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val count = dao.deleteAllMessages()
                Log.d(TAG, "Deleted all $count messages")
                count
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete all messages: ${e.message}", e)
                0
            }
        }
    }

    /**
     * Enforce message cap for a conversation.
     * Keeps only the most recent MAX_MESSAGES_PER_CHAT messages.
     *
     * @param peerID The peer whose messages to cap
     */
    private suspend fun enforceMessageCap(peerID: String) {
        withContext(Dispatchers.IO) {
            try {
                val count = dao.getMessageCount(peerID)
                if (count > MAX_MESSAGES_PER_CHAT) {
                    val toDelete = count - MAX_MESSAGES_PER_CHAT
                    Log.d(TAG, "Conversation with $peerID exceeds cap, deleting $toDelete old messages")

                    // Get all messages, delete oldest ones
                    val allMessages = dao.getAllMessages(peerID)
                    val oldestMessages = allMessages.take(toDelete)

                    oldestMessages.forEach { message ->
                        dao.deleteMessage(message.id)
                    }

                    Log.d(TAG, "Deleted $toDelete old messages for peer $peerID")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enforce message cap: ${e.message}", e)
            }
        }
    }

    /**
     * Apply message retention policy.
     * Deletes messages older than the configured retention period.
     */
    suspend fun applyRetentionPolicy() {
        if (!isPersistenceEnabled()) {
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val retentionDays = dataManager.getMessageRetentionDays() ?: DEFAULT_RETENTION_DAYS
                val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays)

                val count = dao.deleteMessagesOlderThan(cutoffTime)
                if (count > 0) {
                    Log.d(TAG, "Retention policy: deleted $count messages older than $retentionDays days")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply retention policy: ${e.message}", e)
            }
        }
    }

    /**
     * Search messages across all conversations or within a specific conversation.
     *
     * @param query Search query
     * @param peerID Optional peer ID to limit search
     * @return List of matching messages
     */
    suspend fun searchMessages(query: String, peerID: String? = null): List<BitchatMessage> {
        if (!isPersistenceEnabled() || query.isBlank()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            try {
                val entities = dao.searchMessages(query, peerID)
                Log.d(TAG, "Search '$query' found ${entities.size} messages")
                entities.map { it.toBitchatMessage() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to search messages: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * Disable persistence and optionally delete all stored messages.
     *
     * @param deleteMessages If true, deletes all messages from database
     */
    suspend fun disablePersistence(deleteMessages: Boolean = false) {
        dataManager.setMessagePersistence(false)

        if (deleteMessages) {
            deleteAllMessages()
            MessageDatabase.deleteDatabase(context)
        }

        Log.d(TAG, "Message persistence disabled")
    }

    /**
     * Enable persistence and optionally load existing messages.
     *
     * @return Number of conversations loaded
     */
    suspend fun enablePersistence(): Int {
        dataManager.setMessagePersistence(true)

        return withContext(Dispatchers.IO) {
            try {
                val conversations = dao.getAllPeerIDs()
                Log.d(TAG, "Message persistence enabled, found ${conversations.size} conversations")
                conversations.size
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable persistence: ${e.message}", e)
                0
            }
        }
    }
}
