package com.algoryqr.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Foreground + soft background pair, as in the icon tiles: e.g. red stroke on a pale red square. */
@Immutable
data class Tone(val content: Color, val container: Color)

@Immutable
data class AppPalette(
    val base: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    /** Neutral filled surface for quiet chips. */
    val subtle: Color,
    /** Brand red used for buttons, selection and icons. */
    val accent: Color,
    /** Red readable as small text on white. */
    val accentText: Color,
    val red: Tone,
    val rose: Tone,
    val amber: Tone,
    val green: Tone,
    val blue: Tone,
    val slate: Tone,
    /** Soft glow colours drawn over [base]. */
    val auraPrimary: Color,
    val auraSecondary: Color,
    val auraAlpha: Float,
)

private val LightPalette = AppPalette(
    base = Color.White,
    surface = Color.White,
    ink = Color(0xFF1F1A1B),
    muted = Color(0xFF75696B),
    line = Color(0xFFEFE7E8),
    subtle = Color(0xFFF7F4F4),
    accent = Color(0xFFE11D2E),
    accentText = Color(0xFFC81E2B),
    red = Tone(Color(0xFFE11D2E), Color(0xFFFFF0F1)),
    rose = Tone(Color(0xFFDB2777), Color(0xFFFDF0F6)),
    amber = Tone(Color(0xFFD97706), Color(0xFFFFF7E8)),
    green = Tone(Color(0xFF15803D), Color(0xFFEAF8EE)),
    blue = Tone(Color(0xFF2563EB), Color(0xFFEEF3FF)),
    slate = Tone(Color(0xFF57534E), Color(0xFFF4F2F1)),
    auraPrimary = Color(0xFFE11D2E),
    auraSecondary = Color(0xFFFF8A8F),
    auraAlpha = 0.14f,
)

private val DarkPalette = AppPalette(
    base = Color(0xFF141012),
    surface = Color(0xFF1E191B),
    ink = Color(0xFFF6EEEF),
    muted = Color(0xFFAB9EA0),
    line = Color(0xFF382F31),
    subtle = Color(0xFF292224),
    accent = Color(0xFFF0434F),
    accentText = Color(0xFFFF8A8F),
    red = Tone(Color(0xFFFF6B73), Color(0xFF3D1519)),
    rose = Tone(Color(0xFFF472B6), Color(0xFF3A1528)),
    amber = Tone(Color(0xFFFBBF24), Color(0xFF3A2A0D)),
    green = Tone(Color(0xFF4ADE80), Color(0xFF0F2A19)),
    blue = Tone(Color(0xFF7AA2FF), Color(0xFF14213D)),
    slate = Tone(Color(0xFFC4BDBA), Color(0xFF2A2524)),
    auraPrimary = Color(0xFFE11D2E),
    auraSecondary = Color(0xFF9F1239),
    auraAlpha = 0.18f,
)

private val LocalAppPalette = staticCompositionLocalOf { LightPalette }

/** Theme-aware colours; read them inside composition. */
object AppColors {
    val Base: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.base
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.surface
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.ink
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.muted
    val Line: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.line
    val Subtle: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.subtle
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.accent
    val AccentText: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.accentText
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.red.content

    val Red: Tone @Composable @ReadOnlyComposable get() = LocalAppPalette.current.red
    val Rose: Tone @Composable @ReadOnlyComposable get() = LocalAppPalette.current.rose
    val Amber: Tone @Composable @ReadOnlyComposable get() = LocalAppPalette.current.amber
    val Green: Tone @Composable @ReadOnlyComposable get() = LocalAppPalette.current.green
    val Blue: Tone @Composable @ReadOnlyComposable get() = LocalAppPalette.current.blue
    val Slate: Tone @Composable @ReadOnlyComposable get() = LocalAppPalette.current.slate

    val palette: AppPalette @Composable @ReadOnlyComposable get() = LocalAppPalette.current
}

private fun AppPalette.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = red.container,
        onPrimaryContainer = accentText,
        secondary = accentText,
        onSecondary = surface,
        background = this.base,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = this.base,
        onSurfaceVariant = muted,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        outline = line,
        outlineVariant = line,
        error = red.content,
    )
}

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

@Composable
fun AlgoryQrTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    MaterialTheme(colorScheme = palette.toColorScheme(darkTheme), typography = typography) {
        // Screens draw on the aura rather than a Material Surface, so the default text colour is set here.
        CompositionLocalProvider(LocalAppPalette provides palette, LocalContentColor provides palette.ink, content = content)
    }
}
