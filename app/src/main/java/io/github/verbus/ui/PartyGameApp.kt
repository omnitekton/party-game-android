package io.github.verbus.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.verbus.R
import io.github.verbus.app.AppLocaleController
import io.github.verbus.app.ProceduralSoundPlayer
import io.github.verbus.domain.model.PartyGameError
import io.github.verbus.ui.feedback.ProvideUiFeedback
import io.github.verbus.ui.screens.CategorySelectionScreen
import io.github.verbus.ui.screens.MainMenuScreen
import io.github.verbus.ui.screens.ModeSelectionScreen
import io.github.verbus.ui.screens.OptionsScreen
import io.github.verbus.ui.screens.SessionScreen
import io.github.verbus.ui.theme.VerbusTheme
import io.github.verbus.ui.viewmodel.CatalogViewModel
import io.github.verbus.ui.viewmodel.OptionsViewModel
import io.github.verbus.ui.viewmodel.RoundViewModel

private const val ROUTE_MENU = "menu"
private const val ROUTE_MODES = "modes"
private const val ROUTE_CATEGORIES = "categories"
private const val ROUTE_OPTIONS = "options"
private const val ROUTE_SESSION = "session"

@Composable
fun PartyGameApp(
    catalogViewModel: CatalogViewModel,
    optionsViewModel: OptionsViewModel,
    roundViewModel: RoundViewModel,
    soundPlayer: ProceduralSoundPlayer,
    onExit: () -> Unit,
) {
    val navController = rememberNavController()
    val catalogState by catalogViewModel.uiState.collectAsStateWithLifecycle()
    val optionsState by optionsViewModel.uiState.collectAsStateWithLifecycle()
    val roundState by roundViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    AppResumeEffect(onResumed = roundViewModel::onAppResumed)

    LaunchedEffect(optionsState.isLoaded, optionsState.settings.language) {
        if (!optionsState.isLoaded) return@LaunchedEffect
        AppLocaleController.applyLanguage(optionsState.settings.language)
    }

    LaunchedEffect(optionsState.isLoaded, optionsState.settings.selectedSoundSetId) {
        if (!optionsState.isLoaded) return@LaunchedEffect
        soundPlayer.prepareSelectedSet(optionsState.settings.selectedSoundSetId)
    }

    LaunchedEffect(roundState.activeRound, roundState.summary, currentRoute) {
        if ((roundState.activeRound != null || roundState.summary != null) && currentRoute != ROUTE_SESSION) {
            navController.navigate(ROUTE_SESSION) { launchSingleTop = true }
        }
    }

    VerbusTheme(settings = optionsState.settings) {
        ProvideUiFeedback(settings = optionsState.settings, soundPlayer = soundPlayer) {
            NavHost(navController = navController, startDestination = ROUTE_MENU) {
                composable(ROUTE_MENU) {
                    MainMenuScreen(
                        onPlay = { navController.navigate(ROUTE_MODES) },
                        onOptions = { navController.navigate(ROUTE_OPTIONS) },
                        onExit = onExit,
                    )
                }
                composable(ROUTE_MODES) {
                    ModeSelectionScreen(
                        onStorytellingSelected = { navController.navigate(ROUTE_CATEGORIES) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_CATEGORIES) {
                    CategorySelectionScreen(
                        uiState = catalogState,
                        onCategorySelected = roundViewModel::startRound,
                        onReload = catalogViewModel::reload,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_OPTIONS) {
                    OptionsScreen(
                        uiState = optionsState,
                        onBack = { navController.popBackStack() },
                        onSignalMethodSelected = optionsViewModel::setSignalMethod,
                        onLanguageDelta = optionsViewModel::cycleLanguage,
                        onTopicsPerRoundDelta = optionsViewModel::changeTopicsPerRound,
                        onTopicDurationDelta = optionsViewModel::changeTopicDuration,
                        onCountdownDurationDelta = optionsViewModel::changePreRoundCountdown,
                        onTimeoutDurationDelta = optionsViewModel::changeTimeoutDuration,
                        onHapticFeedbackChanged = optionsViewModel::setHapticFeedback,
                        onKeepScreenAwakeChanged = optionsViewModel::setKeepScreenAwake,
                        onBackgroundColorPrimaryDelta = optionsViewModel::cycleBackgroundColorPrimary,
                        onBackgroundColorSecondaryDelta = optionsViewModel::cycleBackgroundColorSecondary,
                        onFontColorDelta = optionsViewModel::cycleFontColor,
                        onAccentColorDelta = optionsViewModel::cycleAccentColor,
                        onAccentTextColorDelta = optionsViewModel::cycleAccentTextColor,
                        onSoundsEnabledChanged = optionsViewModel::setSoundsEnabled,
                        onSoundVolumeDelta = optionsViewModel::changeSoundVolume,
                        onSoundSetDelta = optionsViewModel::cycleSoundSet,
                        onTouchVisualFeedbackChanged = optionsViewModel::setTouchVisualFeedback,
                        onTouchHapticFeedbackChanged = optionsViewModel::setTouchHapticFeedback,
                        onTouchSoundFeedbackChanged = optionsViewModel::setTouchSoundFeedback,
                    )
                }
                composable(ROUTE_SESSION) {
                    SessionScreen(
                        uiState = roundState,
                        soundPlayer = soundPlayer,
                        onComplete = roundViewModel::completeCurrentTopic,
                        onSkip = roundViewModel::skipCurrentTopic,
                        onPlayAgain = roundViewModel::startRound,
                        onBackToMenu = {
                            roundViewModel.clearSummary()
                            navController.navigate(ROUTE_MENU) {
                                popUpTo(ROUTE_MENU) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onDismissError = roundViewModel::dismissError,
                        onDismissInfo = roundViewModel::dismissInfoMessage,
                    )
                }
            }

            val globalError = roundState.error
            if (currentRoute != ROUTE_SESSION && globalError != null) {
                GlobalMessageDialog(
                    text = partyGameErrorText(globalError),
                    onDismiss = roundViewModel::dismissError,
                )
            }
        }
    }
}

@Composable
private fun partyGameErrorText(error: PartyGameError): String = when (error) {
    PartyGameError.CATEGORY_UNAVAILABLE -> stringResource(id = R.string.error_category_unavailable)
    PartyGameError.NO_TOPICS_AVAILABLE -> stringResource(id = R.string.error_no_topics_available)
    PartyGameError.MISSING_CONTENT_INDEX -> stringResource(id = R.string.error_missing_content_index)
    PartyGameError.ROUND_RESTORE_FAILED -> stringResource(id = R.string.error_round_restore_failed)
    PartyGameError.SENSOR_UNAVAILABLE -> stringResource(id = R.string.error_sensor_unavailable)
    PartyGameError.ROUND_NOT_ACTIVE -> stringResource(id = R.string.error_round_not_active)
    PartyGameError.GENERIC -> stringResource(id = R.string.error_generic)
}

@Composable
private fun GlobalMessageDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.action_dismiss))
            }
        },
        text = { Text(text = text) },
    )
}

@Composable
private fun AppResumeEffect(onResumed: () -> Unit) {
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                onResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
