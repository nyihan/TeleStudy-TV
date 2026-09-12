package com.telestudy.tv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- TeleStudy Design Tokens ---
val BrandCyan = Color(0xFF00E5FF)       // Signature Focus & Active Cyan
val AccentGold = Color(0xFFF59E0B)      // Secondary Educational Accent Gold
val BluePrimary = Color(0xFF2563EB)     // Primary Action Blue
val BlueDark = Color(0xFF1D4ED8)

val BackgroundDark = Color(0xFF0A101D)  // Deepest Navy Screen Background
val SurfaceDark = Color(0xFF0F172A)     // Card & Shelf Surface
val ElevatedSurface = Color(0xFF1E293B) // Dialog, Focused Card & Popover Surface
val BorderSubtle = Color(0xFF334155)    // Default Unfocused Card Border

val TextPrimary = Color(0xFFF8FAFC)    // High contrast white for 10-ft TV reading
val TextSecondary = Color(0xFF94A3B8)  // Secondary metadata
val TextDisabled = Color(0xFF64748B)   // Dimmed / placeholder text

val TvFocusCyan = Color(0xFF00E5FF)    // TV Remote D-pad Focus Halo
val ProgressCyan = Color(0xFF00E5FF)   // Video & Watch Progress Bar
val CompletedGreen = Color(0xFF10B981) // Watched / Success Indicator
val ErrorRed = Color(0xFFEF4444)       // Error Indicator

// Compatibility Aliases for Phase 1-5 Code
val BrandBlue = Color(0xFF2AABEE)
val BrandTeal = Color(0xFF2DD4BF)
val DarkSurface = SurfaceDark
val DarkBackground = BackgroundDark
val DarkBorder = BorderSubtle
val SuccessGreen = CompletedGreen

private val DarkColorScheme = darkColorScheme(
    primary = BrandCyan,
    onPrimary = Color(0xFF0A101D),
    secondary = AccentGold,
    onSecondary = Color(0xFF0A101D),
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = ElevatedSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun TeleStudyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
