package io.github.offlinepartygame.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.offlinepartygame.R
import io.github.offlinepartygame.domain.model.AppLanguage
import io.github.offlinepartygame.domain.model.SignalMethod
import io.github.offlinepartygame.domain.model.ThemeColorOption
import io.github.offlinepartygame.ui.components.ChoiceSettingCard
import io.github.offlinepartygame.ui.components.ScreenScaffold
import io.github.offlinepartygame.ui.components.SectionHeader
import io.github.offlinepartygame.ui.components.StepSettingCard
import io.github.offlinepartygame.ui.components.ToggleSettingCard
import io.github.offlinepartygame.ui.viewmodel.OptionsUiState

@Composable
fun OptionsScreen(
    uiState: OptionsUiState,
    onBack: () -> Unit,
    onSignalMethodSelected: (SignalMethod) -> Unit,
    onLanguageDelta: (Int) -> Unit,
    onTopicsPerRoundDelta: (Int) -> Unit,
    onTopicDurationDelta: (Int) -> Unit,
    onCountdownDurationDelta: (Int) -> Unit,
    onTimeoutDurationDelta: (Int) -> Unit,
    onHapticFeedbackChanged: (Boolean) -> Unit,
    onKeepScreenAwakeChanged: (Boolean) -> Unit,
    onBackgroundColorPrimaryDelta: (Int) -> Unit,
    onBackgroundColorSecondaryDelta: (Int) -> Unit,
    onFontColorDelta: (Int) -> Unit,
    onSoundsEnabledChanged: (Boolean) -> Unit,
    onSoundVolumeDelta: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        title = stringResource(id = R.string.screen_options_title),
        backLabel = stringResource(id = R.string.action_back),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            item {
                SectionHeader(text = stringResource(id = R.string.options_section_language))
            }
            item {
                ChoiceSettingCard(
                    title = stringResource(id = R.string.options_language),
                    valueText = languageLabel(uiState.settings.language),
                    onPrevious = { onLanguageDelta(-1) },
                    onNext = { onLanguageDelta(1) },
                )
            }
            item {
                SectionHeader(text = stringResource(id = R.string.options_section_gameplay))
            }
            item {
                SignalMethodCard(
                    selected = uiState.settings.signalMethod,
                    shakeSupported = uiState.isShakeSupported,
                    onSelected = onSignalMethodSelected,
                )
            }
            item {
                StepSettingCard(
                    title = stringResource(id = R.string.options_topics_per_round),
                    valueText = stringResource(id = R.string.options_value_format, uiState.settings.topicsPerRound),
                    onDecrease = { onTopicsPerRoundDelta(-1) },
                    onIncrease = { onTopicsPerRoundDelta(1) },
                )
            }
            item {
                StepSettingCard(
                    title = stringResource(id = R.string.options_topic_duration),
                    valueText = stringResource(id = R.string.options_value_format, uiState.settings.topicDurationSec),
                    onDecrease = { onTopicDurationDelta(-5) },
                    onIncrease = { onTopicDurationDelta(5) },
                )
            }
            item {
                StepSettingCard(
                    title = stringResource(id = R.string.options_countdown_duration),
                    valueText = stringResource(id = R.string.options_value_format, uiState.settings.preRoundCountdownSec),
                    onDecrease = { onCountdownDurationDelta(-1) },
                    onIncrease = { onCountdownDurationDelta(1) },
                )
            }
            item {
                StepSettingCard(
                    title = stringResource(id = R.string.options_timeout_duration),
                    valueText = stringResource(id = R.string.options_value_format, uiState.settings.timeoutMessageDurationSec),
                    onDecrease = { onTimeoutDurationDelta(-1) },
                    onIncrease = { onTimeoutDurationDelta(1) },
                )
            }
            item {
                ToggleSettingCard(
                    title = stringResource(id = R.string.options_haptics),
                    checked = uiState.settings.hapticFeedbackEnabled,
                    onCheckedChange = onHapticFeedbackChanged,
                )
            }
            item {
                ToggleSettingCard(
                    title = stringResource(id = R.string.options_keep_screen_awake),
                    checked = uiState.settings.keepScreenAwake,
                    onCheckedChange = onKeepScreenAwakeChanged,
                )
            }
            item {
                SectionHeader(text = stringResource(id = R.string.options_section_theme))
            }
            item {
                ChoiceSettingCard(
                    title = stringResource(id = R.string.options_theme_background_primary),
                    valueText = themeColorLabel(uiState.settings.backgroundColorPrimary),
                    onPrevious = { onBackgroundColorPrimaryDelta(-1) },
                    onNext = { onBackgroundColorPrimaryDelta(1) },
                )
            }
            item {
                ChoiceSettingCard(
                    title = stringResource(id = R.string.options_theme_background_secondary),
                    valueText = themeColorLabel(uiState.settings.backgroundColorSecondary),
                    onPrevious = { onBackgroundColorSecondaryDelta(-1) },
                    onNext = { onBackgroundColorSecondaryDelta(1) },
                )
            }
            item {
                ChoiceSettingCard(
                    title = stringResource(id = R.string.options_theme_font_color),
                    valueText = themeColorLabel(uiState.settings.fontColor),
                    onPrevious = { onFontColorDelta(-1) },
                    onNext = { onFontColorDelta(1) },
                )
            }
            item {
                SectionHeader(text = stringResource(id = R.string.options_section_sounds))
            }
            item {
                ToggleSettingCard(
                    title = stringResource(id = R.string.options_sounds_enabled),
                    checked = uiState.settings.soundsEnabled,
                    onCheckedChange = onSoundsEnabledChanged,
                )
            }
            item {
                StepSettingCard(
                    title = stringResource(id = R.string.options_sound_volume),
                    valueText = stringResource(id = R.string.options_value_format, uiState.settings.soundVolumeLevel),
                    onDecrease = { onSoundVolumeDelta(-1) },
                    onIncrease = { onSoundVolumeDelta(1) },
                )
            }
        }
    }
}

@Composable
private fun SignalMethodCard(
    selected: SignalMethod,
    shakeSupported: Boolean,
    onSelected: (SignalMethod) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.options_signal_method),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            SignalOptionRow(
                title = stringResource(id = R.string.options_signal_double_tap),
                selected = selected == SignalMethod.DOUBLE_TAP,
                enabled = true,
                onClick = { onSelected(SignalMethod.DOUBLE_TAP) },
            )
            SignalOptionRow(
                title = stringResource(id = R.string.options_signal_shake),
                selected = selected == SignalMethod.SHAKE,
                enabled = shakeSupported,
                onClick = { onSelected(SignalMethod.SHAKE) },
            )
            SignalOptionRow(
                title = stringResource(id = R.string.options_signal_button),
                selected = selected == SignalMethod.BUTTON,
                enabled = true,
                onClick = { onSelected(SignalMethod.BUTTON) },
            )
            if (!shakeSupported) {
                Text(
                    text = stringResource(id = R.string.error_sensor_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SignalOptionRow(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(id = R.string.language_system)
    AppLanguage.POLISH -> stringResource(id = R.string.language_polish)
    AppLanguage.ENGLISH -> stringResource(id = R.string.language_english)
}

@Composable
private fun themeColorLabel(color: ThemeColorOption): String = when (color) {
    ThemeColorOption.COLOR1 -> stringResource(id = R.string.theme_color_1)
    ThemeColorOption.COLOR2 -> stringResource(id = R.string.theme_color_2)
    ThemeColorOption.COLOR3 -> stringResource(id = R.string.theme_color_3)
    ThemeColorOption.COLOR4 -> stringResource(id = R.string.theme_color_4)
    ThemeColorOption.COLOR5 -> stringResource(id = R.string.theme_color_5)
    ThemeColorOption.COLOR6 -> stringResource(id = R.string.theme_color_6)
    ThemeColorOption.COLOR7 -> stringResource(id = R.string.theme_color_7)
    ThemeColorOption.COLOR8 -> stringResource(id = R.string.theme_color_8)
    ThemeColorOption.COLOR9 -> stringResource(id = R.string.theme_color_9)
    ThemeColorOption.COLOR10 -> stringResource(id = R.string.theme_color_10)
}
