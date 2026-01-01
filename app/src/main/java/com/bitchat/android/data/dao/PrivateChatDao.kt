package com.bitchat.android.data.dao

import androidx.room.*
import com.bitchat.android.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for private chat messages.
 *
 * Provides methods for querying, inserting, and deleting messages
 * with pagination support for efficient loading.
 */
@Dao
interface PrivateChatDao {

    /**
     * Get messages for a specific peer with pagination.
     * Messages are ordered by timestamp in descending order (newest first).
     *
     * @param peerID The peer whose messages to retrieve
     * @param limit Maximum number of messages to return
     * @param offset Number of messages to skip (for pagination)
     * @return List of messages
     */
    @Query("SELECT * FROM messages WHERE peerID = :peerID ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessages(peerID: String, limit: Int, offset: Int): List<MessageEntity>

    /**
     * Get all messages for a specific peer.
     * Ordered chronologically (oldest first) for conversation display.
     *
     * @param peerID The peer whose messages to retrieve
     * @return List of all messages
     */
    @Query("SELECT * FROM messages WHERE peerID = :peerID ORDER BY timestamp ASC")
    suspend fun getAllMessages(peerID: String): List<MessageEntity>

    /**
     * Get all messages for a specific peer as a Flow.
     * Automatically updates when database changes.
     *
     * @param peerID The peer whose messages to retrieve
     * @return Flow of message lists
     */
    @Query("SELECT * FROM messages WHERE peerID = :peerID ORDER BY timestamp ASC")
    fun getAllMessagesFlow(peerID: String): Flow<List<MessageEntity>>

    /**
     * Get the total count of messages for a specific peer.
     *
     * @param peerID The peer whose message count to retrieve
     * @return Total number of messages
     */
    @Query("SELECT COUNT(*) FROM messages WHERE peerID = :peerID")
    suspend fun getMessageCount(peerID: String): Int

    /**
     * Get all distinct peer IDs that have messages.
     *
     * @return List of peer IDs with conversations
     */
    @Query("SELECT DISTINCT peerID FROM messages ORDER BY timestamp DESC")
    suspend fun getAllPeerIDs(): List<String>

    /**
     * Insert a single message.
     * If message with same ID exists, replaces it.
     *
     * @param message The message to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    /**
     * Insert multiple messages in a single transaction.
     * More efficient than inserting one by one.
     *
     * @param messages List of messages to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /**
     * Update an existing message.
     * Useful for updating delivery status.
     *
     * @param message The message to update
     */
    @Update
    suspend fun updateMessage(message: MessageEntity)

    /**
     * Delete all messages for a specific peer.
     *
     * @param peerID The peer whose conversation to delete
     * @return Number of messages deleted
     */
    @Query("DELETE FROM messages WHERE peerID = :peerID")
    suspend fun deleteConversation(peerID: String): Int

    /**
     * Delete a specific message by ID.
     *
     * @param messageID The ID of the message to delete
     * @return Number of messages deleted (0 or 1)
     */
    @Query("DELETE FROM messages WHERE id = :messageID")
    suspend fun deleteMessage(messageID: String): Int

    /**
     * Delete all messages from the database.
     * Use with caution!
     *
     * @return Number of messages deleted
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages(): Int

    /**
     * Delete messages older than a specific timestamp.
     * Useful for implementing message retention policies.
     *
     * @param timestampMillis Messages older than this will be deleted
     * @return Number of messages deleted
     */
    @Query("DELETE FROM messages WHERE timestamp < :timestampMillis")
    suspend fun deleteMessagesOlderThan(timestampMillis: Long): Int

    /**
     * Get the most recent message for a specific peer.
     * Useful for displaying conversation previews.
     *
     * @param peerID The peer whose last message to retrieve
     * @return The most recent message, or null if none exist
     */
    @Query("SELECT * FROM messages WHERE peerID = :peerID ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(peerID: String): MessageEntity?

    /**
     * Search messages by content.
     * Performs case-insensitive search within message content.
     *
     * @param query The search query
     * @param peerID Optional peer ID to limit search to specific conversation
     * @return List of matching messages
     */
    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' AND (:peerID IS NULL OR peerID = :peerID) ORDER BY timestamp DESC")
    suspend fun searchMessages(query: String, peerID: String? = null): List<MessageEntity>

    /**
     * Get message count for each peer.
     * Useful for displaying conversation list with message counts.
     *
     * @return Map of peerID to message count
     */
    @Query("SELECT peerID, COUNT(*) as count FROM messages GROUP BY peerID")
    suspend fun getMessageCountsPerPeer(): Map<String, Int>
}
