package com.telestudy.tv.features.home.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.telestudy.tv.data.mapper.EntityMappers
import com.telestudy.tv.data.thumbnail.TelegramThumbnailManager
import com.telestudy.tv.domain.model.TelegramVideo
import com.telestudy.tv.domain.model.WatchProgress
import java.io.File

/**
 * Renders video thumbnail with progressive three-stage fallback:
 * 1. High-res Coil image from local storage.
 * 2. Instant blurred minithumbnail bitmap decoded from memory/Room.
 * 3. Graceful fallback cover with educational styling and lesson badge.
 * Includes duration badge and watch progress bar overlay.
 */
@Composable
fun VideoThumbnail(
    video: TelegramVideo,
    modifier: Modifier = Modifier,
    watchProgress: WatchProgress? = null
) {
    val lessonNum = remember(video.fileName, video.caption) {
        EntityMappers.extractLessonNumber(video.fileName)
            ?: EntityMappers.extractLessonNumber(video.caption)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        val minithumbnailBitmap = remember(video.minithumbnailData) {
            TelegramThumbnailManager.decodeMinithumbnail(video.minithumbnailData)
        }

        // 1. Stage 1/2: Blurred Minithumbnail or Fallback Cover
        if (minithumbnailBitmap != null) {
            Image(
                bitmap = minithumbnailBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(4.dp),
                contentScale = ContentScale.Crop
            )
        } else if (video.thumbnailPath.isNullOrBlank()) {
            // Graceful Fallback Cover
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF).copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                    if (lessonNum != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "LESSON $lessonNum",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. High-res Coil Image if downloaded
        if (!video.thumbnailPath.isNullOrBlank()) {
            val file = remember(video.thumbnailPath) { File(video.thumbnailPath) }
            AsyncImage(
                model = file,
                contentDescription = video.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // 3. Top Completed Badge (if completed)
        if (watchProgress?.isCompleted == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Watched",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Bottom duration badge
        if (video.durationSeconds > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (watchProgress != null && watchProgress.progressFraction > 0f) 8.dp else 6.dp, end = 6.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = video.formattedDuration,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 5. Watch Progress Bar (at the very bottom edge)
        if (watchProgress != null && watchProgress.progressFraction > 0f && !watchProgress.isCompleted) {
            LinearProgressIndicator(
                progress = { watchProgress.progressFraction },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color(0xFF00E5FF),
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }
    }
}
