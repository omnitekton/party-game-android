package io.github.offlinepartygame.domain.model

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
    val soundsEnabled: Boolean = true,
    val soundVolumeLevel: Int = DEFAULT_SOUND_VOLUME_LEVEL,
) {
    fun sanitized(): AppSettings = copy(
        topicsPerRound = topicsPerRound.coerceIn(1, 100),
        topicDurationSec = topicDurationSec.coerceIn(5, 300),
        preRoundCountdownSec = preRoundCountdownSec.coerceIn(0, 60),
        timeoutMessageDurationSec = timeoutMessageDurationSec.coerceIn(1, 30),
        soundVolumeLevel = soundVolumeLevel.coerceIn(1, 10),
    )

    companion object {
        const val DEFAULT_TOPICS_PER_ROUND = 5
        const val DEFAULT_TOPIC_DURATION_SEC = 30
        const val DEFAULT_PRE_ROUND_COUNTDOWN_SEC = 5
        const val DEFAULT_TIMEOUT_MESSAGE_DURATION_SEC = 5
        const val DEFAULT_SOUND_VOLUME_LEVEL = 7
        val DEFAULT_BACKGROUND_COLOR_PRIMARY = ThemeColorOption.COLOR5
        val DEFAULT_BACKGROUND_COLOR_SECONDARY = ThemeColorOption.COLOR3
        val DEFAULT_FONT_COLOR = ThemeColorOption.COLOR1
    }
}
