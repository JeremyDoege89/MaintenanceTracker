package dev.dreamz.weekendfleet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Fixed palette (no dynamic color): status colors must stay legible and distinct everywhere,
// and dark mode is designed for dim garages, not derived from wallpaper.

private val LightColors = lightColorScheme(
    primary = Color(0xFF2B5D4E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EBD8),
    onPrimaryContainer = Color(0xFF002118),
    secondary = Color(0xFF7A5900),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA6),
    onSecondaryContainer = Color(0xFF261900),
    background = Color(0xFFF7F8F5),
    onBackground = Color(0xFF181C1A),
    surface = Color(0xFFF7F8F5),
    onSurface = Color(0xFF181C1A),
    surfaceVariant = Color(0xFFDCE5DF),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainer = Color(0xFFECEFEB),
    surfaceContainerHigh = Color(0xFFE5E9E5),
    outline = Color(0xFF6F7975),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD5BD),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF0E4F3F),
    onPrimaryContainer = Color(0xFFB7EBD8),
    secondary = Color(0xFFF2C065),
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF0F1211),
    onBackground = Color(0xFFE2E6E3),
    surface = Color(0xFF0F1211),
    onSurface = Color(0xFFE2E6E3),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFC3CCC7),
    surfaceContainer = Color(0xFF1B1F1D),
    surfaceContainerHigh = Color(0xFF252A28),
    outline = Color(0xFF8D9692),
    error = Color(0xFFFFB4AB),
)

@Immutable
data class StatusColors(
    val overdue: Color, val onOverdue: Color,
    val dueSoon: Color, val onDueSoon: Color,
    val ok: Color, val onOk: Color,
    val manual: Color, val onManual: Color,
)

private val LightStatus = StatusColors(
    overdue = Color(0xFFB3261E), onOverdue = Color.White,
    dueSoon = Color(0xFF8A5A00), onDueSoon = Color.White,
    ok = Color(0xFF2E6B3A), onOk = Color.White,
    manual = Color(0xFF5C6460), onManual = Color.White,
)

private val DarkStatus = StatusColors(
    overdue = Color(0xFFFFB4AB), onOverdue = Color(0xFF690005),
    dueSoon = Color(0xFFFFB95C), onDueSoon = Color(0xFF452B00),
    ok = Color(0xFF8BD79A), onOk = Color(0xFF00391A),
    manual = Color(0xFFC3CCC7), onManual = Color(0xFF2B3330),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val AppTypography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    )
}

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
