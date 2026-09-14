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
import com.telestudy.tv.features.home.ui.components.VideoCard
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPlayVideo: (TelegramVideo) -> Unit,
    onNavigateToDiscovery: () -> Unit = {},
    onNavigateToSpike: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val deviceType = LocalDeviceType.current
    val isTv = deviceType == DeviceType.TV

    val subjectsWithVideos = remember(uiState.subjects) {
        uiState.subjects.filter { it.videoCount > 0 }
    }

    var activeLessonListSubject by remember { mutableStateOf<TelegramChat?>(null) }

    // Priority 1: Back key exits Group Lesson List view and returns to Home
    BackHandler(enabled = activeLessonListSubject != null) {
        activeLessonListSubject = null
    }

    // Priority 2: Back key closes search when active
    BackHandler(enabled = (uiState.isSearchActive || uiState.searchQuery.isNotBlank()) && activeLessonListSubject == null) {
        viewModel.setSearchActive(false)
        viewModel.updateSearchQuery("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .padding(horizontal = if (isTv) 32.dp else 16.dp, vertical = if (isTv) 8.dp else 16.dp)
    ) {
        if (activeLessonListSubject != null) {
            // --- GROUP LESSON LIST MODE (Full Viewport Multi-row Grid) ---
            GroupLessonListContent(
                subject = activeLessonListSubject!!,
                lessons = uiState.lessons,
                progressMap = uiState.progressMap,
                isTv = isTv,
                onBack = { activeLessonListSubject = null },
                onPlayVideo = onPlayVideo,
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

                // Quick Actions (Cleaned: Search & Sync only, no debug buttons)
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
                        viewModel.selectSubject(subject)
                        activeLessonListSubject = subject
                    },
                    onOpenLessonList = { subject ->
                        viewModel.selectSubject(subject)
                        activeLessonListSubject = subject
                    },
                    onPlayVideo = onPlayVideo,
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
        // --- SHELF 1: Continue Watching (Only shown if in-progress videos exist) ---
        if (uiState.continueWatching.isNotEmpty()) {
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
                            text = "Continue Watching",
                            color = Color.White,
                            fontSize = if (isTv) 20.sp else 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = uiState.continueWatching,
                            key = { "cw_${it.video.chatId}_${it.video.messageId}" }
                        ) { item ->
                            ContinueWatchingCard(
                                item = item,
                                onClick = { onPlayVideo(item.video) },
                                modifier = Modifier.width(if (isTv) 280.dp else 220.dp)
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
                    Text(
                        text = "Study Groups & Subjects",
                        color = Color(0xFF94A3B8),
                        fontSize = if (isTv) 16.sp else 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

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

        // --- SHELF 3: Current Selected Subject Lessons ---
        val selected = uiState.selectedSubject
        val lessons = uiState.lessons

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
                        text = if (selected != null) "${selected.title} (${lessons.size} Lessons)" else "Video Lessons",
                        color = Color.White,
                        fontSize = if (isTv) 19.sp else 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (selected != null && lessons.isNotEmpty()) {
                        TextButton(onClick = { onOpenLessonList(selected) }) {
                            Text(
                                text = "View All (${lessons.size}) →",
                                color = Color(0xFF00E5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (lessons.isNotEmpty()) {
                        Text(
                            text = "Ordered Sequentially",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (lessons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No video lessons in this subject yet.",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onTriggerAutoSync,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Text("Check for Lessons", color = Color(0xFF00E5FF))
                            }
                        }
                    }
                } else {
                    // Render horizontal shelf on TV or adaptive row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = lessons,
                            key = { "lesson_${it.chatId}_${it.messageId}" }
                        ) { video ->
                            val progress = uiState.progressMap[Pair(video.chatId, video.messageId)]
                            VideoCard(
                                video = video,
                                onClick = { onPlayVideo(video) },
                                watchProgress = progress,
                                modifier = Modifier.width(if (isTv) 260.dp else 210.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- SHELF 4: Recently Added Lessons (Latest across all channels) ---
        if (uiState.recentLessons.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)) {
                    Text(
                        text = "Recently Added Lessons",
                        color = Color.White,
                        fontSize = if (isTv) 19.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = uiState.recentLessons,
                            key = { "recent_${it.chatId}_${it.messageId}" }
                        ) { video ->
                            val progress = uiState.progressMap[Pair(video.chatId, video.messageId)]
                            val chatTitle = uiState.chatTitleMap[video.chatId]

                            VideoCard(
                                video = video,
                                onClick = { onPlayVideo(video) },
                                watchProgress = progress,
                                subjectTitle = chatTitle,
                                modifier = Modifier.width(if (isTv) 260.dp else 210.dp)
                            )
                        }
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
    onBack: () -> Unit,
    onPlayVideo: (TelegramVideo) -> Unit,
    onTriggerAutoSync: () -> Unit
) {
    val gridState = rememberLazyGridState()
    val firstLessonRequester = remember { FocusRequester() }
    val backInteractionSource = remember { MutableInteractionSource() }
    val isBackFocused by backInteractionSource.collectIsFocusedAsState()

    // Request initial focus on Lesson 1 when lessons are available
    LaunchedEffect(subject.id, lessons.size) {
        if (lessons.isNotEmpty()) {
            delay(120L)
            try {
                firstLessonRequester.requestFocus()
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
                        modifier = if (index == 0) Modifier.focusRequester(firstLessonRequester) else Modifier
                    )
                }
            }
        }
    }
}

