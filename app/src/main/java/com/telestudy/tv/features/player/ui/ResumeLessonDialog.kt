package com.telestudy.tv.features.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telestudy.tv.features.player.PlayerUiState
import com.telestudy.tv.features.player.ResumeDialogState

/**
 * TV D-pad & Phone Touch dialog presenting explicit choice between
 * resuming from saved progress or starting playback from the beginning.
 */
@Composable
fun ResumeLessonDialog(
    dialogState: ResumeDialogState,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onWatchAgain: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(dialogState) {
        try {
            primaryFocusRequester.requestFocus()
        } catch (_: Exception) {
            // Ignore if layout not ready yet
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Back, Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 320.dp, max = 500.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent click-through to backdrop */ }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (dialogState) {
                    is ResumeDialogState.Prompt -> {
                        // Header Icon
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Title
                        Text(
                            text = "Resume Lesson?",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Lesson Subtitle
                        Text(
                            text = dialogState.lessonTitle,
                            color = Color(0xFF00E5FF),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Watched Text: "You watched **08:17** of **20:00**."
                        val savedTime = PlayerUiState.formatTime(dialogState.savedPositionMs)
                        val durationTime = if (dialogState.durationMs > 0) {
                            PlayerUiState.formatTime(dialogState.durationMs)
                        } else null

                        val annotatedWatched = remember(savedTime, durationTime) {
                            buildAnnotatedString {
                                append("You watched ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                                    append(savedTime)
                                }
                                if (durationTime != null) {
                                    append(" of ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                                        append(durationTime)
                                    }
                                }
                                append(".")
                            }
                        }

                        Text(
                            text = annotatedWatched,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Action Buttons: [ Resume ] [ Start Over ]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ResumeDialogButton(
                                text = "Resume",
                                icon = Icons.Default.PlayArrow,
                                isPrimary = true,
                                focusRequester = primaryFocusRequester,
                                onClick = onResume
                            )

                            ResumeDialogButton(
                                text = "Start Over",
                                icon = Icons.Default.Refresh,
                                isPrimary = false,
                                onClick = onStartOver
                            )
                        }
                    }

                    is ResumeDialogState.Completed -> {
                        // Header Icon
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF4ADE80).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Title
                        Text(
                            text = "Lesson Completed",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Lesson Subtitle
                        Text(
                            text = dialogState.lessonTitle,
                            color = Color(0xFF00E5FF),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Text: "This lesson is already completed."
                        Text(
                            text = "This lesson is already completed.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Action Button: [ Watch Again ]
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ResumeDialogButton(
                                text = "Watch Again",
                                icon = Icons.Default.Replay,
                                isPrimary = true,
                                focusRequester = primaryFocusRequester,
                                onClick = onWatchAgain
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumeDialogButton(
    text: String,
    icon: ImageVector? = null,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var buttonModifier = modifier
        .heightIn(min = 48.dp)
        .defaultMinSize(minWidth = 120.dp)
        .scale(if (isFocused) 1.06f else 1.0f)
        .clip(RoundedCornerShape(12.dp))
        .background(
            when {
                isFocused -> Color(0xFF00E5FF)
                isPrimary -> Color(0xFF0284C7)
                else -> Color.White.copy(alpha = 0.12f)
            }
        )
        .border(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) Color.White else if (isPrimary) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.25f),
            shape = RoundedCornerShape(12.dp)
        )

    if (focusRequester != null) {
        buttonModifier = buttonModifier.focusRequester(focusRequester)
    }

    buttonModifier = buttonModifier
        .focusable(interactionSource = interactionSource)
        .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown &&
                (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                onClick()
                true
            } else false
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )

    Row(
        modifier = buttonModifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = if (isFocused || isPrimary) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

