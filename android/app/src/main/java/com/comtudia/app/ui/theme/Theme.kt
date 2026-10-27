package com.comtudia.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comtudia.app.core.Phase

/** Layout tokens shared by every screen. */
val ContentMaxWidth: Dp = 560.dp
val ContentGutter: Dp = 16.dp

/**
 * Fixed palette for the dark-only comtudia UI, matching the desktop app.
 * Every color used by the Android UI lives here so the screens never
 * hard-code their own copies.
 */
object ComtudiaColors {
    val Background = Color(0xFF0B0F0E)
    val Surface = Color(0xFF121A18)
    val SurfaceHigh = Color(0xFF17211F)
    val Track = Color(0xFF1C2725)
    val Foreground = Color(0xFFE7F1ED)
    val Muted = Color(0xFF8FA39D)
    val Outline = Color(0xFF26332F)

    val Eating = Color(0xFF3DD597)
    val OnEating = Color(0xFF062018)
    val Fasting = Color(0xFF8AA2FF)
    val OnFasting = Color(0xFF0B1020)

    val Error = Color(0xFFF26D6D)
    val OnError = Color(0xFF2A0C0C)
}

/** Accent pair for one phase. */
@Immutable
data class PhaseColors(
    val accent: Color,
    val onAccent: Color,
    val soft: Color,
)

/** Phase colors, exposed as a composition local for future theming. */
@Immutable
data class PhasePalette(
    val eating: PhaseColors,
    val fasting: PhaseColors,
) {
    fun of(phase: Phase): PhaseColors = when (phase) {
        Phase.EATING -> eating
        Phase.FASTING -> fasting
    }
}

val ComtudiaPhaseColors = PhasePalette(
    eating = PhaseColors(
        accent = ComtudiaColors.Eating,
        onAccent = ComtudiaColors.OnEating,
        soft = ComtudiaColors.Eating.copy(alpha = 0.18f),
    ),
    fasting = PhaseColors(
        accent = ComtudiaColors.Fasting,
        onAccent = ComtudiaColors.OnFasting,
        soft = ComtudiaColors.Fasting.copy(alpha = 0.18f),
    ),
)

val LocalPhaseColors = staticCompositionLocalOf { ComtudiaPhaseColors }

@Composable
fun phaseColors(phase: Phase): PhaseColors = LocalPhaseColors.current.of(phase)

/** Accent color for [phase] from the fixed comtudia palette. */
fun phaseColor(phase: Phase): Color = ComtudiaPhaseColors.of(phase).accent

/** Text/icon color that reads well on top of [phaseColor]. */
fun onPhaseColor(phase: Phase): Color = ComtudiaPhaseColors.of(phase).onAccent

val ComtudiaColorScheme = darkColorScheme(
    primary = ComtudiaColors.Eating,
    onPrimary = ComtudiaColors.OnEating,
    primaryContainer = ComtudiaColors.Eating.copy(alpha = 0.18f),
    onPrimaryContainer = ComtudiaColors.Eating,
    secondary = ComtudiaColors.Fasting,
    onSecondary = ComtudiaColors.OnFasting,
    secondaryContainer = ComtudiaColors.Fasting.copy(alpha = 0.18f),
    onSecondaryContainer = ComtudiaColors.Fasting,
    background = ComtudiaColors.Background,
    onBackground = ComtudiaColors.Foreground,
    surface = ComtudiaColors.Surface,
    onSurface = ComtudiaColors.Foreground,
    surfaceVariant = ComtudiaColors.Track,
    onSurfaceVariant = ComtudiaColors.Muted,
    surfaceContainer = ComtudiaColors.SurfaceHigh,
    surfaceContainerHigh = ComtudiaColors.SurfaceHigh,
    outline = ComtudiaColors.Outline,
    outlineVariant = ComtudiaColors.Track,
    error = ComtudiaColors.Error,
    onError = ComtudiaColors.OnError,
)

/**
 * Sans-serif text for labels and titles, monospace for the countdown and the
 * schedule preview so numbers stay visually aligned as they tick.
 */
val ComtudiaTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        letterSpacing = 1.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    ),
)

val ComtudiaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ComtudiaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPhaseColors provides ComtudiaPhaseColors) {
        MaterialTheme(
            colorScheme = ComtudiaColorScheme,
            typography = ComtudiaTypography,
            shapes = ComtudiaShapes,
        ) {
            content()
        }
    }
}
