package com.bitchat.android.data.dao

import androidx.room.*
import com.bitchat.android.data.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

/**
 * ContactDao - Database operations for contact management
 *
 * Purpose:
 * - CRUD operations for user's contact list
 * - Queries for filtering, searching, and organizing contacts
 * - Real-time updates via Flow (reactive)
 *
 * Use Case for Iran:
 * - Quickly find trusted contacts during protests
 * - Filter messages from non-contacts (reduce noise)
 * - Organize contacts by groups (family, protest team, etc.)
 *
 * @author BitChat Team
 */
@Dao
interface ContactDao {

    // ==================== BASIC CRUD ====================

    /**
     * Insert new contact
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    /**
     * Insert multiple contacts
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    /**
     * Update existing contact
     */
    @Update
    suspend fun updateContact(contact: ContactEntity)

    /**
     * Delete contact
     */
    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    /**
     * Delete contact by Hash ID
     */
    @Query("DELETE FROM contacts WHERE hashID = :hashID")
    suspend fun deleteContactByHashID(hashID: String)

    /**
     * Delete all contacts (for app reset)
     */
    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()

    // ==================== GET BY IDENTIFIER ====================

    /**
     * Get contact by Hash ID (e.g., "BC7F4A2E")
     */
    @Query("SELECT * FROM contacts WHERE hashID = :hashID LIMIT 1")
    suspend fun getContactByHashID(hashID: String): ContactEntity?

    /**
     * Get contact by Noise public key (hex string)
     */
    @Query("SELECT * FROM contacts WHERE noisePublicKeyHex = :noisePublicKeyHex LIMIT 1")
    suspend fun getContactByNoisePublicKey(noisePublicKeyHex: String): ContactEntity?

    /**
     * Get contact by current peer ID (mesh connection)
     */
    @Query("SELECT * FROM contacts WHERE currentPeerID = :peerID LIMIT 1")
    suspend fun getContactByPeerID(peerID: String): ContactEntity?

    /**
     * Check if contact exists by Hash ID
     */
    @Query("SELECT COUNT(*) > 0 FROM contacts WHERE hashID = :hashID")
    suspend fun contactExists(hashID: String): Boolean

    /**
     * Check if contact exists by public key
     */
    @Query("SELECT COUNT(*) > 0 FROM contacts WHERE noisePublicKeyHex = :noisePublicKeyHex")
    suspend fun contactExistsByPublicKey(noisePublicKeyHex: String): Boolean

    // ==================== GET ALL / LISTS ====================

    /**
     * Get all contacts (reactive Flow)
     * Sorted by: favorites first, then last message time
     */
    @Query("""
        SELECT * FROM contacts
        WHERE isBlocked = 0
        ORDER BY
            isFavorite DESC,
            lastMessageAt DESC,
            displayName ASC
    """)
    fun getAllContactsFlow(): Flow<List<ContactEntity>>

    /**
     * Get all contacts (one-time)
     */
    @Query("""
        SELECT * FROM contacts
        WHERE isBlocked = 0
        ORDER BY
            isFavorite DESC,
            lastMessageAt DESC,
            displayName ASC
    """)
    suspend fun getAllContacts(): List<ContactEntity>

    /**
     * Get trusted contacts only
     */
    @Query("""
        SELECT * FROM contacts
        WHERE isTrusted = 1 AND isBlocked = 0
        ORDER BY displayName ASC
    """)
    suspend fun getTrustedContacts(): List<ContactEntity>

    /**
     * Get favorite contacts
     */
    @Query("""
        SELECT * FROM contacts
        WHERE isFavorite = 1 AND isBlocked = 0
        ORDER BY lastMessageAt DESC
    """)
    suspend fun getFavoriteContacts(): List<ContactEntity>

    /**
     * Get blocked contacts
     */
    @Query("SELECT * FROM contacts WHERE isBlocked = 1 ORDER BY displayName ASC")
    suspend fun getBlockedContacts(): List<ContactEntity>

    /**
     * Get currently connected contacts (in mesh)
     */
    @Query("""
        SELECT * FROM contacts
        WHERE isConnected = 1 AND isBlocked = 0
        ORDER BY displayName ASC
    """)
    suspend fun getConnectedContacts(): List<ContactEntity>

    /**
     * Get contacts with unread messages
     */
    @Query("""
        SELECT * FROM contacts
        WHERE unreadCount > 0 AND isBlocked = 0
        ORDER BY lastMessageAt DESC
    """)
    suspend fun getContactsWithUnread(): List<ContactEntity>

    // ==================== SEARCH & FILTER ====================

    /**
     * Search contacts by name (custom name or display name)
     */
    @Query("""
        SELECT * FROM contacts
        WHERE (customName LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%')
        AND isBlocked = 0
        ORDER BY
            isFavorite DESC,
            displayName ASC
    """)
    suspend fun searchContacts(query: String): List<ContactEntity>

    /**
     * Get contacts in a specific group
     */
    @Query("""
        SELECT * FROM contacts
        WHERE groups LIKE '%' || :groupName || '%'
        AND isBlocked = 0
        ORDER BY displayName ASC
    """)
    suspend fun getContactsByGroup(groupName: String): List<ContactEntity>

    // ==================== UPDATE OPERATIONS ====================

    /**
     * Update contact's current peer ID (mesh connection)
     */
    @Query("UPDATE contacts SET currentPeerID = :peerID, isConnected = 1, lastSeenAt = :timestamp, updatedAt = :timestamp WHERE noisePublicKeyHex = :noisePublicKeyHex")
    suspend fun updatePeerID(noisePublicKeyHex: String, peerID: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Mark contact as disconnected
     */
    @Query("UPDATE contacts SET isConnected = 0, updatedAt = :timestamp WHERE currentPeerID = :peerID")
    suspend fun markDisconnected(peerID: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update last seen timestamp
     */
    @Query("UPDATE contacts SET lastSeenAt = :timestamp, updatedAt = :timestamp WHERE hashID = :hashID")
    suspend fun updateLastSeen(hashID: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update last message timestamp
     */
    @Query("UPDATE contacts SET lastMessageAt = :timestamp, updatedAt = :timestamp WHERE hashID = :hashID")
    suspend fun updateLastMessage(hashID: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update unread count
     */
    @Query("UPDATE contacts SET unreadCount = :count, updatedAt = :timestamp WHERE hashID = :hashID")
    suspend fun updateUnreadCount(hashID: String, count: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Increment unread count
     */
    @Query("UPDATE contacts SET unreadCount = unreadCount + 1, lastMessageAt = :timestamp, updatedAt = :timestamp WHERE hashID = :hashID")
    suspend fun incrementUnreadCount(hashID: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Clear unread count (mark as read)
     */
    @Query("UPDATE contacts SET unreadCount = 0, updatedAt = :timestamp WHERE hashID = :hashID")
    suspend fun clearUnreadCount(hashID: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Toggle favorite status
     */
    @Query("UPDATE contacts SET isFavorite = :isFavorite, updatedAt = :timestamp WHERE hashID = :hashID")
    suspend fun setFavorite(hashID: String, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    /**
     * Toggle blocked status
     */
    @Query("UPDATE contacts SET isBlocked = :isBlocked, updatedAt = :timestamp WHERE hashID = :hashID")
    suspend fun setBlocked(hashID: String, isBlocked: Boolean, timestamp: Long = System.currentTimeMillis())

    /**
     * Set trust status
     */
    @Query("UPDATE contacts SET isTrusted = :isTrusted, updatedAt = :timestamp WHERE hashID = :hashID")
    suspend fun setTrusted(hashID: String, isTrusted: Boolean, timestamp: Long = System.currentTimeMillis())

    /**
     * Update display name (when they change their nickname)
     */
    @Query("UPDATE contacts SET displayName = :displayName, updatedAt = :timestamp WHERE hashID = :hashID")
    suspend fun updateDisplayName(hashID: String, displayName: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update custom name (your nickname for them)
     */
    @Query("UPDATE contacts SET customName = :customName, updatedAt = :timestamp WHERE hashID = :hashID")
    suspend fun updateCustomName(hashID: String, customName: String?, timestamp: Long = System.currentTimeMillis())

    // ==================== STATISTICS ====================

    /**
     * Get total contact count
     */
    @Query("SELECT COUNT(*) FROM contacts WHERE isBlocked = 0")
    suspend fun getContactCount(): Int

    /**
     * Get trusted contact count
     */
    @Query("SELECT COUNT(*) FROM contacts WHERE isTrusted = 1 AND isBlocked = 0")
    suspend fun getTrustedContactCount(): Int

    /**
     * Get total unread message count
     */
    @Query("SELECT SUM(unreadCount) FROM contacts WHERE isBlocked = 0")
    suspend fun getTotalUnreadCount(): Int?

    /**
     * Get currently connected contact count
     */
    @Query("SELECT COUNT(*) FROM contacts WHERE isConnected = 1 AND isBlocked = 0")
    suspend fun getConnectedContactCount(): Int
}
