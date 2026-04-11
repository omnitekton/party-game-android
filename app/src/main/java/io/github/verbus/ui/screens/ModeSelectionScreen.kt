package io.github.verbus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.verbus.R
import io.github.verbus.ui.components.ScreenScaffold
import io.github.verbus.ui.components.SelectionCard
import io.github.verbus.ui.components.calculateSelectionCardHeight

@Composable
fun ModeSelectionScreen(
    onStorytellingSelected: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        title = stringResource(id = R.string.screen_game_mode_title),
        backLabel = stringResource(id = R.string.action_back),
        onBack = onBack,
    ) { padding ->
        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
        ) {
            val isLandscape = maxWidth > maxHeight
            val gridPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            )
            val viewportHeight = maxHeight - padding.calculateTopPadding() - padding.calculateBottomPadding()
            val cardHeight = calculateSelectionCardHeight(
                availableWidth = maxWidth,
                availableHeight = viewportHeight,
                itemCount = 1,
                columns = 1,
                isLandscape = isLandscape,
                horizontalPadding = 32.dp,
                verticalPadding = 40.dp,
                horizontalSpacing = 0.dp,
                verticalSpacing = 0.dp,
                maxVisibleRowsPortrait = 1,
                maxVisibleRowsLandscape = 1,
                minHeight = 214.dp,
                maxHeight = 340.dp,
                widthToHeightRatio = 0.74f,
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = gridPadding,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SelectionCard(
                        title = stringResource(id = R.string.mode_storytelling_name),
                        subtitle = stringResource(id = R.string.mode_storytelling_description),
                        imageResName = "ic_mode_storytelling",
                        onClick = onStorytellingSelected,
                        modifier = Modifier.height(cardHeight),
                    )
                }
            }
        }
    }
}
