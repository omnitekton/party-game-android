package io.github.verbus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.verbus.R
import io.github.verbus.domain.model.PartyGameError
import io.github.verbus.ui.components.MenuActionButton
import io.github.verbus.ui.components.ScreenScaffold
import io.github.verbus.ui.components.SelectionCard
import io.github.verbus.ui.components.calculateSelectionCardHeight
import io.github.verbus.ui.readableTextStyle
import io.github.verbus.ui.currentLanguageCode
import io.github.verbus.ui.viewmodel.CatalogUiState

private val SelectionColumns = 2
private val SelectionHorizontalSpacing = 14.dp
private val SelectionVerticalSpacing = 14.dp
private val SelectionHorizontalPadding = 32.dp
private val SelectionVerticalPadding = 40.dp

@Composable
fun CategorySelectionScreen(
    uiState: CatalogUiState,
    onCategorySelected: (String) -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageCode = currentLanguageCode()

    ScreenScaffold(
        title = stringResource(id = R.string.screen_category_title),
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
                itemCount = uiState.categories.size.coerceAtLeast(1),
                columns = SelectionColumns,
                isLandscape = isLandscape,
                horizontalPadding = SelectionHorizontalPadding,
                verticalPadding = SelectionVerticalPadding,
                horizontalSpacing = SelectionHorizontalSpacing,
                verticalSpacing = SelectionVerticalSpacing,
                maxVisibleRowsPortrait = 4,
                maxVisibleRowsLandscape = 2,
                minHeight = 122.dp,
                maxHeight = 228.dp,
                widthToHeightRatio = 0.94f,
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(SelectionColumns),
                contentPadding = gridPadding,
                horizontalArrangement = Arrangement.spacedBy(SelectionHorizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(SelectionVerticalSpacing),
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp),
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    uiState.categories.isEmpty() -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = if (uiState.error != null) errorMessage(uiState.error) else stringResource(id = R.string.catalog_empty_message),
                                style = readableTextStyle(MaterialTheme.typography.bodyLarge),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            MenuActionButton(
                                text = stringResource(id = R.string.catalog_retry),
                                onClick = onReload,
                            )
                        }
                    }

                    else -> {
                        if (uiState.hasHiddenCategories) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = stringResource(id = R.string.catalog_warning_hidden_categories),
                                    style = readableTextStyle(MaterialTheme.typography.bodyMedium),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        items(uiState.categories, key = { it.id }) { category ->
                            SelectionCard(
                                title = category.displayName(languageCode),
                                imageResName = category.imageResName,
                                onClick = { onCategorySelected(category.id) },
                                modifier = Modifier.height(cardHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun errorMessage(error: PartyGameError): String = when (error) {
    PartyGameError.CATEGORY_UNAVAILABLE -> stringResource(id = R.string.error_category_unavailable)
    PartyGameError.NO_TOPICS_AVAILABLE -> stringResource(id = R.string.error_no_topics_available)
    PartyGameError.MISSING_CONTENT_INDEX -> stringResource(id = R.string.error_missing_content_index)
    PartyGameError.ROUND_RESTORE_FAILED -> stringResource(id = R.string.error_round_restore_failed)
    PartyGameError.SENSOR_UNAVAILABLE -> stringResource(id = R.string.error_sensor_unavailable)
    PartyGameError.ROUND_NOT_ACTIVE -> stringResource(id = R.string.error_round_not_active)
    PartyGameError.GENERIC -> stringResource(id = R.string.error_generic)
}
