package io.github.verbus.domain.model

data class AppSettings(
    val signalMethod: SignalMethod = SignalMethod.DOUBLE_TAP,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val topicsPerRound: Int = DEFAULT_TOPICS_PER_ROUND,
    val topicDurationSec: Int = DEFAULT_TOPIC_DURATION_SEC,
    val preRoundCountdownSec: Int = DEFAULT_PRE_ROUND_COUNTDOWN_SEC,
    val timeoutMessageDurationSec: Int = DEFAULT_TIMEOUT_MESSAGE_DURATION_SEC,
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val backgroundColorPrimary: ThemeColorOption = DEFAULT_BACKGROUND_COLOR_PRIMARY,
    val backgroundColorSecondary: ThemeColorOption = DEFAULT_BACKGROUND_COLOR_SECONDARY,
    val fontColor: ThemeColorOption = DEFAULT_FONT_COLOR,
    val accentColor: ThemeColorOption = DEFAULT_ACCENT_COLOR,
    val accentTextColor: ThemeColorOption = DEFAULT_ACCENT_TEXT_COLOR,
    val soundsEnabled: Boolean = true,
    val soundVolumeLevel: Int = DEFAULT_SOUND_VOLUME_LEVEL,
    val selectedSoundSetId: String = DEFAULT_SOUND_SET_ID,
    val touchVisualFeedbackEnabled: Boolean = true,
    val touchHapticFeedbackEnabled: Boolean = true,
    val touchSoundFeedbackEnabled: Boolean = true,
) {
    fun sanitized(): AppSettings = copy(
        topicsPerRound = topicsPerRound.coerceIn(1, 100),
        topicDurationSec = topicDurationSec.coerceIn(5, 300),
        preRoundCountdownSec = preRoundCountdownSec.coerceIn(0, 60),
        timeoutMessageDurationSec = timeoutMessageDurationSec.coerceIn(1, 30),
        soundVolumeLevel = soundVolumeLevel.coerceIn(1, 10),
        selectedSoundSetId = selectedSoundSetId.ifBlank { DEFAULT_SOUND_SET_ID },
    )

    companion object {
        const val DEFAULT_TOPICS_PER_ROUND = 5
        const val DEFAULT_TOPIC_DURATION_SEC = 30
        const val DEFAULT_PRE_ROUND_COUNTDOWN_SEC = 5
        const val DEFAULT_TIMEOUT_MESSAGE_DURATION_SEC = 5
        const val DEFAULT_SOUND_VOLUME_LEVEL = 7
        const val DEFAULT_SOUND_SET_ID = "procedural"
        val DEFAULT_BACKGROUND_COLOR_PRIMARY = ThemeColorOption.COLOR5
        val DEFAULT_BACKGROUND_COLOR_SECONDARY = ThemeColorOption.COLOR3
        val DEFAULT_FONT_COLOR = ThemeColorOption.COLOR1
        val DEFAULT_ACCENT_COLOR = ThemeColorOption.COLOR10
        val DEFAULT_ACCENT_TEXT_COLOR = ThemeColorOption.COLOR1
    }
}
