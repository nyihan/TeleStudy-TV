package com.telestudy.tv.features.home.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telestudy.tv.domain.model.TelegramChat
import kotlinx.coroutines.launch

/**
 * Subject/Channel Card for the subject selector row with D-pad focus scaling.
 */
@Composable
fun SubjectCard(
    subject: TelegramChat,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "subjectScale"
    )

    val backgroundColor = when {
        isSelected -> Color(0xFF00E5FF).copy(alpha = 0.18f)
        isFocused -> Color(0xFF1E293B)
        else -> Color(0xFF0F172A)
    }

    val borderColor = when {
        isFocused -> Color(0xFF00E5FF)
        isSelected -> Color(0xFF00E5FF).copy(alpha = 0.7f)
        else -> Color(0xFF334155)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused || isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    coroutineScope.launch {
                        try {
                            bringIntoViewRequester.bringIntoView()
                        } catch (_: Exception) {}
                    }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = subject.title,
                color = if (isSelected || isFocused) Color(0xFF00E5FF) else Color.White,
                fontSize = 15.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subject.videoCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${subject.videoCount} lessons",
                    color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }
    }
}
