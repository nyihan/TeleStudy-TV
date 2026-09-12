package com.telestudy.tv.features.home.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telestudy.tv.data.mapper.EntityMappers
import com.telestudy.tv.domain.model.TelegramVideo
import com.telestudy.tv.domain.model.WatchProgress

/**
 * Adaptive Video Card supporting TV D-pad focus scaling, rich educational metadata badges,
 * progress indicator, and touch interactions.
 */
@Composable
fun VideoCard(
    video: TelegramVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    watchProgress: WatchProgress? = null,
    subjectTitle: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "cardScale")

    val topicTitle = remember(video.fileName, video.caption) {
        EntityMappers.extractTopicTitle(video.fileName, video.caption)
    }

    val lessonNumber = remember(video.fileName, video.caption) {
        EntityMappers.extractLessonNumber(video.fileName)
            ?: EntityMappers.extractLessonNumber(video.caption)
    }

    val unitInfo = remember(video.fileName, video.caption) {
        EntityMappers.extractUnitInfo(video.fileName)
            ?: EntityMappers.extractUnitInfo(video.caption)
    }

    val rawPageInfo = remember(video.fileName, video.caption) {
        EntityMappers.extractPageInfo(video.fileName)
            ?: EntityMappers.extractPageInfo(video.caption)
    }

    val pageInfo = remember(rawPageInfo) {
        rawPageInfo?.let {
            if (it.contains("-")) {
                it.replace("Page ", "Pages ").replace("-", "–")
            } else {
                it
            }
        }
    }

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF1E293B) else Color(0xFF0F172A))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color(0xFF00E5FF) else Color(0xFF334155),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
    ) {
        // Thumbnail with 16:9 aspect ratio and progress overlay
        VideoThumbnail(
            video = video,
            watchProgress = watchProgress,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subject Title (if provided, e.g. in Search Results)
        if (!subjectTitle.isNullOrBlank()) {
            Text(
                text = subjectTitle,
                color = Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Title and Lesson Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (lessonNumber != null) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF00E5FF).copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Lesson $lessonNumber",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = topicTitle,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Educational Metadata: Unit, Page, Duration, Size / Remaining
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (unitInfo != null) {
                    Text(
                        text = unitInfo,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    Text(text = "•", color = Color(0xFF64748B), fontSize = 10.sp)
                }
                if (pageInfo != null) {
                    Text(
                        text = pageInfo,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    if (video.durationSeconds > 0) {
                        Text(text = "•", color = Color(0xFF64748B), fontSize = 10.sp)
                    }
                }
                if (video.durationSeconds > 0) {
                    Text(
                        text = video.formattedDuration,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            if (watchProgress != null && watchProgress.progressFraction > 0f && !watchProgress.isCompleted) {
                Text(
                    text = watchProgress.remainingDurationFormatted,
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = video.formattedSize,
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }
        }
    }
}
