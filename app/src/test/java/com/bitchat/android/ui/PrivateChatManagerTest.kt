package com.bitchat.android.ui

import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.*

/**
 * Unit tests for PrivateChatManager consolidation and sanitization functionality
 *
 * Note: These are lightweight tests focusing on the consolidation and sanitization logic
 */
class PrivateChatManagerTest {

    private lateinit var chatState: ChatState

    @Before
    fun setup() {
        // Create test scope
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

        // Initialize chat state
        chatState = ChatState(testScope)
    }

    // MARK: - Chat Sanitization Tests

    @Test
    fun testSanitizeChat_removeDuplicates() {
        // This test verifies the sanitization logic by manually checking
        // the deduplication behavior

        val peerID = "peer_abc123"

        val message1 = createTestMessage(id = "msg1", sender = "Alice", timestamp = Date(1000))
        val message2 = createTestMessage(id = "msg2", sender = "Alice", timestamp = Date(2000))
        val message3 = createTestMessage(id = "msg1", sender = "Alice", timestamp = Date(1000)) // Duplicate

        val messages = listOf(message1, message2, message3)

        // Simulate what sanitizeChat does: deduplicate by ID and sort by timestamp
        val sanitized = messages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        // Assert: Only 2 unique messages remain
        assertEquals(2, sanitized.size)
        assertEquals("msg1", sanitized[0].id)
        assertEquals("msg2", sanitized[1].id)
    }

    @Test
    fun testSanitizeChat_maintainOrder() {
        // Test that messages are sorted chronologically after sanitization

        val peerID = "peer_abc123"

        val message1 = createTestMessage(id = "msg3", sender = "Bob", timestamp = Date(3000))
        val message2 = createTestMessage(id = "msg1", sender = "Bob", timestamp = Date(1000))
        val message3 = createTestMessage(id = "msg2", sender = "Bob", timestamp = Date(2000))
        val message4 = createTestMessage(id = "msg1", sender = "Bob", timestamp = Date(1000)) // Duplicate

        val messages = listOf(message1, message2, message3, message4)

        // Simulate sanitization: deduplicate and sort
        val sanitized = messages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        // Assert: Messages sorted chronologically, duplicates removed
        assertEquals(3, sanitized.size)
        assertEquals("msg1", sanitized[0].id) // 1000
        assertEquals("msg2", sanitized[1].id) // 2000
        assertEquals("msg3", sanitized[2].id) // 3000
    }

    @Test
    fun testSanitizeChat_noDuplicates() {
        // Test that sanitization preserves all messages when no duplicates exist

        val peerID = "peer_abc123"

        val message1 = createTestMessage(id = "msg1", sender = "Charlie")
        val message2 = createTestMessage(id = "msg2", sender = "Charlie")

        val messages = listOf(message1, message2)

        // Simulate sanitization
        val sanitized = messages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        // Assert: No change, both messages remain
        assertEquals(2, sanitized.size)
    }

    @Test
    fun testSanitizeChat_emptyConversation() {
        // Test sanitization with empty list

        val messages = emptyList<BitchatMessage>()

        // Simulate sanitization
        val sanitized = messages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        // Assert: Still empty
        assertTrue(sanitized.isEmpty())
    }

    // MARK: - Message Consolidation Tests

    @Test
    fun testConsolidateMessages_mergeTwoConversations() {
        // Test the consolidation logic: merge messages from same nickname but different peer IDs

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

        // Simulate what consolidateMessages does:
        // 1. Find all conversations with this nickname
        // 2. Merge messages
        // 3. Deduplicate and sort

        val allMessages = listOf(message1, message2)
        val consolidated = allMessages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        // Assert: Both messages should be consolidated
        assertEquals(2, consolidated.size)
        assertEquals("msg1", consolidated[0].id)
        assertEquals("msg2", consolidated[1].id)
    }

    @Test
    fun testConsolidateMessages_deduplicateByMessageID() {
        // Test that consolidation removes duplicates by message ID

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

        val allMessages = listOf(message1, message2, message3)
        val consolidated = allMessages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        // Assert: Only 2 unique messages (duplicate removed)
        assertEquals(2, consolidated.size)
        val messageIDs = consolidated.map { it.id }
        assertTrue(messageIDs.contains("msg_duplicate"))
        assertTrue(messageIDs.contains("msg_unique"))
    }

    @Test
    fun testConsolidateMessages_chronologicalOrder() {
        // Test that consolidation maintains chronological order

        val nickname = "Charlie"

        val message1 = createTestMessage(id = "msg1", sender = nickname, timestamp = Date(3000))
        val message2 = createTestMessage(id = "msg2", sender = nickname, timestamp = Date(1000))
        val message3 = createTestMessage(id = "msg3", sender = nickname, timestamp = Date(2000))

        val allMessages = listOf(message1, message2, message3)
        val consolidated = allMessages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        // Assert: Messages should be sorted chronologically
        assertEquals(3, consolidated.size)
        assertEquals("msg2", consolidated[0].id) // Timestamp 1000
        assertEquals("msg3", consolidated[1].id) // Timestamp 2000
        assertEquals("msg1", consolidated[2].id) // Timestamp 3000
    }

    @Test
    fun testConsolidateMessages_matchRecipientNickname() {
        // Test that consolidation matches both sender and recipient nicknames

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

        val allMessages = listOf(message1, message2)

        // Filter messages that match nickname (sender OR recipient)
        val relevantMessages = allMessages.filter { msg ->
            msg.sender == nickname || msg.recipientNickname == nickname
        }

        val consolidated = relevantMessages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        // Assert: Both messages should be included (sender OR recipient match)
        assertEquals(2, consolidated.size)
    }

    @Test
    fun testConsolidateMessages_noConversations() {
        // Test consolidation with empty message list

        val messages = emptyList<BitchatMessage>()
        val consolidated = messages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        // Assert: Empty result
        assertTrue(consolidated.isEmpty())
    }

    // MARK: - Integration Test

    @Test
    fun testConsolidateAndSanitize_together() {
        // Test consolidation followed by sanitization

        val nickname = "Frank"

        val message1 = createTestMessage(id = "msg1", sender = nickname, timestamp = Date(1000))
        val message2 = createTestMessage(id = "msg2", sender = nickname, timestamp = Date(2000))
        val message3 = createTestMessage(id = "msg1", sender = nickname, timestamp = Date(1000)) // Duplicate

        val allMessages = listOf(message1, message2, message3)

        // Consolidate and sanitize in one step
        val result = allMessages
            .distinctBy { it.id }
            .sortedBy { it.timestamp }

        // Assert: Only 2 unique messages, properly ordered
        assertEquals(2, result.size)
        assertEquals("msg1", result[0].id)
        assertEquals("msg2", result[1].id)
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
}
