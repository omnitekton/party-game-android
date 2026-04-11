package io.github.verbus.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
///import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.verbus.R
import io.github.verbus.ui.components.MenuActionButton

@Composable
fun MainMenuScreen(
    onPlay: () -> Unit,
    onOptions: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                ),
        ) {
            val isLandscape = maxWidth > maxHeight
            val isCompactLandscape = isLandscape && maxHeight < 430.dp
            val isTabletLike = maxWidth >= 700.dp

            if (isLandscape) {
                LandscapeMainMenuLayout(
                    onPlay = onPlay,
                    onOptions = onOptions,
                    onExit = onExit,
                    isCompact = isCompactLandscape,
                    isTabletLike = isTabletLike,
                )
            } else {
                PortraitMainMenuLayout(
                    onPlay = onPlay,
                    onOptions = onOptions,
                    onExit = onExit,
                    isTabletLike = isTabletLike,
                )
            }
        }
    }
}

@Composable
private fun PortraitMainMenuLayout(
    onPlay: () -> Unit,
    onOptions: () -> Unit,
    onExit: () -> Unit,
    isTabletLike: Boolean,
) {
    val horizontalPadding = if (isTabletLike) 40.dp else 24.dp
    val verticalPadding = if (isTabletLike) 28.dp else 20.dp
    val heroWidthFraction = if (isTabletLike) 0.58f else 0.78f
    val heroHeight = if (isTabletLike) 280.dp else 220.dp
    val heroMaxWidth = if (isTabletLike) 420.dp else 300.dp
    val buttonsMaxWidth = if (isTabletLike) 420.dp else 360.dp
    val contentSpacing = if (isTabletLike) 28.dp else 22.dp

    Column(
        verticalArrangement = Arrangement.spacedBy(contentSpacing, alignment = Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        MainMenuHero(
            titleStyle = if (isTabletLike) {
                MaterialTheme.typography.headlineLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
            heroWidthFraction = heroWidthFraction,
            heroHeight = heroHeight,
            heroMaxWidth = heroMaxWidth,
            compact = false,
        )

        MainMenuButtons(
            onPlay = onPlay,
            onOptions = onOptions,
            onExit = onExit,
            spacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = buttonsMaxWidth),
        )
    }
}

@Composable
private fun LandscapeMainMenuLayout(
    onPlay: () -> Unit,
    onOptions: () -> Unit,
    onExit: () -> Unit,
    isCompact: Boolean,
    isTabletLike: Boolean,
) {
    val horizontalPadding = if (isTabletLike) 40.dp else 24.dp
    val verticalPadding = if (isCompact) 12.dp else 20.dp
    val contentGap = if (isTabletLike) 36.dp else 24.dp

    val heroWidthFraction = when {
        isTabletLike -> 0.72f
        isCompact -> 0.82f
        else -> 0.76f
    }
    val heroHeight = when {
        isTabletLike -> 220.dp
        isCompact -> 110.dp
        else -> 150.dp
    }
    val heroMaxWidth = when {
        isTabletLike -> 460.dp
        isCompact -> 240.dp
        else -> 320.dp
    }

    val buttonsWidth = when {
        isTabletLike -> 360.dp
        isCompact -> 272.dp
        else -> 312.dp
    }

    val titleStyle = when {
        isTabletLike -> MaterialTheme.typography.headlineLarge
        isCompact -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.headlineMedium
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(contentGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        ) {
            MainMenuHero(
                titleStyle = titleStyle,
                heroWidthFraction = heroWidthFraction,
                heroHeight = heroHeight,
                heroMaxWidth = heroMaxWidth,
                compact = isCompact,
            )
        }

        MainMenuButtons(
            onPlay = onPlay,
            onOptions = onOptions,
            onExit = onExit,
            spacing = if (isCompact) 10.dp else 14.dp,
            modifier = Modifier.width(buttonsWidth),
        )
    }
}

@Composable
private fun MainMenuHero(
    titleStyle: TextStyle,
    heroWidthFraction: Float,
    heroHeight: Dp,
    heroMaxWidth: Dp,
    compact: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(id = R.string.app_name),
            style = titleStyle,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Image(
            painter = painterResource(id = R.drawable.ic_main_menu_guessing),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            ////colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            modifier = Modifier
                .fillMaxWidth(heroWidthFraction)
                .widthIn(max = heroMaxWidth)
                .height(heroHeight),
        )
    }
}

@Composable
private fun MainMenuButtons(
    onPlay: () -> Unit,
    onOptions: () -> Unit,
    onExit: () -> Unit,
    spacing: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing, alignment = Alignment.CenterVertically),
        modifier = modifier,
    ) {
        MenuActionButton(
            text = stringResource(id = R.string.menu_play),
            onClick = onPlay,
        )
        MenuActionButton(
            text = stringResource(id = R.string.menu_options),
            onClick = onOptions,
        )
        MenuActionButton(
            text = stringResource(id = R.string.menu_exit),
            onClick = onExit,
        )
    }
}