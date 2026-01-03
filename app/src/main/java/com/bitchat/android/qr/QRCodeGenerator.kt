package com.bitchat.android.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR code generator for BitChat contact sharing.
 *
 * Uses ZXing library to generate QR codes from contact data.
 */
object QRCodeGenerator {

    /**
     * Generate QR code bitmap from contact data
     *
     * @param data QR contact data
     * @param size QR code size in pixels (default: 512)
     * @param foregroundColor QR foreground color (default: black)
     * @param backgroundColor QR background color (default: white)
     * @return QR code bitmap
     */
    fun generate(
        data: QRContactData,
        size: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        // Encode contact data to JSON
        val content = QRContactData.encode(data)

        // Configure QR code writer
        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )

        // Generate QR code matrix
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        // Convert to bitmap
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix[x, y]) foregroundColor else backgroundColor
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        return bitmap
    }

    /**
     * Generate QR code from user identity
     *
     * @param displayName User's nickname
     * @param noisePublicKey Noise static public key
     * @param signingPublicKey Ed25519 signing public key (optional)
     * @param size QR code size in pixels
     * @return QR code bitmap
     */
    fun generateFromIdentity(
        displayName: String,
        noisePublicKey: ByteArray,
        signingPublicKey: ByteArray? = null,
        size: Int = 512
    ): Bitmap {
        val data = QRContactData.create(displayName, noisePublicKey, signingPublicKey)
        return generate(data, size)
    }
}
