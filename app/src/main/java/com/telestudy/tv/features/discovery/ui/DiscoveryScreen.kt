package com.telestudy.tv.features.discovery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telestudy.tv.domain.model.TelegramChat
import com.telestudy.tv.domain.model.TelegramVideo
import com.telestudy.tv.features.discovery.DiscoveryViewModel
import com.telestudy.tv.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Telegram Discovery & Room Persistence",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Phase 3 Completeness Verification",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncChats() },
                        enabled = !uiState.isSyncingChats
                    ) {
                        if (uiState.isSyncingChats) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = BrandTeal,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync Chats",
                                tint = BrandTeal
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Status and Error Banners
            if (uiState.statusMessage != null) {
                Surface(
                    color = BrandBlue.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(1.dp, BrandBlue.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isSyncingChats || uiState.isSyncingVideos) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = BrandTeal,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = uiState.statusMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                        )
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Surface(
                    color = ErrorRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(1.dp, ErrorRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(color = ErrorRed),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Sync Chats Button & Stats Bar
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Chats in Room: ${uiState.chats.size}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BrandTeal
                            )
                        )
                        val totalVideos = uiState.videos.size
                        Text(
                            text = if (uiState.selectedChat != null) "Videos for selected chat: $totalVideos" else "Select a chat to inspect videos",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }

                    Button(
                        onClick = { viewModel.syncChats() },
                        enabled = !uiState.isSyncingChats,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Text("Sync All Chats")
                    }
                }
            }

            var searchQuery by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

            // Main Content Area
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left column: Discovered Chats List
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                ) {
                    val filteredChats = if (searchQuery.isBlank()) {
                        uiState.chats
                    } else {
                        uiState.chats.filter {
                            it.title.contains(searchQuery, ignoreCase = true) || it.id.toString().contains(searchQuery)
                        }
                    }

                    Text(
                        text = "Discovered Chats (${filteredChats.size}/${uiState.chats.size})",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search 1407 chats...", fontSize = 12.sp, color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandTeal,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    if (filteredChats.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkSurface, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.isSyncingChats) "Discovering chats..." else if (uiState.chats.isEmpty()) "No chats discovered yet.\nTap 'Sync All Chats' above." else "No matches found.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredChats, key = { it.id }) { chat ->
                                ChatItem(
                                    chat = chat,
                                    isSelected = chat.id == uiState.selectedChat?.id,
                                    onClick = { viewModel.selectChat(chat) }
                                )
                            }
                        }
                    }
                }

                // Right column: Selected Chat Video Inspection
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                ) {
                    val selected = uiState.selectedChat
                    if (selected == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkSurface, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Select a channel or group on the left\nto inspect and paginate its video history.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        // Selected Chat Header & Controls Card
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = selected.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Chat ID: ${selected.id} • Type: ${selected.typeDescription}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Stored in Room: ${uiState.videos.size} videos",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = BrandTeal,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                if (uiState.isSyncingVideos) {
                                    Text(
                                        text = "Page ${uiState.syncPage} (${uiState.syncVideosFound} discovered)",
                                        style = MaterialTheme.typography.bodySmall.copy(color = BrandBlue)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = { viewModel.syncVideosForSelectedChat() },
                                    enabled = !uiState.isSyncingVideos,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal)
                                ) {
                                    if (uiState.isSyncingVideos) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = DarkBackground,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Paginating History...")
                                    } else {
                                        Text("Sync All Videos")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Video List
                        if (uiState.videos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(DarkSurface, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (uiState.isSyncingVideos) "Paginating message history...\nExamining video messages..." else "No videos found in Room for this chat.\nTap 'Sync All Videos' to paginate history.",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(uiState.videos, key = { it.messageId }) { video ->
                                    VideoItem(video = video)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatItem(
    chat: TelegramChat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) BrandBlue.copy(alpha = 0.25f) else DarkSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) BrandTeal else DarkBorder,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = if (isSelected) BrandTeal else TextSecondary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${chat.typeDescription}${if (chat.isArchived) " • Archived" else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun VideoItem(video: TelegramVideo) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, DarkBorder, RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Duration: ${video.formattedDuration}  •  Size: ${video.formattedSize}",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        fontSize = 11.sp
                    )

                    Text(
                        text = "Msg ID: ${video.messageId}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        ),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
