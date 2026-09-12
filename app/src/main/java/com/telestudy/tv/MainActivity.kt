package com.telestudy.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telestudy.tv.core.device.DeviceType
import com.telestudy.tv.core.device.DeviceTypeProvider
import com.telestudy.tv.core.device.LocalDeviceType
import com.telestudy.tv.core.tdlib.NativeLoadResult
import com.telestudy.tv.core.tdlib.TDLibNativeLoader
import com.telestudy.tv.features.auth.AuthViewModel
import com.telestudy.tv.ui.theme.*
import timber.log.Timber

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val forceTv = intent.getBooleanExtra("force_tv", false)
        val deviceType = if (forceTv) DeviceType.TV else DeviceTypeProvider.resolveDeviceType(this)
        val tdlibLoadResult = TDLibNativeLoader.loadNativeLibrary()
        Timber.i("MainActivity created. DeviceType: %s (forceTv=%s), TDLib loaded: %s", deviceType, forceTv, tdlibLoadResult.isSuccess)

        val app = application as TeleStudyApp
        val clientManager = app.clientManager
        val authViewModel = AuthViewModel(clientManager)
        val mediaRepository = app.mediaRepository

        setContent {
            CompositionLocalProvider(LocalDeviceType provides deviceType) {
                TeleStudyTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val autoSpike = intent.getBooleanExtra("auto_spike", false)
                        val authUiState by authViewModel.uiState.collectAsState()
                        val discoveryViewModel = remember { com.telestudy.tv.features.discovery.DiscoveryViewModel(mediaRepository) }
                        val homeViewModel = remember { com.telestudy.tv.features.home.HomeViewModel(mediaRepository, app.syncManager) }

                        var selectedVideoForPlayback by remember { mutableStateOf<com.telestudy.tv.features.player.PlayerMediaItem?>(null) }
                        var playerViewModel by remember { mutableStateOf<com.telestudy.tv.features.player.PlayerViewModel?>(null) }

                        val initialScreen = when {
                            autoSpike -> "spike"
                            clientManager.authState.value is com.telestudy.tv.core.tdlib.AuthState.Ready -> "home"
                            else -> "auth"
                        }
                        var currentScreen by remember {
                            mutableStateOf(initialScreen)
                        }

                        // Auto-navigate to home when authenticated if on auth screen
                        LaunchedEffect(authUiState.authState, authUiState.currentUser) {
                            if ((authUiState.authState is com.telestudy.tv.core.tdlib.AuthState.Ready || authUiState.currentUser != null) && currentScreen == "auth") {
                                currentScreen = "home"
                            }
                        }

                        val playLesson33 = intent.getBooleanExtra("play_lesson33", false)
                        LaunchedEffect(playLesson33) {
                            if (playLesson33) {
                                val videos = app.database.videoDao().getVideosForChat(-1003686260160L)
                                val l33 = videos.firstOrNull { it.fileName.contains("Lesson 33") }
                                if (l33 != null) {
                                    Timber.i("Auto-playing Lesson 33 from intent: %s (fileId=%s)", l33.fileName, l33.fileId)
                                    val mediaItem = com.telestudy.tv.features.player.PlayerMediaItem(
                                        chatId = l33.chatId,
                                        messageId = l33.messageId,
                                        fileId = l33.fileId,
                                        fileName = l33.fileName,
                                        durationSeconds = l33.durationSeconds,
                                        fileSize = l33.fileSize,
                                        thumbnailPath = l33.thumbnailPath,
                                        width = l33.width,
                                        height = l33.height
                                    )
                                    selectedVideoForPlayback = mediaItem
                                    val playerManager = com.telestudy.tv.core.player.TeleStudyPlayerManager(
                                        this@MainActivity,
                                        clientManager.fileManager,
                                        clientProvider = { clientManager.getClient() }
                                    )
                                    playerViewModel = com.telestudy.tv.features.player.PlayerViewModel(
                                        playerManager = playerManager,
                                        playbackProgressDao = app.playbackProgressDao,
                                        mediaItem = mediaItem
                                    )
                                    currentScreen = "player"
                                }
                            }
                        }

                        when (currentScreen) {
                            "home" -> {
                                com.telestudy.tv.features.home.ui.HomeScreen(
                                    viewModel = homeViewModel,
                                    onPlayVideo = { video ->
                                        Timber.i("Selected lesson to play: ${video.fileName} (id=${video.messageId}, fileId=${video.fileId})")
                                        val mediaItem = com.telestudy.tv.features.player.PlayerMediaItem(
                                            chatId = video.chatId,
                                            messageId = video.messageId,
                                            fileId = video.fileId,
                                            fileName = video.fileName,
                                            durationSeconds = video.durationSeconds,
                                            fileSize = video.fileSize,
                                            thumbnailPath = video.thumbnailPath,
                                            width = video.width,
                                            height = video.height
                                        )
                                        selectedVideoForPlayback = mediaItem
                                        val playerManager = com.telestudy.tv.core.player.TeleStudyPlayerManager(
                                            this@MainActivity,
                                            clientManager.fileManager,
                                            clientProvider = { clientManager.getClient() }
                                        )
                                        playerViewModel = com.telestudy.tv.features.player.PlayerViewModel(
                                            playerManager = playerManager,
                                            playbackProgressDao = app.playbackProgressDao,
                                            mediaItem = mediaItem
                                        )
                                        currentScreen = "player"
                                    },
                                    onNavigateToDiscovery = { currentScreen = "discovery" },
                                    onNavigateToSpike = { currentScreen = "spike" }
                                )
                            }
                            "player" -> {
                                val vm = playerViewModel
                                if (vm != null) {
                                    com.telestudy.tv.features.player.ui.PlayerScreen(
                                        viewModel = vm,
                                        onBack = {
                                            vm.playerManager.release()
                                            playerViewModel = null
                                            selectedVideoForPlayback = null
                                            currentScreen = "home"
                                        }
                                    )
                                } else {
                                    currentScreen = "home"
                                }
                            }
                            "spike" -> {
                                com.telestudy.tv.ui.spike.StreamingSpikeScreen(
                                    autoStart = autoSpike,
                                    onBack = { currentScreen = "home" }
                                )
                            }
                            "discovery" -> {
                                com.telestudy.tv.features.discovery.ui.DiscoveryScreen(
                                    viewModel = discoveryViewModel,
                                    onBack = { currentScreen = "home" }
                                )
                            }
                            "auth" -> {
                                com.telestudy.tv.features.auth.ui.AuthScreen(
                                    viewModel = authViewModel,
                                    deviceType = deviceType,
                                    onAuthSuccess = { currentScreen = "home" }
                                )
                            }
                            else -> {
                                Phase1DashboardScreen(
                                    deviceType = deviceType,
                                    nativeLoadResult = tdlibLoadResult,
                                    apiId = BuildConfig.TELEGRAM_API_ID,
                                    apiHash = BuildConfig.TELEGRAM_API_HASH,
                                    authState = authUiState.authState,
                                    currentUser = authUiState.currentUser,
                                    onNavigateToAuth = { currentScreen = "auth" },
                                    onNavigateToDiscovery = { currentScreen = "discovery" },
                                    onLaunchSpike = { currentScreen = "spike" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Phase1DashboardScreen(
    deviceType: DeviceType,
    nativeLoadResult: NativeLoadResult,
    apiId: Int,
    apiHash: String,
    authState: com.telestudy.tv.core.tdlib.AuthState,
    currentUser: org.drinkless.tdlib.TdApi.User?,
    onNavigateToAuth: () -> Unit,
    onNavigateToDiscovery: () -> Unit,
    onLaunchSpike: () -> Unit
) {
    val isTv = deviceType == DeviceType.TV
    val horizontalPadding = if (isTv) 58.dp else 24.dp
    val verticalPadding = if (isTv) 40.dp else 24.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Header
        Text(
            text = stringResource(R.string.app_name),
            fontSize = if (isTv) 32.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Phase 2: Telegram Authentication System",
            fontSize = if (isTv) 18.sp else 14.sp,
            color = AccentGold,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Card Container
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isTv) 0.7f else 0.95f)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 2.dp,
                    color = BluePrimary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .focusable(),
            colors = CardDefaults.cardColors(
                containerColor = SurfaceDark
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Device Type Status
                StatusRow(
                    label = stringResource(R.string.device_type_label),
                    value = deviceType.name,
                    statusColor = if (isTv) AccentGold else BluePrimary
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // TDLib Native Library Status
                StatusRow(
                    label = stringResource(R.string.tdlib_status_label),
                    value = if (nativeLoadResult.isSuccess) {
                        stringResource(R.string.tdlib_loaded_success)
                    } else {
                        nativeLoadResult.errorMessage ?: stringResource(R.string.tdlib_load_failed)
                    },
                    statusColor = if (nativeLoadResult.isSuccess) SuccessGreen else ErrorRed
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // Telegram Auth Status
                val isReady = authState is com.telestudy.tv.core.tdlib.AuthState.Ready
                val authLabel = when (authState) {
                    is com.telestudy.tv.core.tdlib.AuthState.Ready -> {
                        val name = listOfNotNull(currentUser?.firstName, currentUser?.lastName).joinToString(" ")
                        if (name.isNotBlank()) "Connected: $name" else "Connected (ID: ${currentUser?.id ?: 0})"
                    }
                    is com.telestudy.tv.core.tdlib.AuthState.WaitPhoneNumber -> "Awaiting Phone Login"
                    is com.telestudy.tv.core.tdlib.AuthState.WaitCode -> "Awaiting OTP Code"
                    is com.telestudy.tv.core.tdlib.AuthState.WaitPassword -> "Awaiting 2FA Password"
                    is com.telestudy.tv.core.tdlib.AuthState.WaitOtherDeviceConfirmation -> "Awaiting QR Scan"
                    is com.telestudy.tv.core.tdlib.AuthState.LoggingOut -> "Logging Out..."
                    is com.telestudy.tv.core.tdlib.AuthState.Closed -> "Session Closed"
                    else -> "Not Authenticated"
                }
                StatusRow(
                    label = "Telegram Session",
                    value = authLabel,
                    statusColor = if (isReady) SuccessGreen else AccentGold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(if (isTv) 0.7f else 0.95f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNavigateToAuth,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (authState is com.telestudy.tv.core.tdlib.AuthState.Ready) "Account Info / Logout" else "Login to Telegram",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = onNavigateToDiscovery,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Discovery & Room",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BackgroundDark
                )
            }

            Button(
                onClick = onLaunchSpike,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Streaming Spike",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BackgroundDark
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Platform Readiness Badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlatformBadge(
                label = stringResource(R.string.phone_test_ready),
                isActive = true
            )
            PlatformBadge(
                label = stringResource(R.string.tv_test_ready),
                isActive = isTv
            )
        }
    }
}

@Composable
fun StatusRow(
    label: String,
    value: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = statusColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun PlatformBadge(
    label: String,
    isActive: Boolean
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) BluePrimary.copy(alpha = 0.2f) else SurfaceDark)
            .border(
                width = 1.dp,
                color = if (isActive) BluePrimary else Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) TextPrimary else TextSecondary
        )
    }
}
