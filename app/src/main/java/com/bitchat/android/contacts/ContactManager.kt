package com.bitchat.android.contacts

import android.content.Context
import android.util.Log
import com.bitchat.android.data.MessageDatabase
import com.bitchat.android.data.dao.ContactDao
import com.bitchat.android.data.entity.ContactEntity
import com.bitchat.android.identity.HashIDGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ContactManager - Central service for friend/contact management
 *
 * Purpose:
 * - Add/remove/update contacts
 * - Check if peer is a contact (for message filtering)
 * - Manage contact groups and trust levels
 * - Real-time contact list updates
 *
 * Use Case for Iran:
 * - Build trusted network of friends
 * - Filter messages (only show contacts by default)
 * - Organize contacts by groups (family, protest team, etc.)
 * - Quick lookup: is this message from a friend?
 *
 * Thread Safety:
 * - All database operations use coroutines with Dispatchers.IO
 * - Flow provides reactive updates to UI
 *
 * @author BitChat Team
 */
class ContactManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "ContactManager"

    private val database: MessageDatabase by lazy {
        MessageDatabase.getInstance(context)
    }

    private val dao: ContactDao by lazy {
        database.contactDao()
    }

    // ==================== ADD / REMOVE CONTACTS ====================

    /**
     * Add contact by Hash ID
     * Use case: User enters friend's Hash ID manually
     *
     * @param hashID 8-character Hash ID (e.g., "BC7F4A2E")
     * @param customName Optional custom name for this contact
     * @param verificationMethod How was this contact added?
     * @return ContactEntity if added successfully, null if invalid/already exists
     */
    suspend fun addContactByHashID(
        hashID: String,
        customName: String? = null,
        verificationMethod: String = ContactEntity.VERIFICATION_MANUAL_ID
    ): ContactEntity? = withContext(Dispatchers.IO) {
        try {
            // Validate Hash ID format
            if (!HashIDGenerator.isValidHashID(hashID)) {
                Log.w(TAG, "Invalid Hash ID format: $hashID")
                return@withContext null
            }

            // Check if already exists
            val existing = dao.getContactByHashID(hashID)
            if (existing != null) {
                Log.i(TAG, "Contact already exists: $hashID")
                return@withContext existing
            }

            // Create temporary contact (will be filled in when we see them in mesh)
            val contact = ContactEntity(
                hashID = hashID,
                noisePublicKeyHex = "", // Will be filled when they announce
                displayName = customName ?: "Friend $hashID",
                customName = customName,
                verificationMethod = verificationMethod,
                addedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            dao.insertContact(contact)
            Log.i(TAG, "✅ Added contact by Hash ID: $hashID")

            contact
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add contact by Hash ID: ${e.message}", e)
            null
        }
    }

    /**
     * Add contact from mesh peer (when you see them and want to add)
     *
     * @param noisePublicKey Peer's Noise static public key
     * @param signingPublicKey Peer's Ed25519 signing public key
     * @param displayName Their announced nickname
     * @param customName Optional custom name you assign
     * @param peerID Current mesh peer ID
     * @param isTrusted Whether you trust this peer (verified in person)
     * @param verificationMethod How was identity verified?
     */
    suspend fun addContactFromPeer(
        noisePublicKey: ByteArray,
        signingPublicKey: ByteArray?,
        displayName: String,
        customName: String? = null,
        peerID: String? = null,
        isTrusted: Boolean = false,
        verificationMethod: String = ContactEntity.VERIFICATION_MANUAL_ID
    ): ContactEntity? = withContext(Dispatchers.IO) {
        try {
            val contact = ContactEntity.create(
                noisePublicKey = noisePublicKey,
                signingPublicKey = signingPublicKey,
                displayName = displayName,
                customName = customName,
                isTrusted = isTrusted,
                verificationMethod = verificationMethod
            ).copy(
                currentPeerID = peerID,
                isConnected = peerID != null,
                lastSeenAt = System.currentTimeMillis()
            )

            dao.insertContact(contact)
            Log.i(TAG, "✅ Added contact from peer: ${contact.hashID} (${contact.displayName})")

            contact
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add contact from peer: ${e.message}", e)
            null
        }
    }

    /**
     * Remove contact
     */
    suspend fun removeContact(hashID: String): Boolean = withContext(Dispatchers.IO) {
        try {
            dao.deleteContactByHashID(hashID)
            Log.i(TAG, "Removed contact: $hashID")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove contact: ${e.message}", e)
            false
        }
    }

    // ==================== CHECK CONTACT STATUS ====================

    /**
     * Check if peer is a contact (by public key)
     * Use case: Filter incoming messages - only show if from contact
     */
    suspend fun isContact(noisePublicKeyHex: String): Boolean = withContext(Dispatchers.IO) {
        try {
            dao.contactExistsByPublicKey(noisePublicKeyHex)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check contact status: ${e.message}", e)
            false
        }
    }

    /**
     * Check if peer is a contact (by peer ID)
     */
    suspend fun isContactByPeerID(peerID: String): Boolean = withContext(Dispatchers.IO) {
        try {
            dao.getContactByPeerID(peerID) != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check contact by peer ID: ${e.message}", e)
            false
        }
    }

    /**
     * Check if contact is blocked
     */
    suspend fun isBlocked(hashID: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val contact = dao.getContactByHashID(hashID)
            contact?.isBlocked ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check blocked status: ${e.message}", e)
            false
        }
    }

    // ==================== GET CONTACTS ====================

    /**
     * Get all contacts (reactive Flow for UI)
     */
    fun getAllContactsFlow(): Flow<List<ContactEntity>> {
        return dao.getAllContactsFlow()
    }

    /**
     * Get all contacts (one-time)
     */
    suspend fun getAllContacts(): List<ContactEntity> = withContext(Dispatchers.IO) {
        dao.getAllContacts()
    }

    /**
     * Get contact by Hash ID
     */
    suspend fun getContact(hashID: String): ContactEntity? = withContext(Dispatchers.IO) {
        dao.getContactByHashID(hashID)
    }

    /**
     * Get contact by public key
     */
    suspend fun getContactByPublicKey(noisePublicKey: ByteArray): ContactEntity? = withContext(Dispatchers.IO) {
        val hex = noisePublicKey.joinToString("") { "%02x".format(it) }
        dao.getContactByNoisePublicKey(hex)
    }

    /**
     * Get contact by peer ID
     */
    suspend fun getContactByPeerID(peerID: String): ContactEntity? = withContext(Dispatchers.IO) {
        dao.getContactByPeerID(peerID)
    }

    /**
     * Search contacts by name
     */
    suspend fun searchContacts(query: String): List<ContactEntity> = withContext(Dispatchers.IO) {
        dao.searchContacts(query)
    }

    // ==================== UPDATE CONTACT INFO ====================

    /**
     * Update contact's peer ID (when they reconnect to mesh)
     * Call this when you see a contact in mesh
     */
    fun updateContactPeerID(noisePublicKey: ByteArray, peerID: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val hex = noisePublicKey.joinToString("") { "%02x".format(it) }
                dao.updatePeerID(hex, peerID)
                Log.d(TAG, "Updated contact peer ID: ${peerID.take(8)}...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update peer ID: ${e.message}", e)
            }
        }
    }

    /**
     * Mark contact as disconnected
     */
    fun markContactDisconnected(peerID: String) {
        scope.launch(Dispatchers.IO) {
            try {
                dao.markDisconnected(peerID)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark disconnected: ${e.message}", e)
            }
        }
    }

    /**
     * Update last seen timestamp
     */
    fun updateLastSeen(hashID: String) {
        scope.launch(Dispatchers.IO) {
            try {
                dao.updateLastSeen(hashID)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update last seen: ${e.message}", e)
            }
        }
    }

    /**
     * Update when contact sends/receives message
     */
    fun updateLastMessage(hashID: String) {
        scope.launch(Dispatchers.IO) {
            try {
                dao.updateLastMessage(hashID)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update last message: ${e.message}", e)
            }
        }
    }

    /**
     * Increment unread message count
     */
    fun incrementUnreadCount(hashID: String) {
        scope.launch(Dispatchers.IO) {
            try {
                dao.incrementUnreadCount(hashID)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to increment unread: ${e.message}", e)
            }
        }
    }

    /**
     * Clear unread messages (mark as read)
     */
    fun clearUnreadCount(hashID: String) {
        scope.launch(Dispatchers.IO) {
            try {
                dao.clearUnreadCount(hashID)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear unread: ${e.message}", e)
            }
        }
    }

    /**
     * Update display name (when they change their nickname)
     */
    fun updateDisplayName(hashID: String, displayName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                dao.updateDisplayName(hashID, displayName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update display name: ${e.message}", e)
            }
        }
    }

    /**
     * Update custom name (your nickname for them)
     */
    fun updateCustomName(hashID: String, customName: String?) {
        scope.launch(Dispatchers.IO) {
            try {
                dao.updateCustomName(hashID, customName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update custom name: ${e.message}", e)
            }
        }
    }

    // ==================== CONTACT MANAGEMENT ====================

    /**
     * Toggle favorite status
     */
    fun setFavorite(hashID: String, isFavorite: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                dao.setFavorite(hashID, isFavorite)
                Log.i(TAG, "Set favorite: $hashID = $isFavorite")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set favorite: ${e.message}", e)
            }
        }
    }

    /**
     * Block/unblock contact
     */
    fun setBlocked(hashID: String, isBlocked: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                dao.setBlocked(hashID, isBlocked)
                Log.i(TAG, "Set blocked: $hashID = $isBlocked")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set blocked: ${e.message}", e)
            }
        }
    }

    /**
     * Set trust status (verified identity)
     */
    fun setTrusted(hashID: String, isTrusted: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                dao.setTrusted(hashID, isTrusted)
                Log.i(TAG, "Set trusted: $hashID = $isTrusted")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set trusted: ${e.message}", e)
            }
        }
    }

    // ==================== STATISTICS ====================

    /**
     * Get contact statistics
     */
    suspend fun getContactStats(): ContactStats = withContext(Dispatchers.IO) {
        try {
            ContactStats(
                total = dao.getContactCount(),
                trusted = dao.getTrustedContactCount(),
                connected = dao.getConnectedContactCount(),
                unreadCount = dao.getTotalUnreadCount() ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get stats: ${e.message}", e)
            ContactStats()
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Sync contact with mesh peer info
     * Call when peer announces in mesh
     */
    suspend fun syncWithMeshPeer(
        peerID: String,
        noisePublicKey: ByteArray,
        signingPublicKey: ByteArray?,
        displayName: String
    ) = withContext(Dispatchers.IO) {
        try {
            val hex = noisePublicKey.joinToString("") { "%02x".format(it) }
            val existing = dao.getContactByNoisePublicKey(hex)

            if (existing != null) {
                // Update existing contact
                val updated = existing.copy(
                    currentPeerID = peerID,
                    isConnected = true,
                    displayName = displayName, // Update if they changed nickname
                    lastSeenAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.updateContact(updated)
                Log.d(TAG, "Synced contact with mesh peer: ${existing.hashID}")
            }
            // If not a contact, don't add automatically
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync with mesh peer: ${e.message}", e)
        }
    }

    /**
     * Generate my Hash ID (for sharing with others)
     */
    fun getMyHashID(myNoisePublicKey: ByteArray): String {
        return HashIDGenerator.generateHashID(myNoisePublicKey)
    }

    /**
     * Generate QR code URI for my identity
     */
    fun getMyQRCodeURI(myNoisePublicKey: ByteArray): String {
        return HashIDGenerator.generateQRCodeURI(myNoisePublicKey)
    }
}

/**
 * Contact statistics data class
 */
data class ContactStats(
    val total: Int = 0,
    val trusted: Int = 0,
    val connected: Int = 0,
    val unreadCount: Int = 0
)
