package com.bitchat.android.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * ContactEntity - Persistent storage for user's contact list
 *
 * Purpose:
 * - Store trusted friends/contacts with cryptographic identity
 * - Enable private message filtering (only show messages from contacts)
 * - Support custom nicknames and contact management
 *
 * Use Case for Iran:
 * - Build trusted network of friends
 * - Filter out spam/unknown messages during protests
 * - Maintain contact relationships across app restarts
 *
 * Identity Hierarchy:
 * 1. noisePublicKey (32 bytes) - Cryptographic identity (never changes)
 * 2. hashID (8 chars) - User-friendly share code (derived from public key)
 * 3. displayName - What they call themselves
 * 4. customName - What YOU call them (optional, for privacy)
 *
 * @author BitChat Team
 */
@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["hashID"], unique = true),
        Index(value = ["noisePublicKeyHex"], unique = true),
        Index(value = ["isTrusted"]),
        Index(value = ["isBlocked"])
    ]
)
data class ContactEntity(
    /**
     * Primary key - Hash ID (8 characters, e.g., "BC7F4A2E")
     * User-friendly identifier for sharing
     */
    @PrimaryKey
    val hashID: String,

    /**
     * Noise static public key (32 bytes, stored as hex string)
     * This is the cryptographic identity - never changes
     */
    val noisePublicKeyHex: String,

    /**
     * Ed25519 signing public key (32 bytes, stored as hex string)
     * Used for verifying identity announcements
     */
    val signingPublicKeyHex: String? = null,

    /**
     * Display name - what they call themselves
     * This can change if they update their nickname
     */
    val displayName: String,

    /**
     * Custom name - what YOU call them (optional)
     * For privacy: "Friend 1" instead of real name
     */
    val customName: String? = null,

    /**
     * Trust status - verified in person or through trusted friend
     * Used for: End-to-end encryption trust, friend-of-friend discovery
     */
    val isTrusted: Boolean = false,

    /**
     * Blocked status - ignored in UI, messages dropped
     */
    val isBlocked: Boolean = false,

    /**
     * Favorite status - pinned to top of contact list
     */
    val isFavorite: Boolean = false,

    /**
     * Contact groups/tags (JSON array of strings)
     * Examples: ["family"], ["protest_team"], ["neighborhood"]
     */
    val groups: String? = null, // JSON: ["group1", "group2"]

    /**
     * Notes about this contact (encrypted locally)
     * For your reference only
     */
    val notes: String? = null,

    /**
     * Verification method
     * Options: "in_person", "qr_scan", "friend_introduction", "manual_id"
     */
    val verificationMethod: String? = null,

    /**
     * When you added this contact
     */
    val addedAt: Long = System.currentTimeMillis(),

    /**
     * Last time you saw them online/in mesh
     */
    val lastSeenAt: Long? = null,

    /**
     * Last message timestamp (for sorting contact list)
     */
    val lastMessageAt: Long? = null,

    /**
     * Unread message count from this contact
     */
    val unreadCount: Int = 0,

    /**
     * Current peer ID (16-hex chars)
     * This can change when they reconnect to mesh
     * Used for active mesh connections only
     */
    val currentPeerID: String? = null,

    /**
     * Whether they are currently connected in mesh
     */
    val isConnected: Boolean = false,

    /**
     * Last updated timestamp
     */
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Convert hex string back to public key bytes
     */
    fun getNoisePublicKey(): ByteArray {
        return noisePublicKeyHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    /**
     * Convert hex string back to signing public key bytes
     */
    fun getSigningPublicKey(): ByteArray? {
        return signingPublicKeyHex?.chunked(2)
            ?.map { it.toInt(16).toByte() }
            ?.toByteArray()
    }

    /**
     * Get display name (custom name if set, otherwise their display name)
     */
    fun getEffectiveName(): String {
        return customName ?: displayName
    }

    /**
     * Get groups as list
     */
    fun getGroupsList(): List<String> {
        if (groups.isNullOrBlank()) return emptyList()

        return try {
            val gson = com.google.gson.Gson()
            gson.fromJson(groups, Array<String>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if contact is in a specific group
     */
    fun isInGroup(groupName: String): Boolean {
        return getGroupsList().contains(groupName)
    }

    companion object {
        /**
         * Create ContactEntity from public key and display name
         */
        fun create(
            noisePublicKey: ByteArray,
            signingPublicKey: ByteArray?,
            displayName: String,
            customName: String? = null,
            isTrusted: Boolean = false,
            verificationMethod: String? = null
        ): ContactEntity {
            val hashID = com.bitchat.android.identity.HashIDGenerator.generateHashID(noisePublicKey)
            val noisePublicKeyHex = noisePublicKey.joinToString("") { "%02x".format(it) }
            val signingPublicKeyHex = signingPublicKey?.joinToString("") { "%02x".format(it) }

            return ContactEntity(
                hashID = hashID,
                noisePublicKeyHex = noisePublicKeyHex,
                signingPublicKeyHex = signingPublicKeyHex,
                displayName = displayName,
                customName = customName,
                isTrusted = isTrusted,
                verificationMethod = verificationMethod,
                addedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        /**
         * Verification method constants
         */
        const val VERIFICATION_IN_PERSON = "in_person"
        const val VERIFICATION_QR_SCAN = "qr_scan"
        const val VERIFICATION_FRIEND_INTRO = "friend_introduction"
        const val VERIFICATION_MANUAL_ID = "manual_id"
    }
}
