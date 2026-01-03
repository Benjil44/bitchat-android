package com.bitchat.android.ui.contacts

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import com.bitchat.android.identity.HashIDGenerator
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * MyQRCodeScreen - Display user's Hash ID and QR code for sharing
 *
 * Purpose:
 * - Show user's 8-character Hash ID
 * - Generate QR code from Hash ID
 * - Allow copying Hash ID to clipboard
 * - Share Hash ID with other apps
 *
 * Iran Use Case:
 * - Easy identity sharing without phone numbers
 * - In-person QR scanning for trusted contacts
 * - Can share Hash ID via SMS, Signal, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQRCodeScreen(
    myHashID: String,
    myQRCodeURI: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Generate QR code
    val qrCodeBitmap = remember(myQRCodeURI) {
        generateQRCode(myQRCodeURI, size = 512)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Identity") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Instructions
            Text(
                text = "Share this with friends to let them add you",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Hash ID Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Your Hash ID",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    // Hash ID in large, monospace font
                    Text(
                        text = myHashID,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp,
                            letterSpacing = 4.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    // Copy button
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(myHashID))
                            Toast.makeText(context, "Hash ID copied!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Hash ID")
                    }
                }
            }

            // QR Code
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrCodeBitmap != null) {
                        Image(
                            bitmap = qrCodeBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            "Failed to generate QR code",
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Share button
            Button(
                onClick = {
                    shareHashID(context, myHashID)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, "Share")
                Spacer(Modifier.width(8.dp))
                Text("Share Hash ID")
            }

            Spacer(Modifier.weight(1f))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "💡 How to share",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "• In person: Let friends scan your QR code\n" +
                        "• Remote: Share your Hash ID via text, Signal, etc.\n" +
                        "• Your Hash ID is safe to share publicly",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Generate QR code bitmap from URI
 */
private fun generateQRCode(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                )
            }
        }

        bitmap
    } catch (e: Exception) {
        android.util.Log.e("MyQRCodeScreen", "Failed to generate QR code: ${e.message}")
        null
    }
}

/**
 * Share Hash ID via Android share sheet
 */
private fun shareHashID(context: android.content.Context, hashID: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "My BitChat Hash ID")
        putExtra(Intent.EXTRA_TEXT, """
            Add me on BitChat!

            My Hash ID: $hashID

            1. Open BitChat
            2. Go to Settings → Add Friend
            3. Enter my Hash ID: $hashID
        """.trimIndent())
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share Hash ID via..."))
}

/**
 * Preview for development
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun MyQRCodeScreenPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("My Identity") },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                )

                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Your Hash ID")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "BC7F4A2E",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("QR Code would appear here")
                }
            }
        }
    }
}
