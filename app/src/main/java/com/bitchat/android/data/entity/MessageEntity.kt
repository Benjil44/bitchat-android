package com.bitchat.android.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import java.util.*

/**
 * Room database entity for persisting private messages.
 *
 * Indexed by peerID for efficient conversation queries.
 */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["peerID", "timestamp"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val peerID: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val isPrivate: Boolean,
    val deliveryStatus: String,
    val recipientNickname: String? = null,
    val senderPeerID: String? = null,
    val isEncrypted: Boolean = false,
    val encryptedContent: ByteArray? = null
) {
    /**
     * Convert database entity to domain model
     */
    fun toBitchatMessage(): BitchatMessage {
        return BitchatMessage(
            id = id,
            sender = sender,
            content = content,
            timestamp = Date(timestamp),
            isRelay = false,
            isPrivate = isPrivate,
            recipientNickname = recipientNickname,
            senderPeerID = senderPeerID,
            deliveryStatus = parseDeliveryStatus(deliveryStatus),
            isEncrypted = isEncrypted,
            encryptedContent = encryptedContent
        )
    }

    companion object {
        /**
         * Convert domain model to database entity
         */
        fun from(message: BitchatMessage, peerID: String): MessageEntity {
            return MessageEntity(
                id = message.id,
                peerID = peerID,
                sender = message.sender,
                content = message.content,
                timestamp = message.timestamp.time,
                isPrivate = message.isPrivate,
                deliveryStatus = serializeDeliveryStatus(message.deliveryStatus),
                recipientNickname = message.recipientNickname,
                senderPeerID = message.senderPeerID,
                isEncrypted = message.isEncrypted,
                encryptedContent = message.encryptedContent
            )
        }

        private fun serializeDeliveryStatus(status: DeliveryStatus?): String {
            return when (status) {
                is DeliveryStatus.Sending -> "sending"
                is DeliveryStatus.Sent -> "sent"
                is DeliveryStatus.Delivered -> "delivered:${status.to}:${status.at.time}"
                is DeliveryStatus.Read -> "read:${status.by}:${status.at.time}"
                is DeliveryStatus.Failed -> "failed:${status.reason}"
                is DeliveryStatus.PartiallyDelivered -> "partial:${status.reached}:${status.total}"
                null -> "unknown"
            }
        }

        private fun parseDeliveryStatus(status: String): DeliveryStatus? {
            val parts = status.split(":")
            return when (parts[0]) {
                "sending" -> DeliveryStatus.Sending
                "sent" -> DeliveryStatus.Sent
                "delivered" -> DeliveryStatus.Delivered(
                    to = parts.getOrNull(1) ?: "",
                    at = Date(parts.getOrNull(2)?.toLongOrNull() ?: 0)
                )
                "read" -> DeliveryStatus.Read(
                    by = parts.getOrNull(1) ?: "",
                    at = Date(parts.getOrNull(2)?.toLongOrNull() ?: 0)
                )
                "failed" -> DeliveryStatus.Failed(
                    reason = parts.getOrNull(1) ?: "unknown"
                )
                "partial" -> DeliveryStatus.PartiallyDelivered(
                    reached = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                    total = parts.getOrNull(2)?.toIntOrNull() ?: 0
                )
                else -> null
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageEntity

        if (id != other.id) return false
        if (peerID != other.peerID) return false
        if (sender != other.sender) return false
        if (content != other.content) return false
        if (timestamp != other.timestamp) return false
        if (isPrivate != other.isPrivate) return false
        if (deliveryStatus != other.deliveryStatus) return false
        if (recipientNickname != other.recipientNickname) return false
        if (senderPeerID != other.senderPeerID) return false
        if (isEncrypted != other.isEncrypted) return false
        if (encryptedContent != null) {
            if (other.encryptedContent == null) return false
            if (!encryptedContent.contentEquals(other.encryptedContent)) return false
        } else if (other.encryptedContent != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + peerID.hashCode()
        result = 31 * result + sender.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isPrivate.hashCode()
        result = 31 * result + deliveryStatus.hashCode()
        result = 31 * result + (recipientNickname?.hashCode() ?: 0)
        result = 31 * result + (senderPeerID?.hashCode() ?: 0)
        result = 31 * result + isEncrypted.hashCode()
        result = 31 * result + (encryptedContent?.contentHashCode() ?: 0)
        return result
    }
}
