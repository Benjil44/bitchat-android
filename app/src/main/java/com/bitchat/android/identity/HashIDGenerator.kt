package com.bitchat.android.identity

import android.util.Log
import java.security.MessageDigest

/**
 * HashIDGenerator - Generate user-friendly 8-character Hash IDs from public keys
 *
 * Purpose:
 * - Create short, memorable IDs for users to share with friends
 * - Based on cryptographic public keys but human-readable
 * - No confusing characters (0/O, 1/I/l removed)
 *
 * Use Case for Iran:
 * - Easy to share via voice, SMS, or written notes
 * - QR codes for in-person friend adding
 * - No reliance on phone numbers or real names
 *
 * Format: 8 characters using Base32-like alphabet
 * Example: "BC7F4A2E", "K9X3M7Q2", "R4W8P5N6"
 *
 * @author BitChat Team
 */
object HashIDGenerator {

    private const val TAG = "HashIDGenerator"

    /**
     * Base32-like alphabet without confusing characters
     * Excluded: 0, O, 1, I, L (to prevent confusion)
     * Total: 32 characters (5 bits per character)
     */
    internal const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    /**
     * Generate 8-character Hash ID from public key
     *
     * @param publicKey The Curve25519 public key (32 bytes)
     * @return 8-character Hash ID (e.g., "BC7F4A2E")
     */
    fun generateHashID(publicKey: ByteArray): String {
        require(publicKey.size == 32) { "Public key must be 32 bytes (Curve25519)" }

        // SHA-256 hash of public key
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey)

        // Take first 5 bytes (40 bits) = 8 characters in base32 (5 bits each)
        val bytes = hash.copyOfRange(0, 5)

        // Convert to Base32-like encoding
        return bytesToBase32(bytes, 8)
    }

    /**
     * Verify that a Hash ID is valid format
     *
     * @param hashID The Hash ID to validate
     * @return true if valid format
     */
    fun isValidHashID(hashID: String): Boolean {
        if (hashID.length != 8) return false
        return hashID.all { it in ALPHABET }
    }

    /**
     * Generate QR code compatible format (includes checksum)
     * Format: "bitchat://add/<hashID>/<checksum>"
     *
     * @param publicKey The public key
     * @return QR-ready URI string
     */
    fun generateQRCodeURI(publicKey: ByteArray): String {
        val hashID = generateHashID(publicKey)
        val checksum = calculateChecksum(hashID)
        return "bitchat://add/$hashID/$checksum"
    }

    /**
     * Parse and validate QR code URI
     *
     * @param uri The scanned URI
     * @return Hash ID if valid, null if invalid
     */
    fun parseQRCodeURI(uri: String): String? {
        // Expected format: "bitchat://add/<hashID>/<checksum>"
        val parts = uri.split("/")

        if (parts.size != 5) return null
        if (parts[0] != "bitchat:" || parts[2] != "add") return null

        val hashID = parts[3]
        val checksum = parts[4]

        // Validate format
        if (!isValidHashID(hashID)) return null

        // Validate checksum
        val expectedChecksum = calculateChecksum(hashID)
        if (checksum != expectedChecksum) {
            Log.w(TAG, "Invalid checksum for Hash ID: $hashID")
            return null
        }

        return hashID
    }

    /**
     * Convert Hash ID back to partial fingerprint (for lookup)
     * Note: This is lossy - only returns first 40 bits of hash
     *
     * @param hashID The 8-character Hash ID
     * @return First 5 bytes of original SHA-256 hash
     */
    fun hashIDToBytes(hashID: String): ByteArray? {
        if (!isValidHashID(hashID)) return null

        return try {
            base32ToBytes(hashID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode Hash ID: ${e.message}")
            null
        }
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Convert bytes to Base32-like string
     */
    private fun bytesToBase32(bytes: ByteArray, outputLength: Int): String {
        val bits = bytes.flatMap { byte ->
            (7 downTo 0).map { bit ->
                ((byte.toInt() shr bit) and 1) == 1
            }
        }

        val result = StringBuilder()
        for (i in 0 until outputLength) {
            val startBit = i * 5
            if (startBit + 5 > bits.size) break

            val value = (0 until 5).fold(0) { acc, bit ->
                (acc shl 1) or (if (bits[startBit + bit]) 1 else 0)
            }

            result.append(ALPHABET[value])
        }

        return result.toString()
    }

    /**
     * Convert Base32-like string back to bytes
     */
    private fun base32ToBytes(base32: String): ByteArray {
        val bits = mutableListOf<Boolean>()

        for (char in base32) {
            val value = ALPHABET.indexOf(char)
            if (value == -1) throw IllegalArgumentException("Invalid character: $char")

            // Convert to 5 bits
            for (bit in 4 downTo 0) {
                bits.add(((value shr bit) and 1) == 1)
            }
        }

        // Convert bits to bytes
        val bytes = mutableListOf<Byte>()
        for (i in 0 until bits.size / 8) {
            val startBit = i * 8
            val value = (0 until 8).fold(0) { acc, bit ->
                (acc shl 1) or (if (bits[startBit + bit]) 1 else 0)
            }
            bytes.add(value.toByte())
        }

        return bytes.toByteArray()
    }

    /**
     * Calculate 2-character checksum for error detection
     */
    private fun calculateChecksum(hashID: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(hashID.toByteArray())

        // Use first byte for checksum (2 base32 characters)
        val checksumByte = byteArrayOf(hash[0])
        return bytesToBase32(checksumByte, 2)
    }
}
