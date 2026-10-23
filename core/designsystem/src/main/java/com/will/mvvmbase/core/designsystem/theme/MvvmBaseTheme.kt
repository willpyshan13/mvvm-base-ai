package com.will.mvvmbase.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

enum class AppColorPalette { Blue, Green, Purple }

@Composable
fun MvvmBaseTheme(
    darkTheme: Boolean,
    palette: AppColorPalette = AppColorPalette.Blue,
    content: @Composable () -> Unit,
) {
    val colors = when (palette) {
        AppColorPalette.Blue -> if (darkTheme) darkColorScheme(
            primary = Color(0xFFAFC6FF), onPrimary = Color(0xFF002F67),
            primaryContainer = Color(0xFF244779), onPrimaryContainer = Color(0xFFD9E2FF),
            secondary = Color(0xFFBCC7DC), onSecondary = Color(0xFF263249),
            tertiary = Color(0xFF8FD3DF), onTertiary = Color(0xFF00363F),
            background = Color(0xFF111318), surface = Color(0xFF111318),
        ) else lightColorScheme(
            primary = Color(0xFF2457C5), onPrimary = Color.White,
            primaryContainer = Color(0xFFDCE5FF), onPrimaryContainer = Color(0xFF112C62),
            secondary = Color(0xFF52617A), onSecondary = Color.White,
            tertiary = Color(0xFF006874), onTertiary = Color.White,
            background = Color(0xFFF8F9FF), surface = Color(0xFFF8F9FF),
        )
        AppColorPalette.Green -> if (darkTheme) darkColorScheme(
            primary = Color(0xFF83D9AC), onPrimary = Color(0xFF003823),
            primaryContainer = Color(0xFF15523B), onPrimaryContainer = Color(0xFFA0F4C8),
            secondary = Color(0xFFA6D1BB), onSecondary = Color(0xFF123B28),
            tertiary = Color(0xFFE6CD90), onTertiary = Color(0xFF403007),
            background = Color(0xFF101610), surface = Color(0xFF101610),
        ) else lightColorScheme(
            primary = Color(0xFF176B49), onPrimary = Color.White,
            primaryContainer = Color(0xFFB5F1CD), onPrimaryContainer = Color(0xFF00452B),
            secondary = Color(0xFF466854), onSecondary = Color.White,
            tertiary = Color(0xFF735C1A), onTertiary = Color.White,
            background = Color(0xFFF7FBF6), surface = Color(0xFFF7FBF6),
        )
        AppColorPalette.Purple -> if (darkTheme) darkColorScheme(
            primary = Color(0xFFD5B9FF), onPrimary = Color(0xFF3C1C64),
            primaryContainer = Color(0xFF57397D), onPrimaryContainer = Color(0xFFEDD8FF),
            secondary = Color(0xFFD2C0DB), onSecondary = Color(0xFF392D42),
            tertiary = Color(0xFFF4B8CF), onTertiary = Color(0xFF4A2436),
            background = Color(0xFF17131B), surface = Color(0xFF17131B),
        ) else lightColorScheme(
            primary = Color(0xFF6E42A0), onPrimary = Color.White,
            primaryContainer = Color(0xFFF0DFFF), onPrimaryContainer = Color(0xFF402068),
            secondary = Color(0xFF6C5875), onSecondary = Color.White,
            tertiary = Color(0xFF8A4261), onTertiary = Color.White,
            background = Color(0xFFFCF8FF), surface = Color(0xFFFCF8FF),
        )
    }
    val surface = colors.surface
    val onSurface = if (darkTheme) Color(0xFFE4E4E8) else Color(0xFF1A1B20)
    val themedColors = colors.copy(
        onBackground = onSurface,
        onSurface = onSurface,
        surfaceVariant = lerp(surface, colors.primary, if (darkTheme) 0.18f else 0.10f),
        onSurfaceVariant = lerp(onSurface, surface, if (darkTheme) 0.25f else 0.32f),
        outline = lerp(onSurface, surface, 0.50f),
        outlineVariant = lerp(onSurface, surface, 0.76f),
        surfaceContainerLowest = surface,
        surfaceContainerLow = lerp(surface, colors.primary, 0.025f),
        surfaceContainer = lerp(surface, colors.primary, 0.045f),
        surfaceContainerHigh = lerp(surface, colors.primary, 0.075f),
        surfaceContainerHighest = lerp(surface, colors.primary, 0.105f),
        surfaceBright = if (darkTheme) lerp(surface, Color.White, 0.09f) else surface,
        surfaceDim = if (darkTheme) surface else lerp(surface, Color.Black, 0.045f),
        secondaryContainer = lerp(surface, colors.secondary, if (darkTheme) 0.24f else 0.16f),
        onSecondaryContainer = onSurface,
        tertiaryContainer = lerp(surface, colors.tertiary, if (darkTheme) 0.24f else 0.16f),
        onTertiaryContainer = onSurface,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
        inversePrimary = colors.primary,
        surfaceTint = colors.primary,
    )
    MaterialTheme(
        colorScheme = themedColors,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp)),
        content = content,
    )
}
