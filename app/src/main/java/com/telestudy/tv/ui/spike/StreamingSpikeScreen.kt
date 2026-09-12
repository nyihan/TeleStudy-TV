package com.telestudy.tv.ui.spike

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.telestudy.tv.BuildConfig
import com.telestudy.tv.core.device.DeviceType
import com.telestudy.tv.core.device.LocalDeviceType
import com.telestudy.tv.core.player.TelegramDataSourceFactory
import com.telestudy.tv.core.tdlib.RealTdlibFileManager
import com.telestudy.tv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import java.text.DecimalFormat

@OptIn(UnstableApi::class)
@Composable
fun StreamingSpikeScreen(
    autoStart: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val deviceType = LocalDeviceType.current
    val isTv = deviceType == DeviceType.TV
    val scope = rememberCoroutineScope()

    var statusLog by remember { mutableStateOf("Initializing Phase 1.5 Spike...") }
    var authState by remember { mutableStateOf("Idle") }
    var targetFileId by remember { mutableIntStateOf(-1) }
    var totalFileSize by remember { mutableLongStateOf(0L) }
    var downloadedSize by remember { mutableLongStateOf(0L) }
    var currentOffset by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var playbackDurationMs by remember { mutableLongStateOf(0L) }
    var playbackStartedAtDownloadedBytes by remember { mutableStateOf<Long?>(null) }
    var feasibilityPassed by remember { mutableStateOf<Boolean?>(null) }
    var seekEvents by remember { mutableStateOf<List<String>>(emptyList()) }

    var tdlibClient by remember { mutableStateOf<Client?>(null) }
    var fileManager by remember { mutableStateOf<RealTdlibFileManager?>(null) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    // Initialize TDLib & Spike Controller
    DisposableEffect(Unit) {
        val manager = RealTdlibFileManager { tdlibClient }
        fileManager = manager

        val client = Client.create(
            { update ->
                when (update) {
                    is TdApi.UpdateAuthorizationState -> {
                        when (update.authorizationState) {
                            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                                authState = "Sending TDLib parameters..."
                                statusLog = "Configuring TDLib directory and API..."
                                val params = TdApi.SetTdlibParameters().apply {
                                    useTestDc = false
                                    databaseDirectory = File(context.filesDir, "tdlib_spike").absolutePath
                                    filesDirectory = File(context.filesDir, "tdlib_spike/files").absolutePath
                                    databaseEncryptionKey = ByteArray(0)
                                    useFileDatabase = true
                                    useChatInfoDatabase = true
                                    useMessageDatabase = true
                                    useSecretChats = false
                                    apiId = BuildConfig.TELEGRAM_API_ID
                                    apiHash = BuildConfig.TELEGRAM_API_HASH
                                    systemLanguageCode = "en"
                                    deviceModel = "Android Phone/TV"
                                    systemVersion = "Android 14"
                                    applicationVersion = "1.0.0"
                                }
                                tdlibClient?.send(params, null, null)
                            }
                            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                                authState = "Authenticating with Bot Token..."
                                statusLog = "Authenticating via educational bot token..."
                                // Authenticate seamlessly with bot token for educational groups
                                val botToken = "8364825649:AAGKifPlcXPYkcmVxE5neJ-9ogEj2JxGMdY"
                                tdlibClient?.send(TdApi.CheckAuthenticationBotToken(botToken), null, null)
                            }
                            is TdApi.AuthorizationStateReady -> {
                                authState = "Authorized"
                                statusLog = "TDLib Authorized! Querying educational channel video..."
                                Timber.i("[TeleStudySpike] TDLib Authorized with Bot Token! Loading chat -1003686260160...")
                                scope.launch(Dispatchers.IO) {
                                    tdlibClient?.send(
                                        TdApi.GetChat(-1003686260160L),
                                        { chatObj ->
                                            Timber.i("[TeleStudySpike] GetChat completed: %s", chatObj.javaClass.simpleName)
                                            val tdlibMsgId9 = 9L shl 20
                                            val tdlibMsgId4 = 4L shl 20
                                            Timber.i("[TeleStudySpike] Querying message with TDLib ID %d (server 9) and %d (server 4)...", tdlibMsgId9, tdlibMsgId4)

                                            val onMessageResult: (TdApi.Object) -> Unit = { msgObj ->
                                                var fileFound: TdApi.File? = null
                                                var fileName = "Lesson 33_ Time ( Page131-134).mp4"
                                                if (msgObj is TdApi.Message) {
                                                    val content = msgObj.content
                                                    if (content is TdApi.MessageDocument) {
                                                        fileFound = content.document.document
                                                        fileName = content.document.fileName.ifEmpty { fileName }
                                                    } else if (content is TdApi.MessageVideo) {
                                                        fileFound = content.video.video
                                                        fileName = content.video.fileName.ifEmpty { fileName }
                                                    }
                                                } else if (msgObj is TdApi.Error) {
                                                    Timber.w("[TeleStudySpike] GetMessage returned Error: code=%d, message='%s'", msgObj.code, msgObj.message)
                                                }

                                                if (fileFound != null) {
                                                    targetFileId = fileFound.id
                                                    totalFileSize = fileFound.size
                                                    downloadedSize = fileFound.local.downloadedSize
                                                    currentOffset = fileFound.local.downloadOffset
                                                    Timber.i(
                                                        "[TeleStudySpike] REAL_FILE_DISCOVERED: title='%s', fileId=%d, totalSize=%d bytes, downloadedSize=%d bytes, isCompleted=%s",
                                                        fileName, fileFound.id, fileFound.size,
                                                        fileFound.local.downloadedSize, fileFound.local.isDownloadingCompleted
                                                    )
                                                    statusLog = "Discovered: $fileName ($totalFileSize bytes)"
                                                } else {
                                                    statusLog = "Message query result: ${msgObj.javaClass.simpleName}"
                                                }
                                            }

                                            tdlibClient?.send(
                                                TdApi.GetMessage(-1003686260160L, tdlibMsgId9),
                                                { msgObj ->
                                                    if (msgObj is TdApi.Message) {
                                                        onMessageResult(msgObj)
                                                    } else {
                                                        // Try message 4 as fallback
                                                        tdlibClient?.send(
                                                            TdApi.GetMessage(-1003686260160L, tdlibMsgId4),
                                                            onMessageResult,
                                                            { ex -> Timber.e(ex, "[TeleStudySpike] Error querying message 4") }
                                                        )
                                                    }
                                                },
                                                { ex ->
                                                    Timber.e(ex, "[TeleStudySpike] Error querying message 9")
                                                }
                                            )
                                        },
                                        { ex ->
                                            Timber.e(ex, "[TeleStudySpike] Error loading chat")
                                            statusLog = "Error loading chat: ${ex.message}"
                                        }
                                    )
                                }
                            }
                            else -> {
                                authState = update.authorizationState.javaClass.simpleName
                            }
                        }
                    }
                    is TdApi.UpdateFile -> {
                        manager.onUpdateFile(update)
                        if (update.file.id == targetFileId) {
                            downloadedSize = update.file.local.downloadedSize
                            currentOffset = update.file.local.downloadOffset
                        }
                    }
                }
            },
            { ex -> Timber.w(ex, "TDLib update exception in spike") },
            { ex -> Timber.e(ex, "TDLib fatal exception in spike") }
        )
        tdlibClient = client

        onDispose {
            exoPlayer?.release()
            exoPlayer = null
            tdlibClient?.send(TdApi.Close(), null, null)
            tdlibClient = null
        }
    }

    // Coroutine polling player position
    LaunchedEffect(exoPlayer, isPlaying) {
        while (exoPlayer != null) {
            val player = exoPlayer ?: break
            playbackPositionMs = player.currentPosition
            playbackDurationMs = player.duration.coerceAtLeast(0L)
            isPlaying = player.isPlaying

            // Feasibility check: when playback starts
            if (player.isPlaying && playbackStartedAtDownloadedBytes == null && totalFileSize > 0L) {
                val dlSize = downloadedSize
                playbackStartedAtDownloadedBytes = dlSize
                val passed = dlSize < totalFileSize
                feasibilityPassed = passed
                statusLog = if (passed) {
                    "FEASIBILITY PASS: Playback began at ${decimalFormat.format(dlSize / (1024.0 * 1024.0))} MB downloaded out of ${decimalFormat.format(totalFileSize / (1024.0 * 1024.0))} MB!"
                } else {
                    "Playback started after full download"
                }
            }
            delay(250)
        }
    }

    var hasAutoSeeked by remember { mutableStateOf(false) }

    val startPlayback: () -> Unit = {
        val mgr = fileManager
        if (mgr != null && exoPlayer == null) {
            statusLog = "Preparing ExoPlayer with TelegramDataSource..."
            val effectiveFileId = if (targetFileId != -1) targetFileId else 1001
            val effectiveSize = if (totalFileSize > 0L) totalFileSize else 17393941L
            targetFileId = effectiveFileId
            totalFileSize = effectiveSize

            Timber.i("[TeleStudySpike] STARTING_STREAMING_PIPELINE: fileId=%d, totalSize=%d bytes", effectiveFileId, effectiveSize)

            val player = ExoPlayer.Builder(context).build().apply {
                val uri = Uri.parse("tg://file/$effectiveFileId?size=$effectiveSize")
                val mediaSource = ProgressiveMediaSource.Factory(
                    TelegramDataSourceFactory(mgr)
                ).createMediaSource(MediaItem.fromUri(uri))

                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        val dl = downloadedSize
                        val total = totalFileSize
                        val pass = dl < total && total > 0
                        Timber.i(
                            "[TeleStudySpike] ON_RENDERED_FIRST_FRAME: positionMs=%d, downloadedBytes=%d / %d (%.2f%%), PROGRESSIVE_GATE=%s",
                            currentPosition, dl, total,
                            if (total > 0) (dl.toDouble() / total * 100) else 0.0,
                            if (pass) "PASS" else "FAIL"
                        )
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        val stateName = when (state) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "STATE_$state"
                        }
                        Timber.i(
                            "[TeleStudySpike] EXOPLAYER_STATE_CHANGED: %s, isPlaying=%s, positionMs=%d, downloadedSize=%d",
                            stateName, isPlaying, currentPosition, downloadedSize
                        )
                    }

                    override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                        Timber.i(
                            "[TeleStudySpike] EXOPLAYER_IS_PLAYING: %s, positionMs=%d, downloadedSize=%d / %d",
                            isPlayingNow, currentPosition, downloadedSize, totalFileSize
                        )
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Timber.e(
                            error,
                            "[TeleStudySpike] PLAYER_ERROR: errorCode=%d, message='%s'",
                            error.errorCode, error.message
                        )
                    }
                })

                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
            exoPlayer = player
            statusLog = "ExoPlayer streaming live! Awaiting initial audio/video frames..."
        }
    }

    LaunchedEffect(targetFileId, autoStart) {
        if (autoStart && targetFileId != -1 && exoPlayer == null) {
            delay(800)
            startPlayback()
        }
    }

    LaunchedEffect(isPlaying) {
        if (autoStart && isPlaying && !hasAutoSeeked) {
            delay(4000)
            val player = exoPlayer
            if (player != null && player.duration > 0) {
                hasAutoSeeked = true
                val targetMs = player.duration / 2
                Timber.i("[TeleStudySpike] AUTO_SEEK: Triggering seek to 50%% (%d ms) to test unbuffered byte-range download", targetMs)
                player.seekTo(targetMs)
                seekEvents = seekEvents + "Auto-seeked to 50% (${targetMs / 1000}s)"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(if (isTv) 32.dp else 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Phase 1.5: Progressive Streaming Spike",
                fontSize = if (isTv) 24.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = AccentGold
            )
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
            ) {
                Text("Back to Dashboard", color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Video View Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTv) 360.dp else 220.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, BluePrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (exoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "ExoPlayer Idle",
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Click 'Start Progressive Playback' below",
                            color = AccentGold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Feasibility Result Banner
        if (feasibilityPassed != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (feasibilityPassed == true) SuccessGreen.copy(alpha = 0.2f) else ErrorRed.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (feasibilityPassed == true) "FEASIBILITY GATE: PASS" else "FEASIBILITY GATE: FAILED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (feasibilityPassed == true) SuccessGreen else ErrorRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Playback began when only ${decimalFormat.format((playbackStartedAtDownloadedBytes ?: 0L) / (1024.0 * 1024.0))} MB was downloaded out of ${decimalFormat.format(totalFileSize / (1024.0 * 1024.0))} MB total!",
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "ExoPlayer progressively parsed and played frames without waiting for full download.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Metrics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Streaming Real-Time Metrics",
                    fontWeight = FontWeight.Bold,
                    color = BluePrimary,
                    fontSize = 16.sp
                )

                MetricRow("TDLib Auth State", authState)
                MetricRow("Target File ID", if (targetFileId != -1) targetFileId.toString() else "Querying...")
                MetricRow(
                    "Total File Size",
                    if (totalFileSize > 0L) "${decimalFormat.format(totalFileSize / (1024.0 * 1024.0))} MB ($totalFileSize bytes)" else "Unknown"
                )
                MetricRow(
                    "Downloaded on Disk",
                    "${decimalFormat.format(downloadedSize / (1024.0 * 1024.0))} MB " +
                    "(${if (totalFileSize > 0) (downloadedSize * 100 / totalFileSize) else 0}%)"
                )
                MetricRow("Active Download Offset", "$currentOffset bytes")
                MetricRow(
                    "Playback State",
                    "${if (isPlaying) "Playing" else "Paused/Buffering"} (${playbackPositionMs / 1000}s / ${playbackDurationMs / 1000}s)"
                )
                MetricRow("Status", statusLog)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = startPlayback,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
            ) {
                Text("Start Progressive Playback", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    exoPlayer?.release()
                    exoPlayer = null
                    statusLog = "Player stopped and released. In-flight downloads cancelled."
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.8f))
            ) {
                Text("Stop & Close", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Seek Verification Controls
        Text(
            text = "Seek Feasibility Tests (Trigger unbuffered byte-range downloads):",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SeekButton(
                label = "Seek 25%",
                enabled = exoPlayer != null && playbackDurationMs > 0,
                onClick = {
                    val targetMs = (playbackDurationMs * 0.25).toLong()
                    exoPlayer?.seekTo(targetMs)
                    seekEvents = seekEvents + "Seeked to 25% (${targetMs / 1000}s)"
                    statusLog = "Seeked to 25% -> Triggered byte-range request at new offset"
                }
            )
            SeekButton(
                label = "Seek 50%",
                enabled = exoPlayer != null && playbackDurationMs > 0,
                onClick = {
                    val targetMs = (playbackDurationMs * 0.50).toLong()
                    exoPlayer?.seekTo(targetMs)
                    seekEvents = seekEvents + "Seeked to 50% (${targetMs / 1000}s)"
                    statusLog = "Seeked to 50% -> Intermediate bytes skipped, new chunk requested"
                }
            )
            SeekButton(
                label = "Seek 75%",
                enabled = exoPlayer != null && playbackDurationMs > 0,
                onClick = {
                    val targetMs = (playbackDurationMs * 0.75).toLong()
                    exoPlayer?.seekTo(targetMs)
                    seekEvents = seekEvents + "Seeked to 75% (${targetMs / 1000}s)"
                    statusLog = "Seeked to 75% -> Verifying progressive playback at end of file"
                }
            )
        }

        if (seekEvents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                seekEvents.takeLast(3).forEach { evt ->
                    Text(text = "Ã¢â‚¬Â¢ $evt", color = AccentGold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(text = value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun RowScope.SeekButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = AccentGold,
            disabledContentColor = Color.Gray
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled)
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
