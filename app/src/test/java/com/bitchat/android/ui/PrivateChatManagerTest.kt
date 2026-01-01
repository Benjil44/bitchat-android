package com.bitchat.android.ui

import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.*

/**
 * Unit tests for PrivateChatManager consolidation and sanitization functionality
 */
class PrivateChatManagerTest {

    private lateinit var chatState: ChatState
    private lateinit var messageManager: MessageManager
    private lateinit var dataManager: DataManager
    private lateinit var noiseSessionDelegate: TestNoiseSessionDelegate
    private lateinit var privateChatManager: PrivateChatManager

    @Before
    fun setup() {
        // Create test scope
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

        // Initialize chat state
        chatState = ChatState(testScope)

        // Create mock dependencies
        dataManager = TestDataManager()
        messageManager = MessageManager(chatState, dataManager)
        noiseSessionDelegate = TestNoiseSessionDelegate()

        // Create PrivateChatManager
        privateChatManager = PrivateChatManager(
            state = chatState,
            messageManager = messageManager,
            dataManager = dataManager,
            noiseSessionDelegate = noiseSessionDelegate
        )
    }

    // MARK: - Message Consolidation Tests

    @Test
    fun testConsolidateMessages_mergeTwoConversations() {
        // Setup: Create two conversations with same nickname but different peer IDs
        val peerID1 = "peer_abc123"
        val peerID2 = "peer_def456"
        val nickname = "Alice"

        val message1 = createTestMessage(
            id = "msg1",
            sender = nickname,
            content = "Hello from session 1",
            timestamp = Date(1000)
        )
        val message2 = createTestMessage(
            id = "msg2",
            sender = nickname,
            content = "Hello from session 2",
            timestamp = Date(2000)
        )

        // Add messages to different peer IDs
        chatState.setPrivateChats(mapOf(
            peerID1 to listOf(message1),
            peerID2 to listOf(message2)
        ))

        // Act: Consolidate into peerID2
        val result = privateChatManager.consolidateMessages(peerID2, nickname)

        // Assert: Both messages should be consolidated
        assertEquals(2, result.size)
        assertEquals("msg1", result[0].id)
        assertEquals("msg2", result[1].id)

        // Assert: Only peerID2 should remain in state
        val chats = chatState.getPrivateChatsValue()
        assertEquals(1, chats.size)
        assertTrue(chats.containsKey(peerID2))
        assertFalse(chats.containsKey(peerID1))
    }

    @Test
    fun testConsolidateMessages_deduplicateByMessageID() {
        // Setup: Create duplicate messages across conversations
        val peerID1 = "peer_abc123"
        val peerID2 = "peer_def456"
        val nickname = "Bob"

        val message1 = createTestMessage(
            id = "msg_duplicate",
            sender = nickname,
            content = "Same message",
            timestamp = Date(1000)
        )
        val message2 = createTestMessage(
            id = "msg_duplicate", // Same ID - duplicate
            sender = nickname,
            content = "Same message",
            timestamp = Date(1000)
        )
        val message3 = createTestMessage(
            id = "msg_unique",
            sender = nickname,
            content = "Unique message",
            timestamp = Date(2000)
        )

        chatState.setPrivateChats(mapOf(
            peerID1 to listOf(message1, message3),
            peerID2 to listOf(message2)
        ))

        // Act: Consolidate
        val result = privateChatManager.consolidateMessages(peerID2, nickname)

        // Assert: Only 2 unique messages (duplicate removed)
        assertEquals(2, result.size)
        val messageIDs = result.map { it.id }
        assertTrue(messageIDs.contains("msg_duplicate"))
        assertTrue(messageIDs.contains("msg_unique"))
    }

    @Test
    fun testConsolidateMessages_chronologicalOrder() {
        // Setup: Messages out of order across conversations
        val peerID1 = "peer_abc123"
        val peerID2 = "peer_def456"
        val nickname = "Charlie"

        val message1 = createTestMessage(id = "msg1", sender = nickname, timestamp = Date(3000))
        val message2 = createTestMessage(id = "msg2", sender = nickname, timestamp = Date(1000))
        val message3 = createTestMessage(id = "msg3", sender = nickname, timestamp = Date(2000))

        chatState.setPrivateChats(mapOf(
            peerID1 to listOf(message1),
            peerID2 to listOf(message2, message3)
        ))

        // Act: Consolidate
        val result = privateChatManager.consolidateMessages(peerID2, nickname)

        // Assert: Messages should be sorted chronologically
        assertEquals(3, result.size)
        assertEquals("msg2", result[0].id) // Timestamp 1000
        assertEquals("msg3", result[1].id) // Timestamp 2000
        assertEquals("msg1", result[2].id) // Timestamp 3000
    }

    @Test
    fun testConsolidateMessages_noConversations() {
        // Setup: No conversations exist for nickname
        chatState.setPrivateChats(emptyMap())

        // Act: Consolidate
        val result = privateChatManager.consolidateMessages("peer_new", "UnknownUser")

        // Assert: Empty result
        assertTrue(result.isEmpty())
    }

    @Test
    fun testConsolidateMessages_matchRecipientNickname() {
        // Setup: Messages where nickname is recipient (outgoing messages)
        val peerID1 = "peer_abc123"
        val peerID2 = "peer_def456"
        val nickname = "Dave"

        val message1 = createTestMessage(
            id = "msg1",
            sender = "Me",
            recipientNickname = nickname,
            timestamp = Date(1000)
        )
        val message2 = createTestMessage(
            id = "msg2",
            sender = nickname,
            timestamp = Date(2000)
        )

        chatState.setPrivateChats(mapOf(
            peerID1 to listOf(message1),
            peerID2 to listOf(message2)
        ))

        // Act: Consolidate
        val result = privateChatManager.consolidateMessages(peerID2, nickname)

        // Assert: Both messages should be included (sender OR recipient match)
        assertEquals(2, result.size)
    }

    @Test
    fun testConsolidateMessages_unreadTracking() {
        // Setup: Conversations with unread messages
        val peerID1 = "peer_abc123"
        val peerID2 = "peer_def456"
        val nickname = "Eve"

        val message1 = createTestMessage(id = "msg1", sender = nickname)
        val message2 = createTestMessage(id = "msg2", sender = nickname)

        chatState.setPrivateChats(mapOf(
            peerID1 to listOf(message1),
            peerID2 to listOf(message2)
        ))

        // Mark peerID1 as having unread messages
        chatState.setUnreadPrivateMessages(setOf(peerID1))

        // Act: Consolidate into peerID2
        privateChatManager.consolidateMessages(peerID2, nickname)

        // Assert: Unread should be transferred to peerID2
        val unread = chatState.getUnreadPrivateMessagesValue()
        assertTrue(unread.contains(peerID2))
        assertFalse(unread.contains(peerID1))
    }

    // MARK: - Chat Sanitization Tests

    @Test
    fun testSanitizeChat_removeDuplicates() {
        // Setup: Conversation with duplicate messages
        val peerID = "peer_abc123"

        val message1 = createTestMessage(id = "msg1", sender = "Alice", timestamp = Date(1000))
        val message2 = createTestMessage(id = "msg2", sender = "Alice", timestamp = Date(2000))
        val message3 = createTestMessage(id = "msg1", sender = "Alice", timestamp = Date(1000)) // Duplicate

        chatState.setPrivateChats(mapOf(
            peerID to listOf(message1, message2, message3)
        ))

        // Act: Sanitize
        privateChatManager.sanitizeChat(peerID)

        // Assert: Only 2 unique messages remain
        val messages = chatState.getPrivateChatsValue()[peerID] ?: emptyList()
        assertEquals(2, messages.size)
        assertEquals("msg1", messages[0].id)
        assertEquals("msg2", messages[1].id)
    }

    @Test
    fun testSanitizeChat_maintainOrder() {
        // Setup: Messages out of order with duplicates
        val peerID = "peer_abc123"

        val message1 = createTestMessage(id = "msg3", sender = "Bob", timestamp = Date(3000))
        val message2 = createTestMessage(id = "msg1", sender = "Bob", timestamp = Date(1000))
        val message3 = createTestMessage(id = "msg2", sender = "Bob", timestamp = Date(2000))
        val message4 = createTestMessage(id = "msg1", sender = "Bob", timestamp = Date(1000)) // Duplicate

        chatState.setPrivateChats(mapOf(
            peerID to listOf(message1, message2, message3, message4)
        ))

        // Act: Sanitize
        privateChatManager.sanitizeChat(peerID)

        // Assert: Messages sorted chronologically, duplicates removed
        val messages = chatState.getPrivateChatsValue()[peerID] ?: emptyList()
        assertEquals(3, messages.size)
        assertEquals("msg1", messages[0].id) // 1000
        assertEquals("msg2", messages[1].id) // 2000
        assertEquals("msg3", messages[2].id) // 3000
    }

    @Test
    fun testSanitizeChat_noDuplicates() {
        // Setup: Conversation with no duplicates
        val peerID = "peer_abc123"

        val message1 = createTestMessage(id = "msg1", sender = "Charlie")
        val message2 = createTestMessage(id = "msg2", sender = "Charlie")

        chatState.setPrivateChats(mapOf(
            peerID to listOf(message1, message2)
        ))

        // Act: Sanitize
        privateChatManager.sanitizeChat(peerID)

        // Assert: No change, both messages remain
        val messages = chatState.getPrivateChatsValue()[peerID] ?: emptyList()
        assertEquals(2, messages.size)
    }

    @Test
    fun testSanitizeChat_nonexistentPeer() {
        // Setup: Chat state with no conversation for peer
        chatState.setPrivateChats(emptyMap())

        // Act: Sanitize (should not throw)
        privateChatManager.sanitizeChat("peer_nonexistent")

        // Assert: State unchanged
        assertTrue(chatState.getPrivateChatsValue().isEmpty())
    }

    @Test
    fun testSanitizeChat_emptyConversation() {
        // Setup: Peer with empty conversation
        val peerID = "peer_abc123"

        chatState.setPrivateChats(mapOf(
            peerID to emptyList()
        ))

        // Act: Sanitize
        privateChatManager.sanitizeChat(peerID)

        // Assert: Still empty
        val messages = chatState.getPrivateChatsValue()[peerID] ?: emptyList()
        assertTrue(messages.isEmpty())
    }

    // MARK: - Integration Tests

    @Test
    fun testConsolidateAndSanitize_together() {
        // Setup: Multiple conversations with duplicates
        val peerID1 = "peer_abc123"
        val peerID2 = "peer_def456"
        val nickname = "Frank"

        val message1 = createTestMessage(id = "msg1", sender = nickname, timestamp = Date(1000))
        val message2 = createTestMessage(id = "msg2", sender = nickname, timestamp = Date(2000))
        val message3 = createTestMessage(id = "msg1", sender = nickname, timestamp = Date(1000)) // Duplicate

        chatState.setPrivateChats(mapOf(
            peerID1 to listOf(message1, message2),
            peerID2 to listOf(message3)
        ))

        // Act: Consolidate then sanitize
        privateChatManager.consolidateMessages(peerID2, nickname)
        privateChatManager.sanitizeChat(peerID2)

        // Assert: Only 2 unique messages, properly ordered
        val messages = chatState.getPrivateChatsValue()[peerID2] ?: emptyList()
        assertEquals(2, messages.size)
        assertEquals("msg1", messages[0].id)
        assertEquals("msg2", messages[1].id)

        // Assert: Only peerID2 exists
        assertEquals(1, chatState.getPrivateChatsValue().size)
    }

    // MARK: - Helper Methods

    private fun createTestMessage(
        id: String = UUID.randomUUID().toString(),
        sender: String = "TestSender",
        content: String = "Test message",
        timestamp: Date = Date(),
        recipientNickname: String? = null,
        senderPeerID: String? = null
    ): BitchatMessage {
        return BitchatMessage(
            id = id,
            sender = sender,
            content = content,
            timestamp = timestamp,
            isRelay = false,
            isPrivate = true,
            recipientNickname = recipientNickname,
            senderPeerID = senderPeerID,
            deliveryStatus = DeliveryStatus.Sent
        )
    }

    // MARK: - Test Doubles

    private class TestNoiseSessionDelegate : NoiseSessionDelegate {
        private val sessions = mutableSetOf<String>()

        override fun hasEstablishedSession(peerID: String): Boolean {
            return sessions.contains(peerID)
        }

        override fun initiateHandshake(peerID: String) {
            sessions.add(peerID)
        }

        override fun getMyPeerID(): String {
            return "my_peer_id_test"
        }
    }

    private class TestDataManager : DataManager(
        context = null as? android.content.Context ?: throw IllegalStateException("Test context")
    ) {
        private val blockedUsersSet = mutableSetOf<String>()
        private val favoritesSet = mutableSetOf<String>()

        override fun isUserBlocked(fingerprint: String): Boolean {
            return blockedUsersSet.contains(fingerprint)
        }

        override fun addBlockedUser(fingerprint: String) {
            blockedUsersSet.add(fingerprint)
        }

        override fun removeBlockedUser(fingerprint: String) {
            blockedUsersSet.remove(fingerprint)
        }

        override fun isFavorite(fingerprint: String): Boolean {
            return favoritesSet.contains(fingerprint)
        }

        override fun addFavorite(fingerprint: String) {
            favoritesSet.add(fingerprint)
        }

        override fun removeFavorite(fingerprint: String) {
            favoritesSet.remove(fingerprint)
        }
    }
}
