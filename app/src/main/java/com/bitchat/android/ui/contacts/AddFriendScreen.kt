package com.bitchat.android.ui.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.bitchat.android.contacts.ContactManager
import com.bitchat.android.data.entity.ContactEntity
import com.bitchat.android.identity.HashIDGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AddFriendScreen - UI for adding friends by Hash ID or QR code
 *
 * Purpose:
 * - Add friends manually by typing Hash ID
 * - Add friends by scanning QR code
 * - Verify Hash ID format before adding
 * - Give user feedback on success/failure
 *
 * Iran Use Case:
 * - Easy friend discovery without phone numbers
 * - In-person QR scanning for trusted contacts
 * - Privacy-preserving (no real names required)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendScreen(
    onNavigateBack: () -> Unit,
    onScanQRCode: () -> Unit,
    contactManager: ContactManager,
    scope: CoroutineScope
) {
    val context = LocalContext.current

    // State
    var hashID by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Friend") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions
            Text(
                text = "Add a friend by entering their 8-character Hash ID",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Hash ID input
            OutlinedTextField(
                value = hashID,
                onValueChange = {
                    // Only allow valid characters, max 8 chars
                    val filtered = it.uppercase()
                        .filter { char -> char in HashIDGenerator.ALPHABET }
                        .take(8)
                    hashID = filtered
                    error = null // Clear error on input
                },
                label = { Text("Friend's Hash ID") },
                placeholder = { Text("BC7F4A2E") },
                isError = error != null,
                supportingText = {
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("8 characters (letters and numbers)")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { /* Focus moves to custom name */ }
                )
            )

            // Custom name input (optional)
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text("Custom Name (Optional)") },
                placeholder = { Text("Sara from protest") },
                supportingText = {
                    Text("For privacy, use a nickname instead of real name")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        // Trigger add when user presses Done
                        if (hashID.length == 8) {
                            addFriend(
                                hashID = hashID,
                                customName = customName.ifBlank { null },
                                contactManager = contactManager,
                                scope = scope,
                                context = context,
                                onSuccess = {
                                    Toast.makeText(context, "Friend added!", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                },
                                onError = { errorMsg ->
                                    error = errorMsg
                                },
                                setLoading = { isLoading = it }
                            )
                        }
                    }
                )
            )

            // Add button
            Button(
                onClick = {
                    if (hashID.length != 8) {
                        error = "Hash ID must be exactly 8 characters"
                        return@Button
                    }

                    if (!HashIDGenerator.isValidHashID(hashID)) {
                        error = "Invalid Hash ID format"
                        return@Button
                    }

                    addFriend(
                        hashID = hashID,
                        customName = customName.ifBlank { null },
                        contactManager = contactManager,
                        scope = scope,
                        context = context,
                        onSuccess = {
                            Toast.makeText(context, "Friend added!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                        onError = { errorMsg ->
                            error = errorMsg
                        },
                        setLoading = { isLoading = it }
                    )
                },
                enabled = !isLoading && hashID.length == 8,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Add Friend")
            }

            // Divider with "OR"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(modifier = Modifier.weight(1f))
                Text(
                    "OR",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider(modifier = Modifier.weight(1f))
            }

            // QR Code scanner button
            OutlinedButton(
                onClick = onScanQRCode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, "Scan QR Code")
                Spacer(Modifier.width(8.dp))
                Text("Scan QR Code")
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
                        "💡 How to add friends",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "• Ask your friend for their 8-character Hash ID\n" +
                        "• Or meet in person and scan their QR code\n" +
                        "• Once added, you can message each other privately",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Helper function to add friend
 */
private fun addFriend(
    hashID: String,
    customName: String?,
    contactManager: ContactManager,
    scope: CoroutineScope,
    context: android.content.Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    setLoading: (Boolean) -> Unit
) {
    setLoading(true)

    scope.launch(Dispatchers.IO) {
        try {
            val contact = contactManager.addContactByHashID(
                hashID = hashID,
                customName = customName,
                verificationMethod = ContactEntity.VERIFICATION_MANUAL_ID
            )

            launch(Dispatchers.Main) {
                setLoading(false)
                if (contact != null) {
                    onSuccess()
                } else {
                    onError("Failed to add friend. Please try again.")
                }
            }
        } catch (e: Exception) {
            launch(Dispatchers.Main) {
                setLoading(false)
                onError("Error: ${e.message}")
            }
        }
    }
}

/**
 * Preview for development
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AddFriendScreenPreview() {
    MaterialTheme {
        // Note: This preview won't work fully without real ContactManager
        // but shows the UI layout
        Surface {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Add Friend") },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                )
                Text("Preview - UI only", modifier = Modifier.padding(16.dp))
            }
        }
    }
}
