package io.github.offlinepartygame.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.offlinepartygame.app.ShakeSupportChecker
import io.github.offlinepartygame.domain.model.ActiveRound
import io.github.offlinepartygame.domain.model.AppSettings
import io.github.offlinepartygame.domain.model.GameMode
import io.github.offlinepartygame.domain.model.PartyGameError
import io.github.offlinepartygame.domain.model.RoundSummary
import io.github.offlinepartygame.domain.model.SignalMethod
import io.github.offlinepartygame.domain.repository.SettingsRepository
import io.github.offlinepartygame.domain.service.PartyGameException
import io.github.offlinepartygame.domain.service.RoundCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RoundUiState(
    val isLoading: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val activeRound: ActiveRound? = null,
    val summary: RoundSummary? = null,
    val error: PartyGameError? = null,
    val infoMessage: PartyGameError? = null,
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val isShakeSupported: Boolean = true,
    val completionFeedbackToken: Int = 0,
) {
    val effectiveSignalMethod: SignalMethod
        get() = if (settings.signalMethod == SignalMethod.SHAKE && !isShakeSupported) {
            SignalMethod.DOUBLE_TAP
        } else {
            settings.signalMethod
        }
}

class RoundViewModel(
    private val roundCoordinator: RoundCoordinator,
    settingsRepository: SettingsRepository,
    shakeSupportChecker: ShakeSupportChecker,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RoundUiState(isShakeSupported = shakeSupportChecker.isShakeSupported()),
    )
    val uiState: StateFlow<RoundUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        settings = settings,
                        infoMessage = if (settings.signalMethod == SignalMethod.SHAKE && !current.isShakeSupported) {
                            PartyGameError.SENSOR_UNAVAILABLE
                        } else {
                            current.infoMessage
                        },
                    )
                }
            }
        }
        refreshFromPersistence()
    }

    fun refreshFromPersistence() {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isLoading = current.activeRound != null,
                    error = null,
                )
            }
            roundCoordinator.restoreActiveRound()
                .onSuccess { result ->
                    updateFromResult(
                        result = result,
                        preserveSummaryWhenResultEmpty = true,
                    )
                }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            activeRound = null,
                            summary = current.summary,
                            error = mapError(throwable, PartyGameError.ROUND_RESTORE_FAILED),
                        )
                    }
                    restartTicker(null)
                }
        }
    }

    fun onAppResumed() {
        refreshFromPersistence()
    }

    fun startRound(categoryId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, summary = null) }
            roundCoordinator.startRound(
                mode = GameMode.STORYTELLING,
                categoryId = categoryId,
                settings = _uiState.value.settings,
            ).onSuccess { result ->
                updateFromResult(
                    result = result,
                    infoMessage = if (_uiState.value.settings.signalMethod == SignalMethod.SHAKE &&
                        !_uiState.value.isShakeSupported
                    ) {
                        PartyGameError.SENSOR_UNAVAILABLE
                    } else {
                        null
                    },
                )
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = mapError(throwable, PartyGameError.CATEGORY_UNAVAILABLE),
                    )
                }
                restartTicker(null)
            }
        }
    }

    fun completeCurrentTopic() {
        viewModelScope.launch {
            roundCoordinator.completeCurrentTopic()
                .onSuccess { result ->
                    updateFromResult(
                        result = result,
                        triggerFeedback = true,
                    )
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(error = mapError(throwable, PartyGameError.GENERIC))
                    }
                }
        }
    }

    fun skipCurrentTopic() {
        viewModelScope.launch {
            roundCoordinator.skipCurrentTopic()
                .onSuccess { result ->
                    updateFromResult(result = result)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(error = mapError(throwable, PartyGameError.GENERIC))
                    }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun clearSummary() {
        _uiState.update { it.copy(summary = null, error = null, infoMessage = null) }
    }

    private fun updateFromResult(
        result: io.github.offlinepartygame.domain.model.RoundProgressResult,
        infoMessage: PartyGameError? = null,
        triggerFeedback: Boolean = false,
        preserveSummaryWhenResultEmpty: Boolean = false,
    ) {
        _uiState.update { current ->
            val shouldPreserveSummary = preserveSummaryWhenResultEmpty &&
                current.summary != null &&
                current.activeRound == null &&
                result.activeRound == null &&
                result.summary == null

            current.copy(
                isLoading = false,
                activeRound = result.activeRound,
                summary = if (shouldPreserveSummary) current.summary else result.summary,
                error = null,
                infoMessage = infoMessage ?: result.warning,
                currentTimeMillis = System.currentTimeMillis(),
                completionFeedbackToken = if (triggerFeedback) current.completionFeedbackToken + 1 else current.completionFeedbackToken,
            )
        }
        restartTicker(result.activeRound)
    }

    private fun restartTicker(round: ActiveRound?) {
        tickerJob?.cancel()
        round ?: return

        tickerJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                _uiState.update { it.copy(currentTimeMillis = now) }

                val active = _uiState.value.activeRound ?: break
                if (now >= active.phaseEndsAtMillis) {
                    roundCoordinator.restoreActiveRound()
                        .onSuccess { result ->
                            updateFromResult(result)
                        }
                        .onFailure { throwable ->
                            _uiState.update {
                                it.copy(
                                    activeRound = null,
                                    summary = null,
                                    error = mapError(throwable, PartyGameError.ROUND_RESTORE_FAILED),
                                )
                            }
                            break
                        }
                }
                delay(250)
            }
        }
    }

    private fun mapError(throwable: Throwable, fallback: PartyGameError): PartyGameError =
        (throwable as? PartyGameException)?.error ?: fallback

    companion object {
        fun factory(
            roundCoordinator: RoundCoordinator,
            settingsRepository: SettingsRepository,
            shakeSupportChecker: ShakeSupportChecker,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RoundViewModel(roundCoordinator, settingsRepository, shakeSupportChecker) as T
        }
    }
}
