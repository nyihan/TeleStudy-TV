package com.telestudy.tv.features.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telestudy.tv.core.device.DeviceType
import com.telestudy.tv.core.device.LocalDeviceType
import com.telestudy.tv.data.sync.SyncState
import com.telestudy.tv.domain.model.TelegramChat
import com.telestudy.tv.domain.model.TelegramVideo
import com.telestudy.tv.domain.model.WatchProgress
import com.telestudy.tv.features.home.HomeViewModel
import com.telestudy.tv.features.home.ui.components.ContinueWatchingCard
import com.telestudy.tv.features.home.ui.components.SubjectCard
import com.telestudy.tv.features.home.ui.components.TvSearchKeyboard
import com.telestudy.tv.features.home.ui.components.UpNextCard
import com.telestudy.tv.features.home.ui.components.VideoCard
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPlayVideo: (TelegramVideo) -> Unit,
    onNavigateToDiscovery: () -> Unit = {},
    onNavigateToSpike: () -> Unit = {},
    onCheckForUpdate: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val deviceType = LocalDeviceType.current
    val isTv = deviceType == DeviceType.TV

    val subjectsWithVideos = remember(uiState.subjects) {
        uiState.subjects.filter { it.videoCount > 0 }
    }

    val activeSubject = uiState.activeLessonListSubject

    // Priority 1: Back key exits Group Lesson List view and returns to Home
    BackHandler(enabled = activeSubject != null) {
        viewModel.closeLessonList()
    }

    // Priority 2: Back key closes search when active
    BackHandler(enabled = (uiState.isSearchActive || uiState.searchQuery.isNotBlank()) && activeSubject == null) {
        viewModel.setSearchActive(false)
        viewModel.updateSearchQuery("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .padding(horizontal = if (isTv) 32.dp else 16.dp, vertical = if (isTv) 8.dp else 16.dp)
    ) {
        if (activeSubject != null) {
            // --- GROUP LESSON LIST MODE (Full Viewport Multi-row Grid) ---
            GroupLessonListContent(
                subject = activeSubject,
                lessons = uiState.lessons,
                progressMap = uiState.progressMap,
                isTv = isTv,
                lastPlayedMessageId = uiState.lastPlayedVideoByChat[activeSubject.id],
                onBack = { viewModel.closeLessonList() },
                onPlayVideo = { video ->
                    viewModel.recordLastPlayedVideo(video.chatId, video.messageId)
                    onPlayVideo(video)
                },
                onTriggerAutoSync = { viewModel.triggerAutoSync() }
            )
        } else {
            // --- 1. Top Bar: Branding, Sync Status, Actions (Debug buttons removed) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TeleStudy TV",
                        color = Color.White,
                        fontSize = if (isTv) 26.sp else 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Sync status indicator
                    when (val sync = uiState.syncState) {
                        is SyncState.Syncing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF00E5FF)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = sync.message,
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        is SyncState.Success -> {
                            Text(
                                text = "● Auto-synced with Telegram",
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        is SyncState.Error -> {
                            Text(
                                text = "Sync: ${sync.message}",
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        is SyncState.Idle -> {
                            Text(
                                text = "Ready",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // Quick Actions (Search, Sync, and Update check)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.setSearchActive(!uiState.isSearchActive) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (uiState.isSearchActive) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                        )
                    }
                    IconButton(onClick = { viewModel.triggerAutoSync() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(onClick = onCheckForUpdate) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Check for Updates",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val focusManager = LocalFocusManager.current
            val searchInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isSearchPressed by searchInteractionSource.collectIsPressedAsState()
            val isSearchFocused by searchInteractionSource.collectIsFocusedAsState()

            LaunchedEffect(isSearchPressed, isSearchFocused) {
                if (isSearchPressed || (isSearchFocused && isTv)) {
                    viewModel.setSearchActive(true)
                }
            }

            // --- 2. Search Input Field ---
            OutlinedTextField(
                value = uiState.searchQuery,
                readOnly = false,
                interactionSource = searchInteractionSource,
                onValueChange = {
                    viewModel.updateSearchQuery(it)
                    if (!uiState.isSearchActive) viewModel.setSearchActive(true)
                },
                placeholder = { Text("Search lessons, subjects, numbers (e.g. Lesson 33, Time)...", fontSize = 13.sp) },
                leadingIcon = {
                    IconButton(onClick = { viewModel.setSearchActive(!uiState.isSearchActive) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF00E5FF))
                    }
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.updateSearchQuery("")
                            viewModel.setSearchActive(false)
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTv) 46.dp else 52.dp)
                    .clickable {
                        viewModel.setSearchActive(true)
                    }
                    .onFocusChanged {
                        if (it.isFocused && isTv) {
                            viewModel.setSearchActive(true)
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            if (keyEvent.key == Key.DirectionDown) {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            } else if (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter) {
                                viewModel.setSearchActive(true)
                                true
                            } else false
                        } else false
                    },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // --- 3. Main Content: Search Mode vs Netflix Shelves ---
            if (uiState.isSearchActive || uiState.searchQuery.isNotBlank()) {
                // --- SEARCH MODE ---
                SearchContent(
                    uiState = uiState,
                    isTv = isTv,
                    onChar = { viewModel.onKeyboardChar(it) },
                    onSpace = { viewModel.onKeyboardSpace() },
                    onBackspace = { viewModel.onKeyboardBackspace() },
                    onClear = { viewModel.onKeyboardClear() },
                    onPlayVideo = onPlayVideo
                )
            } else {
                // --- NETFLIX-STYLE SHELVES ---
                ShelvesContent(
                    uiState = uiState,
                    isTv = isTv,
                    subjectsWithVideos = subjectsWithVideos,
                    onSelectSubject = { subject ->
                        viewModel.openLessonList(subject)
                    },
                    onOpenLessonList = { subject ->
                        viewModel.openLessonList(subject)
                    },
                    onPlayVideo = { video ->
                        viewModel.recordLastPlayedVideo(video.chatId, video.messageId)
                        onPlayVideo(video)
                    },
                    onTriggerAutoSync = { viewModel.triggerAutoSync() }
                )
            }
        }
    }
}

@Composable
private fun SearchContent(
    uiState: com.telestudy.tv.features.home.HomeUiState,
    isTv: Boolean,
    onChar: (Char) -> Unit,
    onSpace: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onPlayVideo: (TelegramVideo) -> Unit
) {
    if (isTv) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left: On-Screen TV Keyboard
            TvSearchKeyboard(
                onChar = onChar,
                onSpace = onSpace,
                onBackspace = onBackspace,
                onClear = onClear,
                modifier = Modifier.width(320.dp)
            )

            // Right: Instant Results Grid
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (uiState.searchQuery.isBlank()) "Type with TV remote to search lessons..."
                    else "Search Results for \"${uiState.searchQuery}\" (${uiState.searchResults.size} found)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (uiState.isSearching) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(36.dp))
                    }
                } else if (uiState.searchResults.isEmpty() && uiState.searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No lessons matching \"${uiState.searchQuery}\"", color = Color(0xFF64748B), fontSize = 14.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.searchResults,
                            key = { "${it.chatId}_${it.messageId}" }
                        ) { video ->
                            val progress = uiState.progressMap[Pair(video.chatId, video.messageId)]
                            val chatTitle = uiState.chatTitleMap[video.chatId]

                            VideoCard(
                                video = video,
                                onClick = { onPlayVideo(video) },
                                watchProgress = progress,
                                subjectTitle = chatTitle
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = if (uiState.searchQuery.isBlank()) "Type to search all lessons..."
                else "Search Results for \"${uiState.searchQuery}\" (${uiState.searchResults.size} found)",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (uiState.isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(36.dp))
                }
            } else if (uiState.searchResults.isEmpty() && uiState.searchQuery.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No lessons matching \"${uiState.searchQuery}\"", color = Color(0xFF64748B), fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(
                        items = uiState.searchResults,
                        key = { "${it.chatId}_${it.messageId}" }
                    ) { video ->
                        val progress = uiState.progressMap[Pair(video.chatId, video.messageId)]
                        val chatTitle = uiState.chatTitleMap[video.chatId]

                        VideoCard(
                            video = video,
                            onClick = { onPlayVideo(video) },
                            watchProgress = progress,
                            subjectTitle = chatTitle
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelvesContent(
    uiState: com.telestudy.tv.features.home.HomeUiState,
    isTv: Boolean,
    subjectsWithVideos: List<com.telestudy.tv.domain.model.TelegramChat>,
    onSelectSubject: (com.telestudy.tv.domain.model.TelegramChat) -> Unit,
    onOpenLessonList: (com.telestudy.tv.domain.model.TelegramChat) -> Unit = {},
    onPlayVideo: (TelegramVideo) -> Unit,
    onTriggerAutoSync: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- SHELF 1: Daily Study Continuity (မနေ့က သင်ခန်းစာ & နောက်သင်ခန်းစာ) ---
        val continuity = uiState.continuity
        val lastWatched = continuity.lastWatched
        val upNext = continuity.upNext
        val hasContinuity = lastWatched != null || upNext != null || uiState.continueWatching.isNotEmpty()

        if (hasContinuity) {
            item {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp, 18.dp)
                                .background(Color(0xFF00E5FF), RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Daily Study • နေ့စဉ်သင်ယူမှု",
                            color = Color.White,
                            fontSize = if (isTv) 20.sp else 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Card 1: မနေ့က ဖွင့်ခဲ့သော သင်ခန်းစာ (Last Watched / Continue Watching)
                        if (lastWatched != null) {
                            item(key = "last_watched_${lastWatched.video.chatId}_${lastWatched.video.messageId}") {
                                ContinueWatchingCard(
                                    item = lastWatched,
                                    onClick = { onPlayVideo(lastWatched.video) },
                                    modifier = Modifier.width(if (isTv) 320.dp else 250.dp)
                                )
                            }
                        }

                        // Card 2: ဖွင့်ခဲ့သည့် သင်ခန်းစာ၏ နောက်တစ်ခု (Up Next Lesson - Lesson N+1)
                        if (upNext != null) {
                            item(key = "up_next_${upNext.chatId}_${upNext.messageId}") {
                                UpNextCard(
                                    video = upNext,
                                    subjectTitle = continuity.upNextSubjectTitle ?: "Up Next",
                                    onClick = { onPlayVideo(upNext) },
                                    modifier = Modifier.width(if (isTv) 320.dp else 250.dp)
                                )
                            }
                        }

                        // Additional in-progress lessons (if student studies multiple subjects)
                        val otherContinueWatching = uiState.continueWatching.filter {
                            it.video.chatId != lastWatched?.video?.chatId || it.video.messageId != lastWatched?.video?.messageId
                        }
                        items(
                            items = otherContinueWatching,
                            key = { "cw_${it.video.chatId}_${it.video.messageId}" }
                        ) { item ->
                            ContinueWatchingCard(
                                item = item,
                                onClick = { onPlayVideo(item.video) },
                                modifier = Modifier.width(if (isTv) 300.dp else 240.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- SHELF 2: Study Groups & Subjects ---
        val displaySubjects = if (subjectsWithVideos.isNotEmpty()) subjectsWithVideos else uiState.subjects.take(20)
        if (displaySubjects.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Study Groups & Subjects",
                            color = Color(0xFF94A3B8),
                            fontSize = if (isTv) 16.sp else 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Select a subject to open all lessons",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = displaySubjects,
                            key = { "subject_${it.id}" }
                        ) { subject ->
                            val isSelected = uiState.selectedSubject?.id == subject.id
                            SubjectCard(
                                subject = subject,
                                isSelected = isSelected,
                                onClick = { onSelectSubject(subject) }
                            )
                        }
                    }
                }
            }
        }

        // --- SHELF 3: Selected Subject Overview (Option C - Direct Grid Action) ---
        val selected = uiState.selectedSubject
        val lessons = uiState.lessons

        if (selected != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selected.title,
                                color = Color.White,
                                fontSize = if (isTv) 18.sp else 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${lessons.size} Lessons",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ordered sequentially from Lesson 1 • Press OK to view full 3-column lesson list",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = { onOpenLessonList(selected) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text(
                            text = "View Lessons (${lessons.size}) →",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── Group Lesson List (TV Multi-row Grid with Smooth Remote Auto-Scroll) ────
@Composable
private fun GroupLessonListContent(
    subject: TelegramChat,
    lessons: List<TelegramVideo>,
    progressMap: Map<Pair<Long, Long>, WatchProgress>,
    isTv: Boolean,
    lastPlayedMessageId: Long?,
    onBack: () -> Unit,
    onPlayVideo: (TelegramVideo) -> Unit,
    onTriggerAutoSync: () -> Unit
) {
    val targetIndex = remember(lessons, lastPlayedMessageId) {
        if (lastPlayedMessageId != null) {
            val idx = lessons.indexOfFirst { it.messageId == lastPlayedMessageId }
            if (idx >= 0) idx else 0
        } else 0
    }

    val gridState = rememberLazyGridState()
    val targetLessonRequester = remember { FocusRequester() }
    val backInteractionSource = remember { MutableInteractionSource() }
    val isBackFocused by backInteractionSource.collectIsFocusedAsState()

    // Request initial focus on target lesson and restore scroll position around target lesson
    LaunchedEffect(subject.id, lessons.isNotEmpty(), targetIndex) {
        if (lessons.isNotEmpty()) {
            if (targetIndex > 0) {
                try {
                    gridState.scrollToItem(targetIndex)
                } catch (_: Exception) {}
            }
            delay(120L)
            try {
                targetLessonRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // --- Header with Back Button, Subject Title, and Badge ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                interactionSource = backInteractionSource,
                modifier = Modifier
                    .size(if (isTv) 44.dp else 38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBackFocused) Color(0xFF00E5FF) else Color(0xFF1E293B))
                    .border(
                        width = if (isBackFocused) 2.dp else 1.dp,
                        color = if (isBackFocused) Color.White else Color(0xFF334155),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .focusable(interactionSource = backInteractionSource)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = if (isBackFocused) Color.Black else Color(0xFF00E5FF)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = subject.title,
                        color = Color.White,
                        fontSize = if (isTv) 22.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF00E5FF).copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${lessons.size} Lessons",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "Navigate with TV remote D-pad • Press OK to play",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            if (lessons.isNotEmpty()) {
                Text(
                    text = "● Ordered Sequentially",
                    color = Color(0xFF10B981),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // --- Multi-Row Lesson Grid ---
        if (lessons.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading lessons for ${subject.title}...",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onTriggerAutoSync,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text("Check for Lessons", color = Color(0xFF00E5FF))
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isTv) 3 else 2),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = lessons,
                    key = { _, video -> "${video.chatId}_${video.messageId}" }
                ) { index, video ->
                    val progress = progressMap[Pair(video.chatId, video.messageId)]
                    VideoCard(
                        video = video,
                        onClick = { onPlayVideo(video) },
                        watchProgress = progress,
                        modifier = if (index == targetIndex) Modifier.focusRequester(targetLessonRequester) else Modifier
                    )
                }
            }
        }
    }
}

