package com.bitchat.android.qr

import com.google.gson.Gson
import android.util.Log

/**
 * QR code contact data format for BitChat.
 *
 * Format: JSON encoding of contact information
 * Purpose: Share contact info via QR code for easy friend adding
 *
 * Example QR content:
 * ```json
 * {
 *   "v": 1,
 *   "n": "Alice",
 *   "npk": "a1b2c3d4...",
 *   "spk": "e5f6g7h8..."
 * }
 * ```
 *
 * Security: QR codes are PUBLIC - anyone can scan them.
 * - Only share QR in person with trusted friends
 * - QR contains public keys only (safe to share)
 * - Verify identity in person before marking as trusted
 */
data class QRContactData(
    /**
     * Format version (currently 1)
     * Future versions may add more fields
     */
    val v: Int = 1,

    /**
     * Display name / nickname
     */
    val n: String,

    /**
     * Noise static public key (32 bytes as hex string)
     * This is the cryptographic identity used for E2EE
     */
    val npk: String,

    /**
     * Ed25519 signing public key (32 bytes as hex string, optional)
     * Used for verifying identity announcements
     */
    val spk: String? = null
) {
    companion object {
        private const val TAG = "QRContactData"
        private const val VERSION = 1

        /**
         * Create QR contact data from user's identity
         *
         * @param displayName User's nickname
         * @param noisePublicKey Noise static public key (32 bytes)
         * @param signingPublicKey Ed25519 signing public key (32 bytes, optional)
         * @return QR contact data
         */
        fun create(
            displayName: String,
            noisePublicKey: ByteArray,
            signingPublicKey: ByteArray? = null
        ): QRContactData {
            val npk = noisePublicKey.joinToString("") { "%02x".format(it) }
            val spk = signingPublicKey?.joinToString("") { "%02x".format(it) }

            return QRContactData(
                v = VERSION,
                n = displayName,
                npk = npk,
                spk = spk
            )
        }

        /**
         * Encode QR contact data to JSON string (for QR code generation)
         *
         * @param data Contact data
         * @return JSON string
         */
        fun encode(data: QRContactData): String {
            return Gson().toJson(data)
        }

        /**
         * Decode JSON string to QR contact data (from scanned QR code)
         *
         * @param json JSON string from QR code
         * @return Decoded contact data, or null if invalid
         */
        fun decode(json: String): QRContactData? {
            return try {
                val data = Gson().fromJson(json, QRContactData::class.java)

                // Validate format
                if (data.v != VERSION) {
                    Log.w(TAG, "Unsupported QR version: ${data.v}")
                    return null
                }

                // Validate display name
                if (data.n.isBlank()) {
                    Log.w(TAG, "Invalid QR: empty display name")
                    return null
                }

                // Validate Noise public key (must be 64 hex chars = 32 bytes)
                if (data.npk.length != 64 || !data.npk.matches(Regex("[0-9a-fA-F]{64}"))) {
                    Log.w(TAG, "Invalid QR: invalid Noise public key")
                    return null
                }

                // Validate signing public key if present
                if (data.spk != null && (data.spk.length != 64 || !data.spk.matches(Regex("[0-9a-fA-F]{64}")))) {
                    Log.w(TAG, "Invalid QR: invalid signing public key")
                    return null
                }

                Log.d(TAG, "✅ Valid QR contact data: ${data.n}")
                data
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode QR: ${e.message}")
                null
            }
        }

        /**
         * Convert QR contact data back to byte arrays
         *
         * @param data QR contact data
         * @return Triple of (noisePublicKey, signingPublicKey, displayName)
         */
        fun toBytes(data: QRContactData): Triple<ByteArray, ByteArray?, String> {
            val noisePublicKey = data.npk.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()

            val signingPublicKey = data.spk?.chunked(2)
                ?.map { it.toInt(16).toByte() }
                ?.toByteArray()

            return Triple(noisePublicKey, signingPublicKey, data.n)
        }
    }
}
