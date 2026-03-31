package io.github.offlinepartygame.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.offlinepartygame.R

@Composable
fun MenuActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
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

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            CategoryPreviewSlot(imageResId = imageResId)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CategoryPreviewSlot(
    @DrawableRes imageResId: Int?,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .aspectRatio(3f)
            .padding(horizontal = 12.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
            )
        }
    }
}

private fun isMonochromeVector(@DrawableRes imageResId: Int): Boolean = imageResId in setOf(
    R.drawable.ic_category_animals,
    R.drawable.ic_category_cars,
    R.drawable.ic_category_food,
    R.drawable.ic_category_science,
    R.drawable.ic_mode_storytelling,
)

@Composable
fun StepSettingCard(
    title: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val decreaseDescription = stringResource(id = R.string.action_decrease)
    val increaseDescription = stringResource(id = R.string.action_increase)
    val minusSymbol = stringResource(id = R.string.symbol_minus)
    val plusSymbol = stringResource(id = R.string.symbol_plus)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onDecrease,
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
                        Text(
                            text = valueText,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                OutlinedButton(
                    onClick = onIncrease,
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
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
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

@DrawableRes
private fun resolvePreviewDrawableName(name: String?): Int? = previewDrawableMap[name]

private val previewDrawableMap = mapOf(
    "ic_category_animals" to R.drawable.ic_category_animals,
    "ic_category_cars" to R.drawable.ic_category_cars,
    "ic_category_food" to R.drawable.ic_category_food,
    "ic_category_mystery" to R.drawable.ic_category_mystery,
    "ic_category_science" to R.drawable.ic_category_science,
    "ic_mode_storytelling" to R.drawable.ic_mode_storytelling,
)
