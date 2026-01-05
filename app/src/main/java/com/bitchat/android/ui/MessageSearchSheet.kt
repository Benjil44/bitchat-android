package com.bitchat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.data.StoragePreferences
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.core.ui.component.button.CloseButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Message Search Sheet
 *
 * Allows searching through persisted message history.
 * Requires message persistence to be enabled in settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSearchSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    onMessageClick: (String, BitchatMessage) -> Unit, // (peerID, message)
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // Check if persistence is enabled
    val persistenceEnabled = remember { StoragePreferences.isEnabled(context) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Pair<String, BitchatMessage>>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Search scope filter
    var searchScope by remember { mutableStateOf(SearchScope.ALL_CHATS) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    // Perform search when query changes
    val selectedPeer by viewModel.selectedPrivateChatPeer.collectAsStateWithLifecycle()

    LaunchedEffect(searchQuery, searchScope) {
        if (searchQuery.length < 2) {
            searchResults = emptyList()
            return@LaunchedEffect
        }

        isSearching = true

        val peerID = when (searchScope) {
            SearchScope.CURRENT_CHAT -> selectedPeer
            SearchScope.ALL_CHATS -> null
        }

        try {
            val results = viewModel.searchMessages(searchQuery, peerID)
            searchResults = results
        } catch (e: Exception) {
            android.util.Log.e("MessageSearch", "Search failed: ${e.message}")
        } finally {
            isSearching = false
        }
    }

    if (isPresented) {
        ModalBottomSheet(
            modifier = modifier.statusBarsPadding(),
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = colorScheme.background,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                // Header with close button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Search Messages",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )

                    CloseButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }

                // Persistence disabled warning
                if (!persistenceEnabled) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = colorScheme.errorContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Message History Disabled",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.error
                                )
                                Text(
                                    text = "Enable 'Message History' in Settings to search messages",
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Search input
                SearchInput(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClear = { searchQuery = "" },
                    enabled = persistenceEnabled,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Search scope filter
                val selectedPeer by viewModel.selectedPrivateChatPeer.collectAsStateWithLifecycle()
                if (persistenceEnabled && selectedPeer != null) {
                    SearchScopeToggle(
                        scope = searchScope,
                        onScopeChange = { searchScope = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = colorScheme.outline.copy(alpha = 0.2f)
                )

                // Search results
                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = colorScheme.primary
                        )
                    }
                } else if (searchQuery.isNotEmpty() && searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.length < 2) {
                                "Type at least 2 characters to search"
                            } else {
                                "No messages found for \"$searchQuery\""
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else if (searchResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(searchResults) { (peerID, message) ->
                            SearchResultItem(
                                peerID = peerID,
                                message = message,
                                searchQuery = searchQuery,
                                onClick = {
                                    onMessageClick(peerID, message)
                                    onDismiss()
                                },
                                viewModel = viewModel
                            )
                        }
                    }
                } else {
                    // Initial state - show search tips
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Text(
                                text = "Search your messages",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Type to search message content",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = colorScheme.onSurface.copy(alpha = if (enabled) 0.6f else 0.3f),
                modifier = Modifier.size(20.dp)
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = colorScheme.onSurface
                ),
                cursorBrush = SolidColor(colorScheme.primary),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search messages...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface.copy(alpha = if (enabled) 0.4f else 0.2f)
                        )
                    }
                    innerTextField()
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchScopeToggle(
    scope: SearchScope,
    onScopeChange: (SearchScope) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchScopeChip(
            label = "All Chats",
            selected = scope == SearchScope.ALL_CHATS,
            onClick = { onScopeChange(SearchScope.ALL_CHATS) },
            modifier = Modifier.weight(1f)
        )
        SearchScopeChip(
            label = "Current Chat",
            selected = scope == SearchScope.CURRENT_CHAT,
            onClick = { onScopeChange(SearchScope.CURRENT_CHAT) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SearchScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f

    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Color.White else colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    peerID: String,
    message: BitchatMessage,
    searchQuery: String,
    onClick: () -> Unit,
    viewModel: ChatViewModel
) {
    val colorScheme = MaterialTheme.colorScheme
    val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()

    // Get peer nickname
    val peerNickname = peerNicknames[peerID] ?: peerID.take(12)

    // Format timestamp
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val timestamp = dateFormat.format(message.timestamp)

    // Highlight search query in message content
    val highlightedContent = buildAnnotatedString {
        val content = message.content
        var lastIndex = 0

        // Find all occurrences of search query (case-insensitive)
        val regex = Regex(searchQuery, RegexOption.IGNORE_CASE)
        regex.findAll(content).forEach { match ->
            // Add text before match
            append(content.substring(lastIndex, match.range.first))

            // Add highlighted match
            withStyle(SpanStyle(
                background = Color(0xFFFFEB3B),
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )) {
                append(content.substring(match.range))
            }

            lastIndex = match.range.last + 1
        }

        // Add remaining text
        if (lastIndex < content.length) {
            append(content.substring(lastIndex))
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: peer nickname + timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = peerNickname,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF9500) // Orange like private chat header
                )

                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }

            // Message content with highlighting
            androidx.compose.material3.Text(
                text = highlightedContent,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 3,
                lineHeight = 18.sp
            )
        }
    }
}

enum class SearchScope {
    ALL_CHATS,
    CURRENT_CHAT
}
