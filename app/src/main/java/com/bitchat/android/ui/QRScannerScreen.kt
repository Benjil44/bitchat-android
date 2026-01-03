package com.bitchat.android.ui

import android.Manifest
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bitchat.android.qr.QRContactData
import com.bitchat.android.data.entity.ContactEntity
import com.bitchat.android.data.repository.ContactRepository
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.launch

/**
 * QR scanner screen - scan QR codes to add contacts
 *
 * Use case: User taps "Scan QR Code" → Points camera at friend's QR → Auto-add as contact
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onContactAdded: (ContactEntity) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contactRepository = remember { ContactRepository.getInstance(context) }

    // Camera permission state
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Scanning state
    var isScanning by remember { mutableStateOf(true) }
    var scannedContact by remember { mutableStateOf<ContactEntity?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Request camera permission on first compose
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                // Camera permission not granted
                !cameraPermissionState.status.isGranted -> {
                    CameraPermissionRequiredView(
                        shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
                        onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                        onClose = onClose
                    )
                }

                // Contact scanned successfully
                scannedContact != null -> {
                    ContactScannedSuccessView(
                        contact = scannedContact!!,
                        onConfirm = {
                            onContactAdded(scannedContact!!)
                            onClose()
                        },
                        onScanAnother = {
                            scannedContact = null
                            errorMessage = null
                            isScanning = true
                        },
                        onClose = onClose
                    )
                }

                // Show error
                errorMessage != null -> {
                    ScanErrorView(
                        error = errorMessage!!,
                        onTryAgain = {
                            errorMessage = null
                            isScanning = true
                        },
                        onClose = onClose
                    )
                }

                // Camera scanner
                isScanning -> {
                    QRCameraScanner(
                        onQRScanned = { qrContent ->
                            isScanning = false

                            // Decode QR content
                            val qrData = QRContactData.decode(qrContent)
                            if (qrData == null) {
                                errorMessage = "Invalid QR code format"
                                return@QRCameraScanner
                            }

                            // Convert to contact entity
                            try {
                                val (noisePublicKey, signingPublicKey, displayName) = QRContactData.toBytes(qrData)

                                val contact = ContactEntity.create(
                                    noisePublicKey = noisePublicKey,
                                    signingPublicKey = signingPublicKey,
                                    displayName = displayName,
                                    isTrusted = true, // QR scan = in-person verification
                                    verificationMethod = ContactEntity.VERIFICATION_QR_SCAN
                                )

                                // Save contact to database
                                scope.launch {
                                    try {
                                        contactRepository.insertOrUpdateContact(contact)
                                        scannedContact = contact
                                        Log.d("QRScanner", "✅ Contact added: $displayName (${contact.hashID})")
                                    } catch (e: Exception) {
                                        errorMessage = "Failed to save contact: ${e.message}"
                                        Log.e("QRScanner", "Failed to save contact", e)
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = "Failed to parse contact data: ${e.message}"
                                Log.e("QRScanner", "Failed to parse QR data", e)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Camera scanner view
 */
@Composable
private fun QRCameraScanner(
    onQRScanned: (String) -> Unit
) {
    var hasScanned by remember { mutableStateOf(false) }
    var barcodeView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }

    // Cleanup camera when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            barcodeView?.pause()
            barcodeView = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Camera preview
        AndroidView(
            factory = { context ->
                DecoratedBarcodeView(context).apply {
                    barcodeView = this
                    val formats = listOf(BarcodeFormat.QR_CODE)
                    this.barcodeView.decoderFactory = DefaultDecoderFactory(formats)

                    decodeContinuous(object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult?) {
                            if (!hasScanned && result?.text != null) {
                                hasScanned = true
                                onQRScanned(result.text)
                            }
                        }
                    })

                    resume()
                }
            },
            onRelease = { view ->
                view.pause()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Instructions
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Point camera at QR code",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scanning will happen automatically",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Camera permission required view
 */
@Composable
private fun CameraPermissionRequiredView(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📷",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Camera Permission Required",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (shouldShowRationale) {
                "BitChat needs camera access to scan QR codes for adding contacts"
            } else {
                "Please grant camera permission to scan QR codes"
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        FilledTonalButton(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permission")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

/**
 * Contact scanned success view
 */
@Composable
private fun ContactScannedSuccessView(
    contact: ContactEntity,
    onConfirm: () -> Unit,
    onScanAnother: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✅",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Contact Added!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = contact.displayName,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ID: ${contact.hashID}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        FilledTonalButton(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Contact")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onScanAnother,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan Another")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Close")
        }
    }
}

/**
 * Scan error view
 */
@Composable
private fun ScanErrorView(
    error: String,
    onTryAgain: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "❌",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Scan Failed",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        FilledTonalButton(
            onClick = onTryAgain,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Try Again")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Close")
        }
    }
}
