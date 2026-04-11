package io.github.verbus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.verbus.R
import io.github.verbus.ui.feedback.rememberUiFeedbackController

@Composable
fun MenuActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = rememberUiFeedbackController()
    Button(
        onClick = {
            feedback.onUiInteraction()
            onClick()
        },
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        AdaptiveActionText(
            text = text,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
fun SelectionCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    imageResName: String? = null,
) {
    val imageResId = remember(imageResName) { resolvePreviewDrawableName(imageResName) }
    val feedback = rememberUiFeedbackController()

    Card(
        onClick = {
            feedback.onUiInteraction()
            onClick()
        },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val ultraCompact = maxHeight < 118.dp || maxWidth < 150.dp
            val compact = ultraCompact || maxHeight < 152.dp || maxWidth < 184.dp
            val padding = when {
                ultraCompact -> 10.dp
                compact -> 14.dp
                else -> 20.dp
            }
            val spacing = when {
                ultraCompact -> 6.dp
                compact -> 8.dp
                else -> 12.dp
            }
            val previewHeight = when {
                subtitle.isNullOrBlank() && ultraCompact -> 34.dp
                subtitle.isNullOrBlank() && compact -> 48.dp
                subtitle.isNullOrBlank() -> 84.dp
                ultraCompact -> 38.dp
                compact -> 52.dp
                else -> 88.dp
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing, alignment = Alignment.CenterVertically),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = padding, vertical = padding),
            ) {
                CategoryPreviewSlot(
                    imageResId = imageResId,
                    height = previewHeight,
                )
                AdaptiveCardText(
                    text = title,
                    largeStyle = MaterialTheme.typography.titleLarge,
                    mediumStyle = MaterialTheme.typography.titleMedium,
                    compactStyle = MaterialTheme.typography.titleSmall,
                    maxLines = if (compact) 3 else 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    AdaptiveCardText(
                        text = it,
                        largeStyle = MaterialTheme.typography.bodyMedium,
                        mediumStyle = MaterialTheme.typography.bodySmall,
                        compactStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        maxLines = if (compact) 4 else 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPreviewSlot(
    @DrawableRes imageResId: Int?,
    height: Dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 8.dp),
    ) {
        if (imageResId != null) {
            Image(
                painter = painterResource(id = imageResId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = if (isMonochromeVector(imageResId)) {
                    ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                } else {
                    null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun isMonochromeVector(@DrawableRes imageResId: Int): Boolean = imageResId in setOf(
    R.drawable.ic_category_animals,
    R.drawable.ic_category_cars,
    R.drawable.ic_category_food,
    R.drawable.ic_category_science,
    R.drawable.ic_category_mystery,
    R.drawable.ic_category_plus18,
    R.drawable.ic_category_memes,
    R.drawable.ic_mode_storytelling,
)

fun calculateSelectionCardHeight(
    availableWidth: Dp,
    availableHeight: Dp,
    itemCount: Int,
    columns: Int,
    isLandscape: Boolean,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    maxVisibleRowsPortrait: Int,
    maxVisibleRowsLandscape: Int,
    minHeight: Dp,
    maxHeight: Dp,
    widthToHeightRatio: Float,
): Dp {
    val safeItemCount = itemCount.coerceAtLeast(1)
    val safeColumns = columns.coerceAtLeast(1)
    val totalHorizontalSpacing = horizontalSpacing * (safeColumns - 1)
    val cellWidth = (availableWidth - horizontalPadding - totalHorizontalSpacing) / safeColumns
    val rowsNeeded = ((safeItemCount + safeColumns - 1) / safeColumns).coerceAtLeast(1)
    val visibleRows = minOf(
        rowsNeeded,
        if (isLandscape) maxVisibleRowsLandscape else maxVisibleRowsPortrait,
    ).coerceAtLeast(1)
    val totalVerticalSpacing = verticalSpacing * (visibleRows - 1)
    val heightFromViewport = (availableHeight - verticalPadding - totalVerticalSpacing) / visibleRows
    val heightFromWidth = cellWidth * widthToHeightRatio
    return minOf(heightFromViewport, heightFromWidth).coerceIn(minHeight, maxHeight)
}

@Composable
fun StepSettingCard(
    title: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = rememberUiFeedbackController()
    val decreaseDescription = stringResource(id = R.string.action_decrease)
    val increaseDescription = stringResource(id = R.string.action_increase)
    val minusSymbol = stringResource(id = R.string.symbol_minus)
    val plusSymbol = stringResource(id = R.string.symbol_plus)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            AdaptiveCardText(
                text = title,
                largeStyle = MaterialTheme.typography.titleMedium,
                mediumStyle = MaterialTheme.typography.titleSmall,
                compactStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        feedback.onUiInteraction()
                        onDecrease()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = decreaseDescription },
                ) {
                    Text(
                        text = minusSymbol,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    ) {
                        AdaptiveCardText(
                            text = valueText,
                            largeStyle = MaterialTheme.typography.titleLarge,
                            mediumStyle = MaterialTheme.typography.titleMedium,
                            compactStyle = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        feedback.onUiInteraction()
                        onIncrease()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = increaseDescription },
                ) {
                    Text(
                        text = plusSymbol,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun ChoiceSettingCard(
    title: String,
    valueText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StepSettingCard(
        title = title,
        valueText = valueText,
        onDecrease = onPrevious,
        onIncrease = onNext,
        modifier = modifier,
    )
}

@Composable
fun ToggleSettingCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = rememberUiFeedbackController()
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            AdaptiveCardText(
                text = title,
                largeStyle = MaterialTheme.typography.titleMedium,
                mediumStyle = MaterialTheme.typography.titleSmall,
                compactStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
            )
            Switch(
                checked = checked,
                onCheckedChange = {
                    feedback.onUiInteraction()
                    onCheckedChange(it)
                },
            )
        }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun AdaptiveCardText(
    text: String,
    largeStyle: TextStyle,
    mediumStyle: TextStyle,
    compactStyle: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    BoxWithConstraints(modifier = modifier) {
        val style = when {
            maxWidth < 120.dp || text.length > 44 -> compactStyle
            maxWidth < 170.dp || text.length > 26 -> mediumStyle
            else -> largeStyle
        }
        Text(
            text = text,
            style = style,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AdaptiveActionText(
    text: String,
    modifier: Modifier = Modifier,
) {
    AdaptiveCardText(
        text = text,
        largeStyle = MaterialTheme.typography.titleLarge,
        mediumStyle = MaterialTheme.typography.titleMedium,
        compactStyle = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        modifier = modifier,
    )
}

@DrawableRes
private fun resolvePreviewDrawableName(name: String?): Int? = previewDrawableMap[name]

private val previewDrawableMap = mapOf(
    "ic_category_animals" to R.drawable.ic_category_animals,
    "ic_category_cars" to R.drawable.ic_category_cars,
    "ic_category_food" to R.drawable.ic_category_food,
    "ic_category_mystery" to R.drawable.ic_category_mystery,
    "ic_category_science" to R.drawable.ic_category_science,
    "ic_category_plus18" to R.drawable.ic_category_plus18,
    "ic_category_memes" to R.drawable.ic_category_memes,
    "ic_mode_storytelling" to R.drawable.ic_mode_storytelling,
)
