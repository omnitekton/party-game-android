package io.github.verbus.domain.repository

import io.github.verbus.domain.model.AppLanguage
import io.github.verbus.domain.model.AppSettings
import io.github.verbus.domain.model.SignalMethod
import io.github.verbus.domain.model.ThemeColorOption
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settingsFlow: Flow<AppSettings>
    suspend fun setSignalMethod(value: SignalMethod)
    suspend fun setLanguage(value: AppLanguage)
    suspend fun setTopicsPerRound(value: Int)
    suspend fun setTopicDurationSec(value: Int)
    suspend fun setPreRoundCountdownSec(value: Int)
    suspend fun setTimeoutMessageDurationSec(value: Int)
    suspend fun setHapticFeedbackEnabled(enabled: Boolean)
    suspend fun setKeepScreenAwake(enabled: Boolean)
    suspend fun setBackgroundColorPrimary(value: ThemeColorOption)
    suspend fun setBackgroundColorSecondary(value: ThemeColorOption)
    suspend fun setFontColor(value: ThemeColorOption)
    suspend fun setAccentColor(value: ThemeColorOption)
    suspend fun setAccentTextColor(value: ThemeColorOption)
    suspend fun setSoundsEnabled(enabled: Boolean)
    suspend fun setSoundVolumeLevel(value: Int)
    suspend fun setSelectedSoundSetId(value: String)
    suspend fun setTouchVisualFeedbackEnabled(enabled: Boolean)
    suspend fun setTouchHapticFeedbackEnabled(enabled: Boolean)
    suspend fun setTouchSoundFeedbackEnabled(enabled: Boolean)
}
