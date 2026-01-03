package com.bitchat.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.identity.SecureIdentityStateManager
import com.bitchat.android.qr.QRCodeGenerator

/**
 * QR code display sheet - shows user's QR code for others to scan
 *
 * Use case: User taps "Show My QR" → Other person scans with camera → Auto-add as contact
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRDisplaySheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    val identityManager = remember { SecureIdentityStateManager(context) }

    // Load user's identity
    val staticKeyPair = remember { identityManager.loadStaticKey() }
    val signingKeyPair = remember { identityManager.loadSigningKey() }
    val nickname = remember { dataManager.loadNickname() }

    // Generate QR code
    val qrBitmap = remember(staticKeyPair, signingKeyPair, nickname) {
        staticKeyPair?.let { (_, publicKey) ->
            val signingPublicKey = signingKeyPair?.second
            QRCodeGenerator.generateFromIdentity(
                displayName = nickname,
                noisePublicKey = publicKey,
                signingPublicKey = signingPublicKey,
                size = 800 // High resolution for scanning
            )
        }
    }

    // Configure bottom sheet to start fully expanded
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "My QR Code",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Nickname
            Text(
                text = nickname,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // QR Code
            if (qrBitmap != null) {
                Surface(
                    modifier = Modifier
                        .size(300.dp)
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code for $nickname",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
            } else {
                // Error state
                Text(
                    text = "❌ Failed to generate QR code",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions
            Text(
                text = "Let your friend scan this QR code to add you as a contact",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "⚠️ Only share with people you trust in person",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Close button
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
