package com.bitchat.android.data.repository

import android.content.Context
import android.util.Log
import com.bitchat.android.data.MessageDatabase
import com.bitchat.android.data.entity.ContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for contact management operations.
 *
 * Provides high-level API for:
 * - Adding/updating contacts
 * - Querying contacts
 * - Managing contact relationships
 */
class ContactRepository private constructor(context: Context) {

    private val database = MessageDatabase.getInstance(context)
    private val contactDao = database.contactDao()

    companion object {
        private const val TAG = "ContactRepository"

        @Volatile
        private var INSTANCE: ContactRepository? = null

        fun getInstance(context: Context): ContactRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ContactRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Insert or update a contact
     *
     * @param contact Contact to save
     */
    suspend fun insertOrUpdateContact(contact: ContactEntity) = withContext(Dispatchers.IO) {
        try {
            contactDao.insertContact(contact) // Uses REPLACE strategy = upsert
            Log.d(TAG, "✅ Saved contact: ${contact.displayName} (${contact.hashID})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save contact: ${e.message}", e)
            throw e
        }
    }

    /**
     * Get contact by hash ID
     *
     * @param hashID 8-character hash ID
     * @return Contact or null if not found
     */
    suspend fun getContactByHashID(hashID: String): ContactEntity? = withContext(Dispatchers.IO) {
        contactDao.getContactByHashID(hashID)
    }

    /**
     * Get contact by Noise public key
     *
     * @param noisePublicKeyHex Noise public key as hex string
     * @return Contact or null if not found
     */
    suspend fun getContactByNoisePublicKey(noisePublicKeyHex: String): ContactEntity? = withContext(Dispatchers.IO) {
        contactDao.getContactByNoisePublicKey(noisePublicKeyHex)
    }

    /**
     * Get all contacts
     *
     * @return List of all contacts
     */
    suspend fun getAllContacts(): List<ContactEntity> = withContext(Dispatchers.IO) {
        contactDao.getAllContacts()
    }

    /**
     * Get trusted contacts only
     *
     * @return List of trusted contacts
     */
    suspend fun getTrustedContacts(): List<ContactEntity> = withContext(Dispatchers.IO) {
        contactDao.getTrustedContacts()
    }

    /**
     * Get favorite contacts
     *
     * @return List of favorite contacts
     */
    suspend fun getFavoriteContacts(): List<ContactEntity> = withContext(Dispatchers.IO) {
        contactDao.getFavoriteContacts()
    }

    /**
     * Delete a contact
     *
     * @param hashID Contact hash ID to delete
     */
    suspend fun deleteContact(hashID: String) = withContext(Dispatchers.IO) {
        contactDao.deleteContactByHashID(hashID)
        Log.d(TAG, "✅ Deleted contact: $hashID")
    }

    /**
     * Delete all contacts
     */
    suspend fun deleteAllContacts() = withContext(Dispatchers.IO) {
        contactDao.deleteAllContacts()
        Log.d(TAG, "✅ Deleted all contacts")
    }

    /**
     * Update contact trust status
     *
     * @param hashID Contact hash ID
     * @param isTrusted New trust status
     */
    suspend fun updateTrustStatus(hashID: String, isTrusted: Boolean) = withContext(Dispatchers.IO) {
        contactDao.setTrusted(hashID, isTrusted)
    }

    /**
     * Update contact favorite status
     *
     * @param hashID Contact hash ID
     * @param isFavorite New favorite status
     */
    suspend fun updateFavoriteStatus(hashID: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        contactDao.setFavorite(hashID, isFavorite)
    }

    /**
     * Update contact custom name
     *
     * @param hashID Contact hash ID
     * @param customName New custom name
     */
    suspend fun updateCustomName(hashID: String, customName: String?) = withContext(Dispatchers.IO) {
        contactDao.updateCustomName(hashID, customName)
    }

    /**
     * Search contacts by name
     *
     * @param query Search query
     * @return List of matching contacts
     */
    suspend fun searchContacts(query: String): List<ContactEntity> = withContext(Dispatchers.IO) {
        contactDao.searchContacts(query)
    }
}
