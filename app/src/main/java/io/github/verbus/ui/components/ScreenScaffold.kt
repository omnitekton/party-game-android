package io.github.verbus.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import io.github.verbus.ui.readableTextStyle
import io.github.verbus.ui.feedback.rememberUiFeedbackController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    backLabel: String?,
    onBack: (() -> Unit)?,
    content: @Composable (PaddingValues) -> Unit,
) {
    val feedback = rememberUiFeedbackController()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        style = readableTextStyle(
                            MaterialTheme.typography.titleLarge,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    if (onBack != null && backLabel != null) {
                        TextButton(
                            onClick = {
                                feedback.onUiInteraction()
                                onBack()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Text(
                                text = backLabel,
                                style = readableTextStyle(
                                    MaterialTheme.typography.labelLarge,
                                    backgroundColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                        }
                    }
                },
            )
        },
        content = content,
    )
}
