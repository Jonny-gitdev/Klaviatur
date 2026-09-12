package de.klaviatur.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Palette ───────────────────────────────────────────────────────────────────

val ForestGreen      = Color(0xFF2A5C45)
val ForestGreenLight = Color(0xFF3A7A5D)
val ForestGreenBg    = Color(0xFFD6EFE2)
val ForestGreenText  = Color(0xFF1A4D32)

val Amber       = Color(0xFFC8A84B)
val AmberBg     = Color(0xFFFFF0D6)
val AmberText   = Color(0xFF8A5A00)

val PaperWhite  = Color(0xFFF7F5F2)
val PaperBeige  = Color(0xFFF0EDE8)
val BorderColor = Color(0xFFD6D2CC)
val TextPrimary = Color(0xFF1A1816)
val TextMuted   = Color(0xFF7A7570)
val TextHint    = Color(0xFFA09890)

val Purple50    = Color(0xFFE8E4EE)
val PurpleText  = Color(0xFF4A3870)
val Blue50      = Color(0xFFE2EEF8)
val BlueText    = Color(0xFF1A3D5C)

// ── Progress Levels ───────────────────────────────────────────────────────────

val LevelGray      = Color(0xFFBDBDBD)
val LevelRed       = Color(0xFFEF5350)
val LevelTurquoise = Color(0xFF26A69A)
val LevelGreen     = Color(0xFF66BB6A)

// ── Color scheme ──────────────────────────────────────────────────────────────

private val LightColors = lightColorScheme(
    primary          = ForestGreen,
    onPrimary        = Color.White,
    primaryContainer = ForestGreenBg,
    onPrimaryContainer = ForestGreenText,
    secondary        = Amber,
    onSecondary      = Color.White,
    secondaryContainer = AmberBg,
    onSecondaryContainer = AmberText,
    background       = PaperBeige,
    onBackground     = TextPrimary,
    surface          = PaperWhite,
    onSurface        = TextPrimary,
    surfaceVariant   = PaperBeige,
    onSurfaceVariant = TextMuted,
    outline          = BorderColor,
)

// ── Typography ────────────────────────────────────────────────────────────────

val KlaviatürTypography = Typography(
    headlineLarge  = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.W500, color = TextPrimary, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.W500, color = TextPrimary, letterSpacing = (-0.3).sp),
    titleLarge     = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W500, color = TextPrimary),
    titleMedium    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W500, color = TextPrimary),
    titleSmall     = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W500, color = TextPrimary),
    bodyMedium     = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = TextPrimary),
    bodySmall      = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = TextMuted),
    labelSmall     = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W500, color = TextMuted, letterSpacing = 0.05.sp),
)

// ── Theme ─────────────────────────────────────────────────────────────────────

@Composable
fun KlaviatürTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography  = KlaviatürTypography,
        content     = content
    )
}
