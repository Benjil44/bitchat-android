package com.bitchat.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bitchat.android.ui.DataManager

/**
 * ContactSettingsSection - Settings UI for contact management
 *
 * Purpose:
 * - Toggle contact filtering on/off
 * - Navigate to Add Friend screen
 * - Navigate to My QR Code screen
 * - Navigate to Contact List
 * - Show filtering status
 *
 * Iran Use Case:
 * - Quick access to contact features
 * - Enable/disable message filtering
 * - Easy friend management
 *
 * Integration:
 * Add this composable to your existing Settings screen
 */
@Composable
fun ContactSettingsSection(
    dataManager: DataManager,
    onAddFriendClick: () -> Unit,
    onMyQRCodeClick: () -> Unit,
    onContactListClick: () -> Unit
) {
    var showContactsOnly by remember {
        mutableStateOf(dataManager.isShowContactsOnly())
    }

    var acceptFriendRequests by remember {
        mutableStateOf(dataManager.isAcceptFriendRequestsEnabled())
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section header
        Text(
            text = "Privacy & Contacts",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Contact filtering toggle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show contacts only",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (showContactsOnly)
                                "Only receive messages from friends"
                            else
                                "Receive messages from anyone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = showContactsOnly,
                        onCheckedChange = { enabled ->
                            showContactsOnly = enabled
                            dataManager.setShowContactsOnly(enabled)
                        }
                    )
                }

                if (showContactsOnly) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Protected: Unknown senders filtered",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Quick actions
        Text(
            text = "Friends & Identity",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Manage Contacts button
        SettingsButton(
            icon = Icons.Default.People,
            title = "Manage Contacts",
            subtitle = "View and organize your friends",
            onClick = onContactListClick
        )

        // My QR Code button
        SettingsButton(
            icon = Icons.Default.QrCode,
            title = "My QR Code",
            subtitle = "Share your Hash ID with friends",
            onClick = onMyQRCodeClick
        )

        // Add Friend button
        SettingsButton(
            icon = Icons.Default.PersonAdd,
            title = "Add Friend",
            subtitle = "Add a new contact by Hash ID or QR code",
            onClick = onAddFriendClick
        )

        // Advanced settings
        Text(
            text = "Advanced",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Accept friend requests toggle (for future friend request feature)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Accept friend requests",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Allow others to send you friend requests",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = acceptFriendRequests,
                    onCheckedChange = { enabled ->
                        acceptFriendRequests = enabled
                        dataManager.setAcceptFriendRequests(enabled)
                    }
                )
            }
        }

        // Info card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Contact filtering helps protect your privacy by only showing messages from people you've added as contacts. Recommended for protests and sensitive situations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * Settings button component
 */
@Composable
fun SettingsButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Preview for development
 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun ContactSettingsSectionPreview() {
    MaterialTheme {
        Surface {
            Column {
                Text(
                    "Settings Preview",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                // Note: Preview won't work fully without real DataManager
                // This just shows the UI layout
            }
        }
    }
}
