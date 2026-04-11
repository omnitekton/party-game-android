package io.github.verbus.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val BaseTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 48.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.ExtraBold,
    ),
    displayMedium = TextStyle(
        fontSize = 40.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.ExtraBold,
    ),
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
)

fun responsiveTypography(scale: Float): Typography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.scaled(scale),
    displayMedium = BaseTypography.displayMedium.scaled(scale),
    headlineLarge = BaseTypography.headlineLarge.scaled(scale),
    headlineMedium = BaseTypography.headlineMedium.scaled(scale),
    headlineSmall = BaseTypography.headlineSmall.scaled(scale),
    titleLarge = BaseTypography.titleLarge.scaled(scale),
    titleMedium = BaseTypography.titleMedium.scaled(scale),
    titleSmall = BaseTypography.titleSmall.scaled(scale),
    bodyLarge = BaseTypography.bodyLarge.scaled(scale),
    bodyMedium = BaseTypography.bodyMedium.scaled(scale),
    bodySmall = BaseTypography.bodySmall.scaled(scale),
    labelLarge = BaseTypography.labelLarge.scaled(scale),
    labelMedium = BaseTypography.labelMedium.scaled(scale),
)

private fun TextStyle.scaled(scale: Float): TextStyle = copy(
    fontSize = fontSize.scaled(scale),
    lineHeight = lineHeight.scaled(scale),
)

private fun TextUnit.scaled(scale: Float): TextUnit = if (this == TextUnit.Unspecified) {
    this
} else {
    (value * scale).sp
}