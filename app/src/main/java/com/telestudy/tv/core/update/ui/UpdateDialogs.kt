package com.telestudy.tv.core.update.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telestudy.tv.core.update.AppUpdateManager
import com.telestudy.tv.core.update.model.AppUpdateInfo
import com.telestudy.tv.core.update.model.UpdateState
import com.telestudy.tv.ui.theme.*
import java.io.File
import java.util.Locale

/**
 * Top-level host for TV update dialogs.
 * Enforces player safety: strictly suppressed when a video is playing.
 */
@Composable
fun UpdateHostDialog(
    updateManager: AppUpdateManager,
    isPlayerActive: Boolean
) {
    if (isPlayerActive) {
        // Player safety: Never interrupt active video playback with update dialogs
        return
    }

    val state by updateManager.updateState.collectAsState()
    val context = LocalContext.current

    when (val currentState = state) {
        is UpdateState.Available -> {
            UpdatePromptDialog(
                updateInfo = currentState.updateInfo,
                onConfirmUpdate = { updateManager.startDownload(currentState.updateInfo) },
                onDismiss = { updateManager.dismiss() }
            )
        }
        is UpdateState.Downloading -> {
            UpdateDownloadDialog(
                progress = currentState.progress,
                downloadedBytes = currentState.downloadedBytes,
                totalBytes = currentState.totalBytes,
                updateInfo = currentState.updateInfo,
                onCancel = { updateManager.cancelDownload() }
            )
        }
        is UpdateState.Verifying -> {
            UpdateVerifyingDialog(
                statusMessage = currentState.statusMessage
            )
        }
        is UpdateState.ReadyToInstall -> {
            UpdateReadyDialog(
                apkFile = currentState.apkFile,
                updateInfo = currentState.updateInfo,
                onInstall = { updateManager.installUpdate(currentState.apkFile, currentState.updateInfo) },
                onDismiss = { updateManager.dismiss() }
            )
        }
        is UpdateState.Error -> {
            UpdateErrorDialog(
                message = currentState.message,
                canRetry = currentState.canRetry,
                onRetry = { updateManager.checkForUpdates(isManual = true) },
                onDismiss = { updateManager.dismiss() }
            )
        }
        else -> {
            // Idle, Checking, UpToDate, Cancelled -> No modal displayed
        }
    }
}

/**
 * 10-Foot TV Remote Dialog prompting the user to update to the latest release.
 */
@Composable
fun UpdatePromptDialog(
    updateInfo: AppUpdateInfo,
    onConfirmUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val updateButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            updateButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    BackHandler {
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(620.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, BrandCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                color = ElevatedSurface,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Title Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Software Update Available",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BrandCyan.copy(alpha = 0.15f))
                                .border(1.dp, BrandCyan, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "v${updateInfo.latestVersionName}",
                                color = BrandCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "ဆော့ဖ်ဝဲလ် အသစ် ရရှိနေပါပြီ • Size: ${formatBytes(updateInfo.apkSize)}",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Changelog Section
                    if (updateInfo.releaseNotes.isNotBlank()) {
                        Text(
                            text = "What's New:",
                            color = AccentGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = updateInfo.releaseNotes,
                                color = TextPrimary.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // TV Action Buttons (D-pad navigable)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TvActionButton(
                            text = "Update Now (အခုတင်မည်)",
                            isPrimary = true,
                            focusRequester = updateButtonFocusRequester,
                            modifier = Modifier.weight(1.3f),
                            onClick = onConfirmUpdate
                        )

                        TvActionButton(
                            text = "Later (နောက်မှ)",
                            isPrimary = false,
                            modifier = Modifier.weight(0.9f),
                            onClick = onDismiss
                        )
                    }
                }
            }
        }
    }
}

/**
 * 10-Foot TV Remote Dialog showing live download progress with Cyan bar and cancel option.
 */
@Composable
fun UpdateDownloadDialog(
    progress: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    updateInfo: AppUpdateInfo,
    onCancel: () -> Unit
) {
    BackHandler {
        onCancel()
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(560.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, BrandCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                color = ElevatedSurface,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Downloading Update",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "TeleStudy TV v${updateInfo.latestVersionName}",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = BrandCyan,
                        trackColor = SurfaceDark
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Details: Downloaded / Total (Percent%)
                    val percent = (progress * 100).toInt().coerceIn(0, 100)
                    val sizeText = if (totalBytes > 0) {
                        "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)} ($percent%)"
                    } else {
                        formatBytes(downloadedBytes)
                    }

                    Text(
                        text = sizeText,
                        color = BrandCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    TvActionButton(
                        text = "Cancel Download",
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth(0.5f),
                        onClick = onCancel
                    )
                }
            }
        }
    }
}

/**
 * 10-Foot TV Dialog displaying SHA-256 and certificate verification state.
 */
@Composable
fun UpdateVerifyingDialog(
    statusMessage: String
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(480.dp)
                    .clip(RoundedCornerShape(16.dp)),
                color = ElevatedSurface,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = BrandCyan,
                        strokeWidth = 3.5.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = statusMessage,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 10-Foot TV Dialog indicating the verified update is ready to install.
 */
@Composable
fun UpdateReadyDialog(
    apkFile: File,
    updateInfo: AppUpdateInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val installButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            installButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    BackHandler {
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(560.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, CompletedGreen.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                color = ElevatedSurface,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(CompletedGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Verified & Ready to Install",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "TeleStudy TV v${updateInfo.latestVersionName} has been cryptographically verified against the official production signing key.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Your Telegram login and lesson progress will be preserved.",
                        color = CompletedGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TvActionButton(
                            text = "Install Now (အခုသွင်းမည်)",
                            isPrimary = true,
                            focusRequester = installButtonFocusRequester,
                            modifier = Modifier.weight(1.2f),
                            onClick = onInstall
                        )
                        TvActionButton(
                            text = "Later (နောက်မှ)",
                            isPrimary = false,
                            modifier = Modifier.weight(0.8f),
                            onClick = onDismiss
                        )
                    }
                }
            }
        }
    }
}

/**
 * 10-Foot TV Dialog displaying an update error with retry option.
 */
@Composable
fun UpdateErrorDialog(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val retryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            retryFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    BackHandler {
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(520.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, ErrorRed.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                color = ElevatedSurface,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Update Issue",
                        color = ErrorRed,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = message,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (canRetry) {
                            TvActionButton(
                                text = "Retry",
                                isPrimary = true,
                                focusRequester = retryFocusRequester,
                                modifier = Modifier.weight(1f),
                                onClick = onRetry
                            )
                        }
                        TvActionButton(
                            text = "Close",
                            isPrimary = !canRetry,
                            focusRequester = if (!canRetry) retryFocusRequester else null,
                            modifier = Modifier.weight(1f),
                            onClick = onDismiss
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable 10-Foot TV Action Button with Cyan focus scaling and border highlight.
 */
@Composable
fun TvActionButton(
    text: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "buttonScale"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> BrandCyan
            isPrimary -> BluePrimary
            else -> BorderSubtle
        },
        animationSpec = tween(durationMillis = 150),
        label = "buttonBorder"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused && isPrimary -> BrandCyan
            isFocused && !isPrimary -> SurfaceDark
            isPrimary -> BluePrimary
            else -> SurfaceDark.copy(alpha = 0.6f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "buttonBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused && isPrimary -> Color(0xFF0A101D) // Dark text on bright cyan focus
            isPrimary -> Color.White
            isFocused -> BrandCyan
            else -> TextSecondary
        },
        animationSpec = tween(durationMillis = 150),
        label = "buttonText"
    )

    var buttonModifier = modifier
        .scale(scale)
        .height(48.dp)
        .clip(RoundedCornerShape(10.dp))
        .border(width = if (isFocused) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
        .background(backgroundColor)
        .focusable(interactionSource = interactionSource)

    if (focusRequester != null) {
        buttonModifier = buttonModifier.focusRequester(focusRequester)
    }

    Button(
        onClick = onClick,
        modifier = buttonModifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor
        ),
        elevation = null,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
