package com.bitchat.android.ui.contacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitchat.android.contacts.ContactManager
import com.bitchat.android.data.entity.ContactEntity
import kotlinx.coroutines.CoroutineScope
import java.text.SimpleDateFormat
import java.util.*

/**
 * ContactListScreen - Telegram-style contact list
 *
 * Purpose:
 * - Show all contacts sorted by activity
 * - Display unread message counts
 * - Show online/offline status
 * - Long-press menu for contact actions
 * - Search contacts
 *
 * Iran Use Case:
 * - See which trusted friends are online
 * - Quick access to contacts
 * - Unread message badges
 * - Organized, clean interface
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    contactManager: ContactManager,
    onContactClick: (ContactEntity) -> Unit,
    onAddFriendClick: () -> Unit,
    onMyQRCodeClick: () -> Unit,
    scope: CoroutineScope
) {
    // State
    val contacts by contactManager.getAllContactsFlow()
        .collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<ContactEntity?>(null) }

    // Filter contacts based on search
    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter { contact ->
                contact.getEffectiveName().contains(searchQuery, ignoreCase = true) ||
                contact.hashID.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Group contacts
    val favoriteContacts = filteredContacts.filter { it.isFavorite }
    val regularContacts = filteredContacts.filter { !it.isFavorite }
    val onlineContacts = filteredContacts.filter { it.isConnected }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Contacts") },
                    actions = {
                        // My QR Code button
                        IconButton(onClick = onMyQRCodeClick) {
                            Icon(Icons.Default.QrCode, "My QR Code")
                        }

                        // Add friend button
                        IconButton(onClick = onAddFriendClick) {
                            Icon(Icons.Default.PersonAdd, "Add Friend")
                        }

                        // More menu
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort by name") },
                                onClick = { /* TODO */ }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by recent") },
                                onClick = { /* TODO */ }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                // Search bar
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { },
                    active = false,
                    onActiveChange = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search contacts...") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search") }
                ) { }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Statistics header
            item {
                ContactStatsHeader(
                    totalContacts = contacts.size,
                    onlineContacts = onlineContacts.size,
                    unreadCount = contacts.sumOf { it.unreadCount }
                )
            }

            // Favorites section
            if (favoriteContacts.isNotEmpty()) {
                item {
                    SectionHeader("FAVORITES")
                }
                items(favoriteContacts) { contact ->
                    ContactListItem(
                        contact = contact,
                        onClick = { onContactClick(contact) },
                        onLongClick = { selectedContact = contact }
                    )
                }
            }

            // Regular contacts section
            if (regularContacts.isNotEmpty()) {
                item {
                    SectionHeader("CONTACTS")
                }
                items(regularContacts) { contact ->
                    ContactListItem(
                        contact = contact,
                        onClick = { onContactClick(contact) },
                        onLongClick = { selectedContact = contact }
                    )
                }
            }

            // Empty state
            if (contacts.isEmpty()) {
                item {
                    EmptyContactsState(onAddFriendClick = onAddFriendClick)
                }
            }

            // No search results
            if (contacts.isNotEmpty() && filteredContacts.isEmpty()) {
                item {
                    NoSearchResults(searchQuery = searchQuery)
                }
            }
        }

        // Long-press menu (bottom sheet)
        if (selectedContact != null) {
            ContactActionSheet(
                contact = selectedContact!!,
                contactManager = contactManager,
                scope = scope,
                onDismiss = { selectedContact = null },
                onOpenChat = {
                    onContactClick(selectedContact!!)
                    selectedContact = null
                }
            )
        }
    }
}

/**
 * Statistics header showing contact counts
 */
@Composable
fun ContactStatsHeader(
    totalContacts: Int,
    onlineContacts: Int,
    unreadCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.People,
                label = "Total",
                value = totalContacts.toString()
            )
            StatItem(
                icon = Icons.Default.Check,
                label = "Online",
                value = onlineContacts.toString(),
                valueColor = MaterialTheme.colorScheme.primary
            )
            StatItem(
                icon = Icons.Default.Notifications,
                label = "Unread",
                value = unreadCount.toString(),
                valueColor = if (unreadCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * Section header (e.g., "FAVORITES", "CONTACTS")
 */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * Individual contact list item (Telegram-style)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactListItem(
    contact: ContactEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with first letter
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (contact.isFavorite) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.getEffectiveName().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (contact.isFavorite) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(12.dp))

        // Name and status
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.getEffectiveName(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (contact.isTrusted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Last message time or status
            if (contact.lastMessageAt != null) {
                Text(
                    text = formatTimeAgo(contact.lastMessageAt!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No messages yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Right side: unread badge and online indicator
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Unread badge
            if (contact.unreadCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        contact.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Online indicator
            if (contact.isConnected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)) // Green
                )
            }
        }
    }

    Divider()
}

/**
 * Empty state when no contacts
 */
@Composable
fun EmptyContactsState(onAddFriendClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.PeopleOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "No contacts yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "Add friends to start messaging securely",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(onClick = onAddFriendClick) {
            Icon(Icons.Default.PersonAdd, null)
            Spacer(Modifier.width(8.dp))
            Text("Add Your First Friend")
        }
    }
}

/**
 * No search results state
 */
@Composable
fun NoSearchResults(searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "No contacts found for \"$searchQuery\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Bottom sheet with contact actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactActionSheet(
    contact: ContactEntity,
    contactManager: ContactManager,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header with contact info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        contact.getEffectiveName().first().uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        contact.getEffectiveName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        contact.hashID,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()

            // Actions
            ContactActionItem(
                icon = Icons.Default.Chat,
                label = "Open Chat",
                onClick = onOpenChat
            )

            ContactActionItem(
                icon = if (contact.isFavorite) Icons.Default.StarBorder else Icons.Default.Star,
                label = if (contact.isFavorite) "Remove from Favorites" else "Add to Favorites",
                onClick = {
                    contactManager.setFavorite(contact.hashID, !contact.isFavorite)
                    onDismiss()
                }
            )

            ContactActionItem(
                icon = if (contact.isTrusted) Icons.Default.VerifiedUser else Icons.Default.Shield,
                label = if (contact.isTrusted) "Mark as Unverified" else "Mark as Verified",
                onClick = {
                    contactManager.setTrusted(contact.hashID, !contact.isTrusted)
                    onDismiss()
                }
            )

            ContactActionItem(
                icon = Icons.Default.Edit,
                label = "Rename Contact",
                onClick = {
                    // TODO: Show rename dialog
                    onDismiss()
                }
            )

            ContactActionItem(
                icon = Icons.Default.Block,
                label = "Block Contact",
                iconTint = MaterialTheme.colorScheme.error,
                onClick = {
                    contactManager.setBlocked(contact.hashID, true)
                    onDismiss()
                }
            )

            ContactActionItem(
                icon = Icons.Default.Delete,
                label = "Remove Contact",
                iconTint = MaterialTheme.colorScheme.error,
                onClick = {
                    // TODO: Show confirmation dialog
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun ContactActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Format timestamp as "5m ago", "2h ago", "Yesterday", etc.
 */
private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 172800_000 -> "Yesterday"
        else -> {
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
