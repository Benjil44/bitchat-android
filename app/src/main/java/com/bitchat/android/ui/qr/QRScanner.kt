package com.bitchat.android.ui.qr

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * QR Scanner for BitChat
 *
 * Purpose:
 * - Scan QR codes to quickly add friends
 * - Extract Hash ID from QR code data
 * - Handle camera permissions gracefully
 *
 * Iran Use Case:
 * - Fast in-person friend adding during protests
 * - No typing errors (QR is more reliable than manual entry)
 * - Quick contact exchange before police arrive
 */

/**
 * Remembers a QR code scanner launcher that handles camera permissions
 * and returns the scanned Hash ID
 *
 * @param onScanned Callback with the scanned Hash ID (8 characters)
 * @param onError Callback when scanning fails or is cancelled
 * @return A function to launch the scanner
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberQRScanner(
    onScanned: (String) -> Unit,
    onError: (String?) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current

    // Camera permission state
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // QR scanner launcher
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        if (result.contents == null) {
            // Scan cancelled or failed
            onError("Scan cancelled")
        } else {
            // Extract Hash ID from QR code content
            val scannedData = result.contents
            val hashID = extractHashID(scannedData)

            if (hashID != null && hashID.length == 8) {
                onScanned(hashID)
            } else {
                onError("Invalid QR code. This is not a BitChat friend QR code.")
            }
        }
    }

    // Return launch function
    return {
        if (cameraPermission.status.isGranted) {
            // Permission already granted, launch scanner
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan friend's QR code")
                setBeepEnabled(true)
                setOrientationLocked(false)
                setBarcodeImageEnabled(false)
            }
            scannerLauncher.launch(options)
        } else {
            // Request permission
            cameraPermission.launchPermissionRequest()
        }
    }
}

/**
 * Extract Hash ID from QR code data
 *
 * QR code format: "bitchat://add/{hashID}"
 * or just the raw Hash ID: "BC7F4A2E"
 *
 * @param qrData The scanned QR code content
 * @return The 8-character Hash ID, or null if invalid
 */
private fun extractHashID(qrData: String): String? {
    return when {
        // Format 1: bitchat://add/{hashID}
        qrData.startsWith("bitchat://add/", ignoreCase = true) -> {
            qrData.substring(14).take(8).uppercase()
        }
        // Format 2: Raw Hash ID (8 characters)
        qrData.length == 8 -> {
            qrData.uppercase()
        }
        // Format 3: Longer raw data (might be public key, extract first 8 chars)
        qrData.length > 8 -> {
            // Try to extract Hash ID if it's a valid format
            qrData.take(8).uppercase()
        }
        else -> null
    }
}

/**
 * Simple QR scanner launcher without permission handling
 * Use this when you've already handled permissions
 */
@Composable
fun rememberSimpleQRScanner(
    onResult: (String?) -> Unit
): () -> Unit {
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        onResult(result.contents)
    }

    return {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan friend's QR code")
            setBeepEnabled(true)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
        }
        scannerLauncher.launch(options)
    }
}
