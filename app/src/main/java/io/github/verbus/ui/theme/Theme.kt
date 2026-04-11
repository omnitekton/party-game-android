package io.github.verbus.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import io.github.verbus.domain.model.AppSettings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

@Composable
fun VerbusTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val typographyScale = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp,
        configuration.orientation,
    ) {
        responsiveTypographyScale(configuration)
    }

    val background = settings.backgroundColorPrimary.color
    val surface = settings.backgroundColorSecondary.color
    val onBase = settings.fontColor.color
    val accent = settings.accentColor.color
    val onAccent = settings.accentTextColor.color
    val isDark = background.luminance() < 0.45f

    val surfaceVariant = lerp(surface, accent, 0.18f)
    val outline = lerp(background, accent, 0.42f)
    val secondaryContainer = lerp(surface, accent, 0.24f)
    val tertiary = lerp(background, accent, 0.55f)
    val tertiaryContainer = lerp(surface, accent, 0.32f)
    val error = themedErrorColor(background = background, accent = accent)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            secondary = accent,
            onSecondary = onAccent,
            tertiary = tertiary,
            onTertiary = onBase,
            primaryContainer = secondaryContainer,
            onPrimaryContainer = onBase,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onBase,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onBase,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            onBackground = onBase,
            onSurface = onBase,
            onSurfaceVariant = onBase,
            outline = outline,
            error = error,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            secondary = accent,
            onSecondary = onAccent,
            tertiary = tertiary,
            onTertiary = onBase,
            primaryContainer = secondaryContainer,
            onPrimaryContainer = onBase,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onBase,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onBase,
            background = background,
            surface = surface,
            surfaceVariant = surfaceVariant,
            onBackground = onBase,
            onSurface = onBase,
            onSurfaceVariant = onBase,
            outline = outline,
            error = error,
            onError = Color.White,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = responsiveTypography(typographyScale),
        content = content,
    )
}

private fun responsiveTypographyScale(configuration: Configuration): Float {
    val smallestWidth = configuration.smallestScreenWidthDp
    val screenHeight = configuration.screenHeightDp
    val base = when {
        smallestWidth < 360 -> 0.88f
        smallestWidth < 400 -> 0.94f
        smallestWidth < 480 -> 1.0f
        smallestWidth < 600 -> 1.05f
        smallestWidth < 840 -> 1.12f
        else -> 1.22f
    }
    val landscapePenalty = if (
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && screenHeight < 430
    ) {
        0.06f
    } else {
        0f
    }
    return (base - landscapePenalty).coerceIn(0.84f, 1.24f)
}

private fun themedErrorColor(background: Color, accent: Color): Color {
    val fallback = Color(0xFFC63B34)
    return lerp(fallback, accent, 0.18f)
}
