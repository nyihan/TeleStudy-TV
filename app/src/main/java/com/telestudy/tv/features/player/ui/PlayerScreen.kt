package com.telestudy.tv.features.player.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.telestudy.tv.core.device.DeviceType
import com.telestudy.tv.core.device.LocalDeviceType
import com.telestudy.tv.features.player.PlaybackStatus
import com.telestudy.tv.features.player.PlayerViewModel
import kotlinx.coroutines.delay
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val deviceType = LocalDeviceType.current
    val isTv = deviceType == DeviceType.TV
    val uiState by viewModel.uiState.collectAsState()

    BackHandler {
        onBack()
    }

    // Auto-hide controls after 4 seconds of inactivity if playing
    LaunchedEffect(uiState.isControlsVisible, uiState.isPlaying) {
        if (uiState.isControlsVisible && uiState.isPlaying) {
            delay(4000L)
            viewModel.setControlsVisible(false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                if (uiState.resumeDialogState == null) {
                    viewModel.toggleControls()
                }
            }
            .onKeyEvent { keyEvent ->
                if (uiState.resumeDialogState != null) {
                    return@onKeyEvent false
                }
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            viewModel.seekBy(-10_000L)
                            viewModel.setControlsVisible(true)
                            true
                        }
                        Key.DirectionRight -> {
                            viewModel.seekBy(10_000L)
                            viewModel.setControlsVisible(true)
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            if (!uiState.isControlsVisible) {
                                viewModel.setControlsVisible(true)
                                true
                            } else {
                                false
                            }
                        }
                        Key.Back, Key.Escape -> {
                            if (uiState.isControlsVisible) {
                                viewModel.setControlsVisible(false)
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // 1. ExoPlayer Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = viewModel.playerManager.getOrCreatePlayer()
                }
            },
            update = { playerView ->
                playerView.player = viewModel.playerManager.getOrCreatePlayer()
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Thumbnail backdrop while buffering first frame
        if (uiState.status == PlaybackStatus.BUFFERING && uiState.currentPositionMs == 0L) {
            val thumbnailPath = uiState.mediaItem?.thumbnailPath
            if (!thumbnailPath.isNullOrEmpty()) {
                val f = File(thumbnailPath)
                if (f.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(f)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Lesson Thumbnail",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // 3. Buffering Indicator
        if (uiState.isBuffering && !uiState.isError && uiState.resumeDialogState == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF00E5FF),
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        // 4. Adaptive Controls (Phone vs TV)
        if (uiState.resumeDialogState == null) {
            if (isTv) {
                TvPlayerControls(
                    uiState = uiState,
                    onBack = onBack,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSeekBy = { delta -> viewModel.seekBy(delta) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PhonePlayerControls(
                    uiState = uiState,
                    onBack = onBack,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSeekBy = { delta -> viewModel.seekBy(delta) },
                    onSeekTo = { pos -> viewModel.seekTo(pos) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 5. Resume / Start Over Decision Dialog
        if (uiState.resumeDialogState != null) {
            ResumeLessonDialog(
                dialogState = uiState.resumeDialogState!!,
                onResume = { viewModel.onResumeSelected() },
                onStartOver = { viewModel.onStartOverSelected() },
                onWatchAgain = { viewModel.onWatchAgainSelected() },
                onDismiss = onBack
            )
        }

        // 5. Error Dialog / Overlay
        if (uiState.isError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier
                        .padding(32.dp)
                        .widthIn(max = 480.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Playback Error",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.errorMessage ?: "Unknown error occurred",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = onBack,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Back to Lessons")
                            }
                            Button(
                                onClick = { viewModel.retry() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}
