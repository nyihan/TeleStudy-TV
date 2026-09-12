package com.telestudy.tv.features.home.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 10-foot TV Remote-First On-Screen Search Keyboard.
 * Fully operable using D-pad Up/Down/Left/Right and OK/Center click.
 */
@Composable
fun TvSearchKeyboard(
    onChar: (Char) -> Unit,
    onSpace: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf('A', 'B', 'C', 'D', 'E', 'F'),
        listOf('G', 'H', 'I', 'J', 'K', 'L'),
        listOf('M', 'N', 'O', 'P', 'Q', 'R'),
        listOf('S', 'T', 'U', 'V', 'W', 'X'),
        listOf('Y', 'Z', '0', '1', '2', '3'),
        listOf('4', '5', '6', '7', '8', '9')
    )

    val firstKeyRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            firstKeyRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.95f))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Alphanumeric rows
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { char ->
                    TvKeyButton(
                        text = char.toString(),
                        onClick = { onChar(char) },
                        modifier = if (char == 'A') Modifier.focusRequester(firstKeyRequester) else Modifier
                    )
                }
            }
        }

        // Action Keys Row: SPACE, DEL, CLEAR
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Space Key
            TvActionKeyButton(
                label = "SPACE",
                icon = {
                    Icon(
                        imageVector = Icons.Default.SpaceBar,
                        contentDescription = "Space",
                        tint = it,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = onSpace,
                modifier = Modifier.width(100.dp)
            )

            // Backspace Key
            TvActionKeyButton(
                label = "DEL",
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = it,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = onBackspace,
                modifier = Modifier.width(84.dp)
            )

            // Clear Key
            TvActionKeyButton(
                label = "CLEAR",
                icon = {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = it,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = onClear,
                modifier = Modifier.width(84.dp)
            )
        }
    }
}

@Composable
private fun TvKeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.15f else 1.0f, label = "keyScale")

    Box(
        modifier = modifier
            .size(width = 42.dp, height = 36.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF00E5FF) else Color(0xFF1E293B))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF334155),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TvActionKeyButton(
    label: String,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.12f else 1.0f, label = "actionKeyScale")

    Box(
        modifier = modifier
            .height(36.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF00E5FF) else Color(0xFF1E293B))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF334155),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon(if (isFocused) Color.Black else Color(0xFF94A3B8))
            Text(
                text = label,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
