package io.github.verbus.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle

@Composable
fun currentLanguageCode(): String {
    val locale = LocalConfiguration.current.locales[0]
    return locale?.language ?: "en"
}

@Composable
fun readableTextStyle(
    style: TextStyle,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
): TextStyle = style.copy(
    shadow = readabilityTextShadow(backgroundColor = backgroundColor),
)

fun readabilityTextShadow(backgroundColor: Color): Shadow {
    val shadowColor = if (backgroundColor.luminance() > 0.58f) {
        Color.Black.copy(alpha = 0.82f)
    } else {
        Color.White.copy(alpha = 0.56f)
    }
    return Shadow(
        color = shadowColor,
        offset = Offset(0f, 2f),
        blurRadius = 8f,
    )
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
