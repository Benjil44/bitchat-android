package com.bitchat.android.ui

import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import com.bitchat.android.mesh.PeerFingerprintManager
import java.security.MessageDigest

import com.bitchat.android.mesh.BluetoothMeshService
import java.util.*
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Interface for Noise session operations needed by PrivateChatManager
 * This avoids reflection and makes dependencies explicit
 */
interface NoiseSessionDelegate {
    fun hasEstablishedSession(peerID: String): Boolean
    fun initiateHandshake(peerID: String)
    fun getMyPeerID(): String
}

/**
 * Send request for queuing
 */
private data class SendRequest(
    val content: String,
    val peerID: String,
    val recipientNickname: String?,
    val senderNickname: String?,
    val myPeerID: String,
    val onSendMessage: (String, String, String, String) -> Unit
)

/**
 * Handles private chat functionality including peer management and blocking
 * Now uses centralized PeerFingerprintManager for all fingerprint operations
 */
class PrivateChatManager(
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val dataManager: DataManager,
    private val noiseSessionDelegate: NoiseSessionDelegate
) {

    companion object {
        private const val TAG = "PrivateChatManager"
    }

    // Use centralized fingerprint management - NO LOCAL STORAGE
    private val fingerprintManager = PeerFingerprintManager.getInstance()

    // Track received private messages that need read receipts
    private val unreadReceivedMessages = mutableMapOf<String, MutableList<BitchatMessage>>()

    // Send queue to prevent race conditions on rapid send
    private val sendQueue = Channel<SendRequest>(Channel.UNLIMITED)
    private val sendJob = Job()
    private val sendScope = CoroutineScope(Dispatchers.Default + sendJob)

    init {
        // Process send requests sequentially to prevent duplicates
        sendScope.launch {
            for (request in sendQueue) {
                processSendRequest(request)
            }
        }
    }

    // MARK: - Private Chat Lifecycle

    fun startPrivateChat(peerID: String, meshService: BluetoothMeshService): Boolean {
        if (isPeerBlocked(peerID)) {
            val peerNickname = getPeerNickname(peerID, meshService)
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "cannot start chat with $peerNickname: user is blocked.",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return false
        }

        // Establish Noise session if needed before starting the chat
        establishNoiseSessionIfNeeded(peerID, meshService)

        // Get peer nickname for consolidation
        val peerNickname = getPeerNickname(peerID, meshService)

        // Consolidate messages from all conversations with same nickname
        if (peerNickname != peerID) { // Only if we have a real nickname (not just peer ID)
            try {
                consolidateMessages(peerID, peerNickname)
                Log.d(TAG, "Consolidated messages for $peerNickname into conversation with $peerID")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to consolidate messages: ${e.message}")
            }
        }

        // Consolidate any temporary Nostr conversation for this peer into the stable/current peerID
        try {
            consolidateNostrTempConversationIfNeeded(peerID)
        } catch (_: Exception) { }

        // Sanitize the chat to ensure no duplicates
        sanitizeChat(peerID)

        state.setSelectedPrivateChatPeer(peerID)

        // Clear unread
        messageManager.clearPrivateUnreadMessages(peerID)

        // Initialize chat if needed
        messageManager.initializePrivateChat(peerID)

        // Load persisted messages if enabled
        CoroutineScope(Dispatchers.IO).launch {
            messageManager.loadPersistedMessages(peerID)
        }

        // Send read receipts for all unread messages from this peer
        sendReadReceiptsForPeer(peerID, meshService)

        return true
    }

    fun endPrivateChat() {
        state.setSelectedPrivateChatPeer(null)
    }

    fun sendPrivateMessage(
        content: String,
        peerID: String,
        recipientNickname: String?,
        senderNickname: String?,
        myPeerID: String,
        onSendMessage: (String, String, String, String) -> Unit
    ): Boolean {
        if (isPeerBlocked(peerID)) {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "cannot send message to $recipientNickname: user is blocked.",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return false
        }

        // Queue send request to prevent race conditions
        sendScope.launch {
            sendQueue.send(SendRequest(content, peerID, recipientNickname, senderNickname, myPeerID, onSendMessage))
        }

        return true
    }

    /**
     * Process send request sequentially from queue
     * Prevents duplicate messages on rapid sends
     */
    private fun processSendRequest(request: SendRequest) {
        val message = BitchatMessage(
            sender = request.senderNickname ?: request.myPeerID,
            content = request.content,
            timestamp = Date(),
            isRelay = false,
            isPrivate = true,
            recipientNickname = request.recipientNickname,
            senderPeerID = request.myPeerID,
            deliveryStatus = DeliveryStatus.Sending
        )

        messageManager.addPrivateMessage(request.peerID, message)
        request.onSendMessage(request.content, request.peerID, request.recipientNickname ?: "", message.id)

        Log.d(TAG, "Processed send request: ${message.id} to ${request.peerID}")
    }

    // MARK: - Peer Management

    fun isPeerBlocked(peerID: String): Boolean {
        val fingerprint = fingerprintManager.getFingerprintForPeer(peerID)
        return fingerprint != null && dataManager.isUserBlocked(fingerprint)
    }

    fun toggleFavorite(peerID: String) {
        var fingerprint = fingerprintManager.getFingerprintForPeer(peerID)

        // Fallback: if this looks like a 64-hex Noise public key (offline favorite entry),
        // compute a synthetic fingerprint (SHA-256 of public key) to allow unfollowing offline peers
        if (fingerprint == null && peerID.length == 64 && peerID.matches(Regex("^[0-9a-fA-F]+$"))) {
            try {
                val pubBytes = peerID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val fpBytes = digest.digest(pubBytes)
                fingerprint = fpBytes.joinToString("") { "%02x".format(it) }
                Log.d(TAG, "Computed fingerprint from noise key hex for offline toggle: $fingerprint")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to compute fingerprint from noise key hex: ${e.message}")
            }
        }

        if (fingerprint == null) {
            Log.w(TAG, "toggleFavorite: no fingerprint for peerID=$peerID; ignoring toggle")
            return
        }

        Log.d(TAG, "toggleFavorite called for peerID: $peerID, fingerprint: $fingerprint")

        val wasFavorite = dataManager.isFavorite(fingerprint!!)
        Log.d(TAG, "Current favorite status: $wasFavorite")

        val currentFavorites = state.getFavoritePeersValue()
        Log.d(TAG, "Current UI state favorites: $currentFavorites")

        if (wasFavorite) {
            dataManager.removeFavorite(fingerprint!!)
            Log.d(TAG, "Removed from favorites: $fingerprint")
        } else {
            dataManager.addFavorite(fingerprint!!)
            Log.d(TAG, "Added to favorites: $fingerprint")
        }

        // Always update state to trigger UI refresh - create new set to ensure change detection
        val newFavorites = dataManager.favoritePeers.toSet()
        state.setFavoritePeers(newFavorites)

        Log.d(TAG, "Force updated favorite peers state. New favorites: $newFavorites")
        Log.d(TAG, "All peer fingerprints: ${fingerprintManager.getAllPeerFingerprints()}")
    }


    fun isFavorite(peerID: String): Boolean {
        val fingerprint = fingerprintManager.getFingerprintForPeer(peerID) ?: return false
        val isFav = dataManager.isFavorite(fingerprint)
        Log.d(TAG, "isFavorite check: peerID=$peerID, fingerprint=$fingerprint, result=$isFav")
        return isFav
    }

    fun getPeerFingerprint(peerID: String): String? {
        return fingerprintManager.getFingerprintForPeer(peerID)
    }

    fun getPeerFingerprints(): Map<String, String> {
        return fingerprintManager.getAllPeerFingerprints()
    }

    // MARK: - Block/Unblock Operations

    fun blockPeer(peerID: String, meshService: BluetoothMeshService): Boolean {
        val fingerprint = fingerprintManager.getFingerprintForPeer(peerID)
        if (fingerprint != null) {
            dataManager.addBlockedUser(fingerprint)

            val peerNickname = getPeerNickname(peerID, meshService)
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "blocked user $peerNickname",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)

            // End private chat if currently in one with this peer
            if (state.getSelectedPrivateChatPeerValue() == peerID) {
                endPrivateChat()
            }

            return true
        }
        return false
    }

    fun unblockPeer(peerID: String, meshService: BluetoothMeshService): Boolean {
        val fingerprint = fingerprintManager.getFingerprintForPeer(peerID)
        if (fingerprint != null && dataManager.isUserBlocked(fingerprint)) {
            dataManager.removeBlockedUser(fingerprint)

            val peerNickname = getPeerNickname(peerID, meshService)
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "unblocked user $peerNickname",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return true
        }
        return false
    }

    fun blockPeerByNickname(targetName: String, meshService: BluetoothMeshService): Boolean {
        val peerID = getPeerIDForNickname(targetName, meshService)

        if (peerID != null) {
            return blockPeer(peerID, meshService)
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "user '$targetName' not found",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return false
        }
    }

    fun unblockPeerByNickname(targetName: String, meshService: BluetoothMeshService): Boolean {
        val peerID = getPeerIDForNickname(targetName, meshService)

        if (peerID != null) {
            val fingerprint = fingerprintManager.getFingerprintForPeer(peerID)
            if (fingerprint != null && dataManager.isUserBlocked(fingerprint)) {
                return unblockPeer(peerID, meshService)
            } else {
                val systemMessage = BitchatMessage(
                    sender = "system",
                    content = "user '$targetName' is not blocked",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
                return false
            }
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "user '$targetName' not found",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return false
        }
    }

    fun listBlockedUsers(): String {
        val blockedCount = dataManager.blockedUsers.size
        return if (blockedCount == 0) {
            "no blocked users"
        } else {
            "blocked users: $blockedCount fingerprints"
        }
    }

    // MARK: - Message Handling

    fun handleIncomingPrivateMessage(message: BitchatMessage) {
        handleIncomingPrivateMessage(message, suppressUnread = false)
    }

    fun handleIncomingPrivateMessage(message: BitchatMessage, suppressUnread: Boolean) {
        val senderPeerID = message.senderPeerID
        if (senderPeerID != null) {
            // Mesh-origin private message: AppStateStore updates the list; avoid double-add here.
            if (!isPeerBlocked(senderPeerID)) {
                // Ensure chat exists
                messageManager.initializePrivateChat(senderPeerID)

                // Sanitize chat to remove any duplicates that may have been introduced
                sanitizeChat(senderPeerID)

                // Track as unread for read receipt purposes if not focused
                if (!suppressUnread && state.getSelectedPrivateChatPeerValue() != senderPeerID) {
                    val unreadList = unreadReceivedMessages.getOrPut(senderPeerID) { mutableListOf() }
                    unreadList.add(message)
                    Log.d(TAG, "Queued unread from $senderPeerID (count=${unreadList.size})")
                    val currentUnread = state.getUnreadPrivateMessagesValue().toMutableSet()
                    currentUnread.add(senderPeerID)
                    state.setUnreadPrivateMessages(currentUnread)
                }
            }
            return
        }
        // Non-mesh path (e.g., Nostr): add to UI state using existing logic
        val inferredPeer = state.getSelectedPrivateChatPeerValue() ?: return
        if (suppressUnread) {
            messageManager.addPrivateMessageNoUnread(inferredPeer, message)
        } else {
            messageManager.addPrivateMessage(inferredPeer, message)
        }

        // Sanitize after adding message
        sanitizeChat(inferredPeer)
    }

    /**
     * Send read receipts for all unread messages from a specific peer
     * Called when the user focuses on a private chat
     */
    fun sendReadReceiptsForPeer(peerID: String, meshService: BluetoothMeshService) {
        // Collect candidate messages: all incoming messages from this peer in the conversation
        val chats = try { state.getPrivateChatsValue() } catch (_: Exception) { emptyMap<String, List<BitchatMessage>>() }
        val messages = chats[peerID].orEmpty()

        if (messages.isEmpty()) {
            Log.d(TAG, "No messages found for peer $peerID to send read receipts")
        }

        val myNickname = state.getNicknameValue() ?: "unknown"
        var sentCount = 0
        messages.forEach { msg ->
            // Only for incoming messages from this peer
            if (msg.senderPeerID == peerID) {
                try {
                    meshService.sendReadReceipt(msg.id, peerID, myNickname)
                    sentCount += 1
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send read receipt for message ${msg.id}: ${e.message}")
                }
            }
        }

        // Clear any locally tracked unread queue for this peer
        unreadReceivedMessages.remove(peerID)
        // Also clear UI unread marker for this peer now that chat is focused/read
        try { messageManager.clearPrivateUnreadMessages(peerID) } catch (_: Exception) { }
        Log.d(TAG, "Sent $sentCount read receipts for peer $peerID (from conversation messages)")
    }

    fun cleanupDisconnectedPeer(peerID: String) {
        // End private chat if peer disconnected
        if (state.getSelectedPrivateChatPeerValue() == peerID) {
            endPrivateChat()
        }

        // Clean up unread messages for disconnected peer
        unreadReceivedMessages.remove(peerID)
        Log.d(TAG, "Cleaned up unread messages for disconnected peer $peerID")
    }

    // MARK: - Noise Session Management

    /**
     * Establish Noise session if needed before starting private chat
     * Uses same lexicographical logic as MessageHandler.handleNoiseIdentityAnnouncement
     */
    private fun establishNoiseSessionIfNeeded(peerID: String, meshService: BluetoothMeshService) {
        if (noiseSessionDelegate.hasEstablishedSession(peerID)) {
            Log.d(TAG, "Noise session already established with $peerID")
            return
        }

        Log.d(TAG, "No Noise session with $peerID, determining who should initiate handshake")

        val myPeerID = noiseSessionDelegate.getMyPeerID()

        // Use lexicographical comparison to decide who initiates (same logic as MessageHandler)
        if (myPeerID < peerID) {
            // We should initiate the handshake
            Log.d(
                TAG,
                "Our peer ID lexicographically < target peer ID, initiating Noise handshake with $peerID"
            )
            noiseSessionDelegate.initiateHandshake(peerID)
        } else {
            // They should initiate, we send identity announcement through standard announce
            Log.d(
                TAG,
                "Our peer ID lexicographically >= target peer ID, sending identity announcement to prompt handshake from $peerID"
            )
            meshService.sendAnnouncementToPeer(peerID)
            Log.d(TAG, "Sent identity announcement to $peerID – starting handshake now from our side")
            noiseSessionDelegate.initiateHandshake(peerID)
        }

    }

    // MARK: - Utility Functions

    private fun getPeerIDForNickname(nickname: String, meshService: BluetoothMeshService): String? {
        return meshService.getPeerNicknames().entries.find { it.value == nickname }?.key
    }

    private fun getPeerNickname(peerID: String, meshService: BluetoothMeshService): String {
        return meshService.getPeerNicknames()[peerID] ?: peerID
    }

    // MARK: - Consolidation

    /**
     * Consolidates messages from multiple peer IDs with same nickname into a single conversation.
     *
     * Use case: Peer reconnects with different ephemeral peer ID but same nickname.
     * Without consolidation, user sees two separate conversations.
     *
     * @param currentPeerID The primary/current peer ID to consolidate into
     * @param peerNickname The nickname to match across different peer IDs
     * @param persistedReadReceipts Set of message IDs that have been read (for read status preservation)
     * @return Consolidated list of messages, deduplicated and sorted chronologically
     */
    fun consolidateMessages(
        currentPeerID: String,
        peerNickname: String,
        persistedReadReceipts: Set<String> = emptySet()
    ): List<BitchatMessage> {
        Log.d(TAG, "consolidateMessages: peerID=$currentPeerID, nickname=$peerNickname")

        // Find all conversations where messages involve this nickname (as sender or recipient)
        val allChats = state.getPrivateChatsValue()
        val relevantConversations = allChats.filter { (peerID, messages) ->
            messages.any { msg ->
                msg.sender == peerNickname || msg.recipientNickname == peerNickname
            }
        }

        Log.d(TAG, "Found ${relevantConversations.size} conversations with nickname $peerNickname")

        if (relevantConversations.isEmpty()) {
            return emptyList()
        }

        // Merge all messages from these conversations
        val allMessages = relevantConversations.values.flatten()

        // Deduplicate by message ID
        val uniqueMessages = allMessages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        Log.d(TAG, "Consolidated ${allMessages.size} messages into ${uniqueMessages.size} unique messages")

        // Update state to use current peer ID only
        val updatedChats = allChats.toMutableMap()

        // Remove old peer ID entries (but keep current one)
        relevantConversations.keys.forEach { oldPeerID ->
            if (oldPeerID != currentPeerID) {
                updatedChats.remove(oldPeerID)
                Log.d(TAG, "Removed old conversation for peer $oldPeerID")
            }
        }

        // Set consolidated messages under current peer ID
        updatedChats[currentPeerID] = uniqueMessages
        state.setPrivateChats(updatedChats)

        // Update unread messages tracking
        val unread = state.getUnreadPrivateMessagesValue().toMutableSet()
        val hadUnread = relevantConversations.keys.any { oldPeerID ->
            oldPeerID != currentPeerID && unread.remove(oldPeerID)
        }
        if (hadUnread) {
            unread.add(currentPeerID)
            state.setUnreadPrivateMessages(unread)
        }

        // Update read receipts tracking
        unreadReceivedMessages.remove(currentPeerID)

        Log.d(TAG, "Consolidation complete: ${uniqueMessages.size} messages under $currentPeerID")

        return uniqueMessages
    }

    /**
     * Removes duplicate messages within a conversation by message ID.
     * Should be called after receiving new messages or after consolidation.
     *
     * @param peerID The peer whose conversation should be sanitized
     */
    fun sanitizeChat(peerID: String) {
        val chats = state.getPrivateChatsValue()
        val messages = chats[peerID] ?: return

        val beforeCount = messages.size

        // Deduplicate by message ID, preserve chronological order
        val sanitized = messages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        val afterCount = sanitized.size

        if (beforeCount != afterCount) {
            Log.d(TAG, "sanitizeChat: Removed ${beforeCount - afterCount} duplicates from $peerID conversation")

            // Update state with sanitized messages
            val updatedChats = chats.toMutableMap()
            updatedChats[peerID] = sanitized
            state.setPrivateChats(updatedChats)
        } else {
            Log.d(TAG, "sanitizeChat: No duplicates found in $peerID conversation")
        }
    }

    private fun consolidateNostrTempConversationIfNeeded(targetPeerID: String) {
        // If target is a mesh/noise-based peerID, merge any messages from its temp Nostr key
        if (targetPeerID.startsWith("nostr_")) return

        // Find favorites mapping and corresponding temp key
        val tryMergeKeys = mutableListOf<String>()

        // If we know the sender's Nostr pubkey for this peer via favorites, derive temp key
        try {
            val noiseKeyBytes = targetPeerID.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val npub = com.bitchat.android.favorites.FavoritesPersistenceService.shared.findNostrPubkey(noiseKeyBytes)
            if (npub != null) {
                // Normalize to hex to match how we formed temp keys (nostr_<pub16>)
                val (hrp, data) = com.bitchat.android.nostr.Bech32.decode(npub)
                if (hrp == "npub") {
                    val pubHex = data.joinToString("") { "%02x".format(it) }
                    tryMergeKeys.add("nostr_${pubHex.take(16)}")
                }
            }
        } catch (_: Exception) { }

        // Also merge any directly-addressed temp key used by incoming messages (without mapping yet)
        // Search existing chats for keys that begin with "nostr_" and have messages from the same nickname
        state.getPrivateChatsValue().keys.filter { it.startsWith("nostr_") }.forEach { tempKey ->
            if (!tryMergeKeys.contains(tempKey)) tryMergeKeys.add(tempKey)
        }

        if (tryMergeKeys.isEmpty()) return

        val currentChats = state.getPrivateChatsValue().toMutableMap()
        val targetList = currentChats[targetPeerID]?.toMutableList() ?: mutableListOf()

        var didMerge = false
        tryMergeKeys.forEach { tempKey ->
            val tempList = currentChats[tempKey]
            if (!tempList.isNullOrEmpty()) {
                targetList.addAll(tempList)
                currentChats.remove(tempKey)
                didMerge = true
            }
        }

        if (didMerge) {
            currentChats[targetPeerID] = targetList
            state.setPrivateChats(currentChats)

            // Also remove unread flag from temp keys and apply to target
            val unread = state.getUnreadPrivateMessagesValue().toMutableSet()
            val hadUnread = tryMergeKeys.any { unread.remove(it) }
            if (hadUnread) {
                unread.add(targetPeerID)
                state.setUnreadPrivateMessages(unread)
            }
        }
    }

    // MARK: - Emergency Clear

    fun clearAllPrivateChats() {
        state.setSelectedPrivateChatPeer(null)
        state.setUnreadPrivateMessages(emptySet())

        // Clear unread messages tracking
        unreadReceivedMessages.clear()

        // Clear fingerprints via centralized manager (only if needed for emergency clear)
        // Note: This will be handled by the parent PeerManager.clearAllPeers()
    }

    /**
     * Shutdown manager and clean up resources
     * Call this when the manager is no longer needed
     */
    fun shutdown() {
        sendJob.cancel()
        Log.d(TAG, "PrivateChatManager shut down")
    }

    // MARK: - Public Getters

    fun getAllPeerFingerprints(): Map<String, String> {
        return fingerprintManager.getAllPeerFingerprints()
    }
}
