package io.github.verbus.ui.screens

import android.content.res.Configuration
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.verbus.R
import io.github.verbus.app.ProceduralSoundPlayer
import io.github.verbus.domain.model.ActiveRound
import io.github.verbus.domain.model.PartyGameError
import io.github.verbus.domain.model.RoundPhase
import io.github.verbus.domain.model.RoundSummary
import io.github.verbus.domain.model.SignalMethod
import io.github.verbus.domain.model.SoundEffect
import io.github.verbus.ui.currentLanguageCode
import io.github.verbus.ui.findActivity
import io.github.verbus.ui.viewmodel.RoundUiState
import kotlin.math.ceil
import kotlin.math.sqrt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance

@Composable
fun SessionScreen(
    uiState: RoundUiState,
    soundPlayer: ProceduralSoundPlayer,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onPlayAgain: (String) -> Unit,
    onBackToMenu: () -> Unit,
    onDismissError: () -> Unit,
    onDismissInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = uiState.activeRound != null) {}
    BackHandler(enabled = uiState.summary != null) { onBackToMenu() }

    GameplayStatusBarEffect(enabled = uiState.activeRound != null)

    KeepScreenAwakeEffect(enabled = uiState.settings.keepScreenAwake && uiState.activeRound != null)
    SessionSoundEffects(uiState = uiState, soundPlayer = soundPlayer)

    var isProcessingTopicAction by remember { mutableStateOf(false) }
    var activeTopicOverlay by remember { mutableStateOf<TopicActionOverlay?>(null) }
    var completionSuccessSoundDelayMillis by remember { mutableStateOf(0L) }
    val hapticFeedback = LocalHapticFeedback.current

    TopicActionHapticsEffect(
        actionOverlay = activeTopicOverlay,
        enabled = uiState.settings.hapticFeedbackEnabled,
        hapticFeedback = hapticFeedback,
    )

    LaunchedEffect(uiState.activeRound?.currentTopic?.stableId, uiState.summary) {
        if (uiState.activeRound != null || uiState.summary != null) {
            isProcessingTopicAction = false
            activeTopicOverlay = null
            completionSuccessSoundDelayMillis = 0L
        }
    }

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            isProcessingTopicAction = false
            activeTopicOverlay = null
            completionSuccessSoundDelayMillis = 0L
        }
    }

    val signalComplete: () -> Unit = {
        if (!isProcessingTopicAction) {
            isProcessingTopicAction = true
            completionSuccessSoundDelayMillis = 0L
            activeTopicOverlay = TopicActionOverlay.COMPLETED
        }
    }

    val signalCompleteFromDoubleTap: () -> Unit = {
        if (!isProcessingTopicAction) {
            isProcessingTopicAction = true
            completionSuccessSoundDelayMillis = 140L
            activeTopicOverlay = TopicActionOverlay.COMPLETED
        }
    }

    val signalSkip: () -> Unit = {
        if (!isProcessingTopicAction) {
            isProcessingTopicAction = true
            soundPlayer.play(
                effect = SoundEffect.TOPIC_SKIP,
                enabled = uiState.settings.soundsEnabled,
                volumeLevel = uiState.settings.soundVolumeLevel,
            )
            activeTopicOverlay = TopicActionOverlay.SKIPPED
        }
    }

    LaunchedEffect(activeTopicOverlay) {
        when (activeTopicOverlay) {
            TopicActionOverlay.COMPLETED -> {
                val overlayDurationMillis = 650L
                val successSoundDelayMillis = completionSuccessSoundDelayMillis.coerceAtLeast(0L)

                if (successSoundDelayMillis > 0L) {
                    kotlinx.coroutines.delay(successSoundDelayMillis)
                }

                soundPlayer.play(
                    effect = SoundEffect.TOPIC_SUCCESS,
                    enabled = uiState.settings.soundsEnabled,
                    volumeLevel = uiState.settings.soundVolumeLevel,
                )

                kotlinx.coroutines.delay((overlayDurationMillis - successSoundDelayMillis).coerceAtLeast(0L))
                onComplete()
                completionSuccessSoundDelayMillis = 0L
                activeTopicOverlay = null
            }

            TopicActionOverlay.SKIPPED -> {
                kotlinx.coroutines.delay(650)
                onSkip()
                activeTopicOverlay = null
            }

            null -> Unit
        }
    }

    val summary = uiState.summary
    val activeRound = uiState.activeRound

    Box(modifier = modifier.fillMaxSize()) {
        when {
            summary != null -> {
                SummaryContent(
                    summary = summary,
                    onPlayAgain = { onPlayAgain(summary.categoryId) },
                    onBackToMenu = onBackToMenu,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            activeRound != null -> {
                ActiveRoundContent(
                    round = activeRound,
                    currentTimeMillis = uiState.currentTimeMillis,
                    effectiveSignalMethod = uiState.effectiveSignalMethod,
                    soundsEnabled = uiState.settings.soundsEnabled,
                    soundVolumeLevel = uiState.settings.soundVolumeLevel,
                    touchVisualFeedbackEnabled = uiState.settings.touchVisualFeedbackEnabled,
                    touchHapticFeedbackEnabled = uiState.settings.touchHapticFeedbackEnabled,
                    touchSoundFeedbackEnabled = uiState.settings.touchSoundFeedbackEnabled,
                    soundPlayer = soundPlayer,
                    onSignalComplete = signalComplete,
                    onDoubleTapSignalComplete = signalCompleteFromDoubleTap,
                    onSkipTopic = signalSkip,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(id = R.string.error_round_not_active),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        activeTopicOverlay?.let { overlay ->
            TopicActionOverlayView(actionOverlay = overlay)
        }
    }

    uiState.error?.let { error ->
        MessageDialog(text = errorMessage(error), onDismiss = onDismissError)
    }
    uiState.infoMessage?.let { info ->
        MessageDialog(text = infoMessage(info), onDismiss = onDismissInfo)
    }
}

@Composable
private fun SessionSoundEffects(
    uiState: RoundUiState,
    soundPlayer: ProceduralSoundPlayer,
) {
    val activeRound = uiState.activeRound
    val summary = uiState.summary
    val soundsEnabled = uiState.settings.soundsEnabled
    val volumeLevel = uiState.settings.soundVolumeLevel

    var summarySoundPlayed by rememberSaveable { mutableStateOf(false) }
    var timeoutSoundPlayed by rememberSaveable { mutableStateOf(false) }
    var roundStartSoundPlayed by rememberSaveable { mutableStateOf(false) }
    var lastCountdownRoundKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastCountdownTickSecond by rememberSaveable { mutableStateOf<Int?>(null) }

    val countdownRoundKey = activeRound
        ?.takeIf { it.phase == RoundPhase.COUNTDOWN }
        ?.let { "${it.currentTopicNumber}:${it.phaseEndsAtMillis}" }

    LaunchedEffect(
        activeRound?.phase,
        activeRound?.phaseEndsAtMillis,
        activeRound?.currentTopic?.stableId,
        activeRound?.currentTopicNumber,
        uiState.currentTimeMillis,
        summary,
        soundsEnabled,
        volumeLevel,
    ) {
        if (summary != null) {
            timeoutSoundPlayed = false
            roundStartSoundPlayed = false
            lastCountdownRoundKey = null
            lastCountdownTickSecond = null

            if (!summarySoundPlayed) {
                soundPlayer.play(
                    effect = if (summary.completedCount > 0) {
                        SoundEffect.ROUND_SUCCESS
                    } else {
                        SoundEffect.ROUND_FAILURE
                    },
                    enabled = soundsEnabled,
                    volumeLevel = volumeLevel,
                )
                summarySoundPlayed = true
            }
            return@LaunchedEffect
        } else {
            summarySoundPlayed = false
        }

        if (activeRound == null) {
            timeoutSoundPlayed = false
            roundStartSoundPlayed = false
            lastCountdownRoundKey = null
            lastCountdownTickSecond = null
            return@LaunchedEffect
        }

        when (activeRound.phase) {
            RoundPhase.COUNTDOWN -> {
                timeoutSoundPlayed = false

                if (countdownRoundKey != lastCountdownRoundKey) {
                    lastCountdownRoundKey = countdownRoundKey
                    lastCountdownTickSecond = null
                    roundStartSoundPlayed = false
                }

                val countdownSecond = secondsRemaining(
                    endMillis = activeRound.phaseEndsAtMillis,
                    nowMillis = uiState.currentTimeMillis,
                )

                if (countdownSecond > 0 && countdownSecond != lastCountdownTickSecond) {
                    soundPlayer.play(
                        effect = SoundEffect.COUNTDOWN_TICK,
                        enabled = soundsEnabled,
                        volumeLevel = volumeLevel,
                    )
                    lastCountdownTickSecond = countdownSecond
                }
            }

            RoundPhase.TOPIC -> {
                timeoutSoundPlayed = false
                lastCountdownTickSecond = null

                if (!roundStartSoundPlayed) {
                    soundPlayer.play(
                        effect = SoundEffect.ROUND_START,
                        enabled = soundsEnabled,
                        volumeLevel = volumeLevel,
                    )
                    roundStartSoundPlayed = true
                }
            }

            RoundPhase.TIME_UP -> {
                lastCountdownTickSecond = null

                if (!timeoutSoundPlayed) {
                    soundPlayer.play(
                        effect = SoundEffect.TOPIC_TIMEOUT,
                        enabled = soundsEnabled,
                        volumeLevel = volumeLevel,
                    )
                    timeoutSoundPlayed = true
                }
            }
        }
    }
}

@Composable
private fun GameplayStatusBarEffect(enabled: Boolean) {
    val context = LocalContext.current

    DisposableEffect(context, enabled) {
        val activity = context.findActivity()
        val window = activity?.window

        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val previousBehavior = controller.systemBarsBehavior

            if (enabled) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }

            onDispose {
                controller.show(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior = previousBehavior
            }
        }
    }
}

@Composable
private fun Modifier.gameplayContentInsets(): Modifier =
    this.windowInsetsPadding(
        WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top + WindowInsetsSides.Bottom
        )
    )

@Composable
private fun ActiveRoundContent(
    round: ActiveRound,
    currentTimeMillis: Long,
    effectiveSignalMethod: SignalMethod,
    soundsEnabled: Boolean,
    soundVolumeLevel: Int,
    touchVisualFeedbackEnabled: Boolean,
    touchHapticFeedbackEnabled: Boolean,
    touchSoundFeedbackEnabled: Boolean,
    soundPlayer: ProceduralSoundPlayer,
    onSignalComplete: () -> Unit,
    onDoubleTapSignalComplete: () -> Unit,
    onSkipTopic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageCode = currentLanguageCode()
    val remainingSeconds = secondsRemaining(round.phaseEndsAtMillis, currentTimeMillis)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isShake = effectiveSignalMethod == SignalMethod.SHAKE
    val isButton = effectiveSignalMethod == SignalMethod.BUTTON

    if (isShake && round.phase == RoundPhase.TOPIC) {
        ShakeDetectorEffect(onShake = onSignalComplete)
    }

    val backgroundColor = when (round.phase) {
        RoundPhase.COUNTDOWN -> MaterialTheme.colorScheme.background
        RoundPhase.TOPIC -> MaterialTheme.colorScheme.background
        RoundPhase.TIME_UP -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    }

    Surface(
        color = backgroundColor,
        modifier = modifier.fillMaxSize(),
    ) {
        if (isLandscape) {
            LandscapeRoundLayout(
                round = round,
                languageCode = languageCode,
                remainingSeconds = remainingSeconds,
                signalMethod = effectiveSignalMethod,
                soundsEnabled = soundsEnabled,
                soundVolumeLevel = soundVolumeLevel,
                touchVisualFeedbackEnabled = touchVisualFeedbackEnabled,
                touchHapticFeedbackEnabled = touchHapticFeedbackEnabled,
                touchSoundFeedbackEnabled = touchSoundFeedbackEnabled,
                soundPlayer = soundPlayer,
                onSignalComplete = onSignalComplete,
                onDoubleTapSignalComplete = onDoubleTapSignalComplete,
                onSkipTopic = onSkipTopic,
                showButton = isButton,
            )
        } else {
            PortraitRoundLayout(
                round = round,
                languageCode = languageCode,
                remainingSeconds = remainingSeconds,
                signalMethod = effectiveSignalMethod,
                soundsEnabled = soundsEnabled,
                soundVolumeLevel = soundVolumeLevel,
                touchVisualFeedbackEnabled = touchVisualFeedbackEnabled,
                touchHapticFeedbackEnabled = touchHapticFeedbackEnabled,
                touchSoundFeedbackEnabled = touchSoundFeedbackEnabled,
                soundPlayer = soundPlayer,
                onSignalComplete = onSignalComplete,
                onDoubleTapSignalComplete = onDoubleTapSignalComplete,
                onSkipTopic = onSkipTopic,
                showButton = isButton,
            )
        }
    }
}

@Composable
private fun gameplayTextShadow(): Shadow {
    val textColor = MaterialTheme.colorScheme.onBackground
    val shadowColor = if (textColor.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.34f)
    } else {
        Color.White.copy(alpha = 0.22f)
    }

    return Shadow(
        color = shadowColor,
        offset = Offset(0f, 4f),
        blurRadius = 10f,
    )
}

@Composable
private fun PortraitRoundLayout(
    round: ActiveRound,
    languageCode: String,
    remainingSeconds: Int,
    signalMethod: SignalMethod,
    soundsEnabled: Boolean,
    soundVolumeLevel: Int,
    touchVisualFeedbackEnabled: Boolean,
    touchHapticFeedbackEnabled: Boolean,
    touchSoundFeedbackEnabled: Boolean,
    soundPlayer: ProceduralSoundPlayer,
    onSignalComplete: () -> Unit,
    onDoubleTapSignalComplete: () -> Unit,
    onSkipTopic: () -> Unit,
    showButton: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .gameplayContentInsets()
            .padding(18.dp)
    ) {
        PortraitTopStatsPanel(
            round = round,
            languageCode = languageCode,
            remainingSeconds = remainingSeconds,
        )

        RoundPhaseBody(
            round = round,
            languageCode = languageCode,
            signalMethod = signalMethod,
            soundsEnabled = soundsEnabled,
            soundVolumeLevel = soundVolumeLevel,
            touchVisualFeedbackEnabled = touchVisualFeedbackEnabled,
            touchHapticFeedbackEnabled = touchHapticFeedbackEnabled,
            touchSoundFeedbackEnabled = touchSoundFeedbackEnabled,
            soundPlayer = soundPlayer,
            onSignalComplete = onSignalComplete,
            onDoubleTapSignalComplete = onDoubleTapSignalComplete,
            onSkipTopic = onSkipTopic,
            showButton = showButton,
            isLandscape = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LandscapeRoundLayout(
    round: ActiveRound,
    languageCode: String,
    remainingSeconds: Int,
    signalMethod: SignalMethod,
    soundsEnabled: Boolean,
    soundVolumeLevel: Int,
    touchVisualFeedbackEnabled: Boolean,
    touchHapticFeedbackEnabled: Boolean,
    touchSoundFeedbackEnabled: Boolean,
    soundPlayer: ProceduralSoundPlayer,
    onSignalComplete: () -> Unit,
    onDoubleTapSignalComplete: () -> Unit,
    onSkipTopic: () -> Unit,
    showButton: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .gameplayContentInsets()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        LandscapeTopStatsBar(
            round = round,
            languageCode = languageCode,
            remainingSeconds = remainingSeconds,
        )

        RoundPhaseBody(
            round = round,
            languageCode = languageCode,
            signalMethod = signalMethod,
            soundsEnabled = soundsEnabled,
            soundVolumeLevel = soundVolumeLevel,
            touchVisualFeedbackEnabled = touchVisualFeedbackEnabled,
            touchHapticFeedbackEnabled = touchHapticFeedbackEnabled,
            touchSoundFeedbackEnabled = touchSoundFeedbackEnabled,
            soundPlayer = soundPlayer,
            onSignalComplete = onSignalComplete,
            onDoubleTapSignalComplete = onDoubleTapSignalComplete,
            onSkipTopic = onSkipTopic,
            showButton = showButton,
            isLandscape = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PortraitTopStatsPanel(
    round: ActiveRound,
    languageCode: String,
    remainingSeconds: Int,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                text = round.categoryDisplayName(languageCode),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PortraitStatCell(
                    text = stringResource(id = R.string.round_timer_label, remainingSeconds),
                    modifier = Modifier.weight(1f),
                )
                PortraitStatCell(
                    text = stringResource(id = R.string.round_topic_counter, round.currentTopicNumber, round.totalTopics),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PortraitCounterStatCell(
                    label = stringResource(id = R.string.round_completed_label),
                    value = round.completedCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                PortraitCounterStatCell(
                    label = stringResource(id = R.string.round_timed_out_label),
                    value = round.timedOutCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                PortraitCounterStatCell(
                    label = stringResource(id = R.string.round_skipped_label),
                    value = round.skippedCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PortraitStatCell(
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            val statStyle = when {
                maxWidth < 92.dp || text.length > 22 -> MaterialTheme.typography.bodySmall
                maxWidth < 124.dp || text.length > 15 -> MaterialTheme.typography.bodyMedium
                else -> MaterialTheme.typography.bodyLarge
            }
            Text(
                text = text,
                style = statStyle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PortraitCounterStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 112.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            val compact = maxWidth < 104.dp
            val labelStyle = if (compact) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyMedium
            }
            val valueStyle = if (compact) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineSmall
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.CenterVertically),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = label,
                    style = labelStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = value,
                    style = valueStyle,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LandscapeTopStatsBar(
    round: ActiveRound,
    languageCode: String,
    remainingSeconds: Int,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            CompactLandscapeStat(
                visualText = round.categoryDisplayName(languageCode),
                contentDescription = stringResource(
                    id = R.string.summary_category,
                    round.categoryDisplayName(languageCode),
                ),
                emphasized = true,
                modifier = Modifier.weight(1.65f),
            )
            CompactLandscapeStat(
                visualText = "${remainingSeconds}s",
                contentDescription = stringResource(id = R.string.round_timer_label, remainingSeconds),
                modifier = Modifier.weight(0.72f),
            )
            CompactLandscapeStat(
                visualText = "${round.currentTopicNumber}/${round.totalTopics}",
                contentDescription = stringResource(
                    id = R.string.round_topic_counter,
                    round.currentTopicNumber,
                    round.totalTopics,
                ),
                modifier = Modifier.weight(0.82f),
            )
            CompactLandscapeIconStat(
                icon = Icons.Rounded.Check,
                value = round.completedCount.toString(),
                contentDescription = stringResource(id = R.string.round_completed_counter, round.completedCount),
                modifier = Modifier.weight(0.8f),
            )
            CompactLandscapeIconStat(
                icon = Icons.Rounded.HourglassTop,
                value = round.timedOutCount.toString(),
                contentDescription = stringResource(id = R.string.round_timed_out_counter, round.timedOutCount),
                modifier = Modifier.weight(0.88f),
            )
            CompactLandscapeIconStat(
                icon = Icons.AutoMirrored.Rounded.Redo,
                value = round.skippedCount.toString(),
                contentDescription = stringResource(id = R.string.round_skipped_counter, round.skippedCount),
                modifier = Modifier.weight(0.8f),
                iconSize = 24.dp,
            )
        }
    }
}

@Composable
private fun CompactLandscapeStat(
    visualText: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Text(
            text = visualText,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompactLandscapeIconStat(
    icon: ImageVector,
    value: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(iconSize),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RoundPhaseBody(
    round: ActiveRound,
    languageCode: String,
    signalMethod: SignalMethod,
    soundsEnabled: Boolean,
    soundVolumeLevel: Int,
    touchVisualFeedbackEnabled: Boolean,
    touchHapticFeedbackEnabled: Boolean,
    touchSoundFeedbackEnabled: Boolean,
    soundPlayer: ProceduralSoundPlayer,
    onSignalComplete: () -> Unit,
    onDoubleTapSignalComplete: () -> Unit,
    onSkipTopic: () -> Unit,
    showButton: Boolean,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
) {
    when (round.phase) {
        RoundPhase.COUNTDOWN -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier.fillMaxSize(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.countdown_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            shadow = gameplayTextShadow(),
                        ),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(id = R.string.countdown_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        RoundPhase.TOPIC -> {
            TopicPhaseLayout(
                topicText = round.currentTopic.displayText(languageCode),
                signalMethod = signalMethod,
                soundsEnabled = soundsEnabled,
                soundVolumeLevel = soundVolumeLevel,
                touchVisualFeedbackEnabled = touchVisualFeedbackEnabled,
                touchHapticFeedbackEnabled = touchHapticFeedbackEnabled,
                touchSoundFeedbackEnabled = touchSoundFeedbackEnabled,
                soundPlayer = soundPlayer,
                onSignalComplete = onSignalComplete,
                onDoubleTapSignalComplete = onDoubleTapSignalComplete,
                onSkipTopic = onSkipTopic,
                showButton = showButton,
                isLandscape = isLandscape,
                modifier = modifier,
            )
        }

        RoundPhase.TIME_UP -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier.fillMaxSize(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.padding(20.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.time_up_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            shadow = gameplayTextShadow(),
                        ),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = round.currentTopic.displayText(languageCode),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            shadow = gameplayTextShadow(),
                        ),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(id = R.string.time_up_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicPhaseLayout(
    topicText: String,
    signalMethod: SignalMethod,
    soundsEnabled: Boolean,
    soundVolumeLevel: Int,
    touchVisualFeedbackEnabled: Boolean,
    touchHapticFeedbackEnabled: Boolean,
    touchSoundFeedbackEnabled: Boolean,
    soundPlayer: ProceduralSoundPlayer,
    onSignalComplete: () -> Unit,
    onDoubleTapSignalComplete: () -> Unit,
    onSkipTopic: () -> Unit,
    showButton: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val topicAreaDescription = stringResource(id = R.string.round_center_zone_label)
    val touchPulse = remember { mutableStateOf<TouchPulse?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 20.dp else 16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics { contentDescription = topicAreaDescription }
                .pointerInput(
                    signalMethod,
                    soundsEnabled,
                    soundVolumeLevel,
                    touchSoundFeedbackEnabled,
                    touchHapticFeedbackEnabled,
                    touchVisualFeedbackEnabled,
                ) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (touchVisualFeedbackEnabled) {
                                touchPulse.value = TouchPulse(
                                    token = SystemClock.elapsedRealtime(),
                                    offset = offset,
                                )
                            }
                            if (touchHapticFeedbackEnabled) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (soundsEnabled && touchSoundFeedbackEnabled) {
                                soundPlayer.play(
                                    effect = SoundEffect.SINGLE_TAP,
                                    enabled = true,
                                    volumeLevel = soundVolumeLevel,
                                )
                            }
                        },
                        onDoubleTap = { offset ->
                            if (touchVisualFeedbackEnabled) {
                                touchPulse.value = TouchPulse(
                                    token = SystemClock.elapsedRealtime(),
                                    offset = offset,
                                )
                            }
                            if (touchHapticFeedbackEnabled) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (signalMethod == SignalMethod.DOUBLE_TAP && isInCenterActivationZone(offset, size)) {
                                if (soundsEnabled && touchSoundFeedbackEnabled) {
                                    soundPlayer.play(
                                        effect = SoundEffect.DOUBLE_TAP,
                                        enabled = true,
                                        volumeLevel = soundVolumeLevel,
                                    )
                                }
                                onDoubleTapSignalComplete()
                            }
                        },
                    )
                },
        ) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight
            val horizontalPadding = if (isLandscape) 24.dp else 12.dp
            val textWidthFraction = if (isLandscape) 0.94f else 1f
            val textWidthCap = if (isLandscape) 960.dp else 760.dp
            val topicTextLayout = rememberTopicTextLayout(
                topicText = topicText,
                availableWidth = ((availableWidth * textWidthFraction).coerceAtMost(textWidthCap) - (horizontalPadding * 2))
                    .coerceAtLeast(120.dp),
                availableHeight = availableHeight,
                isLandscape = isLandscape,
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                TouchPulseLayer(
                    pulse = touchPulse.value,
                    enabled = touchVisualFeedbackEnabled,
                )
                Text(
                    text = topicTextLayout.displayText,
                    fontSize = topicTextLayout.fontSize,
                    lineHeight = topicTextLayout.lineHeight,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = topicTextLayout.maxLines,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    style = MaterialTheme.typography.displayMedium.copy(
                        shadow = gameplayTextShadow(),
                    ),
                    modifier = Modifier
                        .fillMaxWidth(textWidthFraction)
                        .widthIn(max = textWidthCap)
                        .padding(horizontal = horizontalPadding),
                )
            }
        }

        Text(
            text = when (signalMethod) {
                SignalMethod.DOUBLE_TAP -> stringResource(id = R.string.round_signal_instruction_double_tap)
                SignalMethod.SHAKE -> stringResource(id = R.string.round_signal_instruction_shake)
                SignalMethod.BUTTON -> stringResource(id = R.string.round_signal_instruction_button)
            },
            style = if (isLandscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.9f else 1f)
                .widthIn(max = if (isLandscape) 720.dp else 760.dp),
        )

        RoundTopicActionButtons(
            showCompletedButton = showButton,
            isLandscape = isLandscape,
            onSignalComplete = onSignalComplete,
            onSkipTopic = onSkipTopic,
        )
    }
}

@Composable
private fun RoundTopicActionButtons(
    showCompletedButton: Boolean,
    isLandscape: Boolean,
    onSignalComplete: () -> Unit,
    onSkipTopic: () -> Unit,
) {
    val completedButtonDescription = stringResource(id = R.string.accessibility_completed_button)
    val skipButtonDescription = stringResource(id = R.string.accessibility_skip_button)
    val feedback = io.github.verbus.ui.feedback.rememberUiFeedbackController()

    val actionButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )

    if (showCompletedButton) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = if (isLandscape) {
                Modifier
                    .fillMaxWidth(0.8f)
                    .widthIn(max = 560.dp)
            } else {
                Modifier.fillMaxWidth()
            },
        ) {
            Button(
                onClick = { feedback.onUiInteraction(playSound = false); onSignalComplete() },
                colors = actionButtonColors,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 58.dp)
                    .semantics { contentDescription = completedButtonDescription },
            ) {
                Text(
                    text = stringResource(id = R.string.action_completed),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Button(
                onClick = { feedback.onUiInteraction(playSound = false); onSkipTopic() },
                colors = actionButtonColors,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 58.dp)
                    .semantics { contentDescription = skipButtonDescription },
            ) {
                Text(
                    text = stringResource(id = R.string.action_skip),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    } else {
        Button(
            onClick = { feedback.onUiInteraction(playSound = false); onSkipTopic() },
            colors = actionButtonColors,
            modifier = if (isLandscape) {
                Modifier
                    .fillMaxWidth(0.44f)
                    .widthIn(max = 340.dp)
                    .heightIn(min = 58.dp)
                    .semantics { contentDescription = skipButtonDescription }
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .semantics { contentDescription = skipButtonDescription }
            },
        ) {
            Text(
                text = stringResource(id = R.string.action_skip),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

private fun isInCenterActivationZone(offset: Offset, size: androidx.compose.ui.unit.IntSize): Boolean {
    val centerRect = RectF(
        size.width * 0.2f,
        size.height * 0.2f,
        size.width * 0.8f,
        size.height * 0.8f,
    )
    return centerRect.contains(offset.x, offset.y)
}

private enum class TopicActionOverlay {
    COMPLETED,
    SKIPPED,
}

private data class TouchPulse(
    val token: Long,
    val offset: Offset,
)

@Composable
private fun SummaryContent(
    summary: RoundSummary,
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageCode = currentLanguageCode()
    val categoryText = summary.categoryDisplayName(languageCode)
    val modeText = summaryModeDisplayName(summary)
    val primaryButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
    val secondaryButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onBackground,
    )

    Surface(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .gameplayContentInsets(),
        ) {
            val isLandscape = maxWidth > maxHeight

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (isLandscape) 16.dp else 20.dp,
                        vertical = if (isLandscape) 16.dp else 24.dp,
                    ),
            ) {
                if (isLandscape) {
                    LandscapeSummaryLayout(
                        summary = summary,
                        categoryText = categoryText,
                        modeText = modeText,
                        primaryButtonColors = primaryButtonColors,
                        secondaryButtonColors = secondaryButtonColors,
                        onPlayAgain = onPlayAgain,
                        onBackToMenu = onBackToMenu,
                    )
                } else {
                    PortraitSummaryLayout(
                        summary = summary,
                        categoryText = categoryText,
                        modeText = modeText,
                        primaryButtonColors = primaryButtonColors,
                        secondaryButtonColors = secondaryButtonColors,
                        onPlayAgain = onPlayAgain,
                        onBackToMenu = onBackToMenu,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortraitSummaryLayout(
    summary: RoundSummary,
    categoryText: String,
    modeText: String,
    primaryButtonColors: androidx.compose.material3.ButtonColors,
    secondaryButtonColors: androidx.compose.material3.ButtonColors,
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
    ) {
        Text(
            text = stringResource(id = R.string.summary_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                shadow = gameplayTextShadow(),
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SummaryInfoCard(
                label = stringResource(id = R.string.summary_label_category),
                value = categoryText,
                modifier = Modifier.weight(1f),
            )
            SummaryInfoCard(
                label = stringResource(id = R.string.summary_label_mode),
                value = modeText,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SummaryStatCard(
                label = stringResource(id = R.string.action_completed),
                value = summary.completedCount.toString(),
                modifier = Modifier.weight(1f),
            )
            SummaryStatCard(
                label = stringResource(id = R.string.summary_label_timed_out),
                value = summary.timedOutCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SummaryStatCard(
                label = stringResource(id = R.string.action_skip),
                value = summary.skippedCount.toString(),
                modifier = Modifier.weight(1f),
            )
            SummaryStatCard(
                label = stringResource(id = R.string.summary_label_total),
                value = summary.totalTopics.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        SummaryActionButtons(
            primaryButtonColors = primaryButtonColors,
            secondaryButtonColors = secondaryButtonColors,
            onPlayAgain = onPlayAgain,
            onBackToMenu = onBackToMenu,
            isLandscape = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LandscapeSummaryLayout(
    summary: RoundSummary,
    categoryText: String,
    modeText: String,
    primaryButtonColors: androidx.compose.material3.ButtonColors,
    secondaryButtonColors: androidx.compose.material3.ButtonColors,
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 980.dp),
    ) {
        Text(
            text = stringResource(id = R.string.summary_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                shadow = gameplayTextShadow(),
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1.55f),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SummaryInfoCard(
                        label = stringResource(id = R.string.summary_label_category),
                        value = categoryText,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryInfoCard(
                        label = stringResource(id = R.string.summary_label_mode),
                        value = modeText,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SummaryStatCard(
                        label = stringResource(id = R.string.action_completed),
                        value = summary.completedCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryStatCard(
                        label = stringResource(id = R.string.summary_label_timed_out),
                        value = summary.timedOutCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryStatCard(
                        label = stringResource(id = R.string.action_skip),
                        value = summary.skippedCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }

                SummaryStatCard(
                    label = stringResource(id = R.string.summary_label_total),
                    value = summary.totalTopics.toString(),
                    modifier = Modifier.fillMaxWidth(),
                    emphasized = true,
                )
            }

            SummaryActionButtons(
                primaryButtonColors = primaryButtonColors,
                secondaryButtonColors = secondaryButtonColors,
                onPlayAgain = onPlayAgain,
                onBackToMenu = onBackToMenu,
                isLandscape = true,
                modifier = Modifier.weight(0.9f),
            )
        }
    }
}

@Composable
private fun SummaryInfoCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = summaryCardContainerColor(emphasized = false),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = summaryCardBorderColor(emphasized = false),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SummaryStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(if (emphasized) 24.dp else 22.dp),
        colors = CardDefaults.cardColors(
            containerColor = summaryCardContainerColor(emphasized = emphasized),
        ),
        border = BorderStroke(
            width = if (emphasized) 1.5.dp else 1.dp,
            color = summaryCardBorderColor(emphasized = emphasized),
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (emphasized) 4.dp else 3.dp,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (emphasized) 16.dp else 14.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = value,
                style = if (emphasized) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun summaryCardContainerColor(emphasized: Boolean): Color {
    val background = MaterialTheme.colorScheme.background
    val foreground = MaterialTheme.colorScheme.onBackground
    return lerp(
        start = background,
        stop = foreground,
        fraction = if (emphasized) 0.16f else 0.10f,
    )
}

@Composable
private fun summaryCardBorderColor(emphasized: Boolean): Color {
    val background = MaterialTheme.colorScheme.background
    val foreground = MaterialTheme.colorScheme.onBackground
    return lerp(
        start = background,
        stop = foreground,
        fraction = if (emphasized) 0.28f else 0.18f,
    )
}

@Composable
private fun SummaryActionButtons(
    primaryButtonColors: androidx.compose.material3.ButtonColors,
    secondaryButtonColors: androidx.compose.material3.ButtonColors,
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val feedback = io.github.verbus.ui.feedback.rememberUiFeedbackController()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        Button(
            onClick = { feedback.onUiInteraction(); onPlayAgain() },
            colors = primaryButtonColors,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(
                text = stringResource(id = R.string.action_play_again),
                style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }

        Button(
            onClick = { feedback.onUiInteraction(); onBackToMenu() },
            colors = secondaryButtonColors,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(
                text = stringResource(id = R.string.action_back_to_menu),
                style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun summaryModeDisplayName(summary: RoundSummary): String = when (summary.modeId) {
    "storytelling" -> stringResource(id = R.string.mode_storytelling_name)
    else -> stringResource(id = R.string.mode_storytelling_name)
}

@Composable
private fun ShakeDetectorEffect(onShake: () -> Unit) {
    val context = LocalContext.current
    val currentOnShake by rememberUpdatedState(newValue = onShake)
    val sensorManager = remember(context) { context.getSystemService(SensorManager::class.java) }

    DisposableEffect(sensorManager) {
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || accelerometer == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                private var lastTriggerElapsedRealtime = 0L
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.values.size < 3) return
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
                    val now = SystemClock.elapsedRealtime()
                    if (gForce > SHAKE_THRESHOLD_G && now - lastTriggerElapsedRealtime >= SHAKE_DEBOUNCE_MILLIS) {
                        lastTriggerElapsedRealtime = now
                        currentOnShake()
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }
}

@Composable
private fun KeepScreenAwakeEffect(enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
private fun TopicActionHapticsEffect(
    actionOverlay: TopicActionOverlay?,
    enabled: Boolean,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    LaunchedEffect(actionOverlay, enabled) {
        if (enabled && actionOverlay != null) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

@Composable
private fun TouchPulseLayer(
    pulse: TouchPulse?,
    enabled: Boolean,
) {
    if (!enabled || pulse == null) return

    val progress = remember(pulse.token) { Animatable(0f) }

    LaunchedEffect(pulse.token) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 320),
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val animatedProgress = progress.value
        val maxRadius = size.minDimension * 0.22f
        drawCircle(
            color = primaryColor.copy(alpha = (1f - animatedProgress) * 0.28f),
            radius = maxRadius * (0.35f + animatedProgress),
            center = pulse.offset,
        )
        drawCircle(
            color = primaryColor.copy(alpha = (1f - animatedProgress) * 0.16f),
            radius = maxRadius * (0.7f + animatedProgress * 0.55f),
            center = pulse.offset,
        )
    }
}

@Composable
private fun TopicActionOverlayView(actionOverlay: TopicActionOverlay) {
    val gradientColors = when (actionOverlay) {
        TopicActionOverlay.COMPLETED -> listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
        )
        TopicActionOverlay.SKIPPED -> listOf(
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.84f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
        )
    }
    val symbol = if (actionOverlay == TopicActionOverlay.COMPLETED) "✓" else "↷"
    val label = if (actionOverlay == TopicActionOverlay.COMPLETED) {
        stringResource(id = R.string.completed_overlay_title)
    } else {
        stringResource(id = R.string.skipped_overlay_title)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = symbol,
                fontSize = 110.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                style = MaterialTheme.typography.displayLarge.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.30f),
                        offset = Offset(0f, 4f),
                        blurRadius = 10f,
                    ),
                ),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.headlineLarge.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.30f),
                        offset = Offset(0f, 4f),
                        blurRadius = 10f,
                    ),
                ),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun MessageDialog(text: String, onDismiss: () -> Unit) {
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
private fun errorMessage(error: PartyGameError): String = when (error) {
    PartyGameError.CATEGORY_UNAVAILABLE -> stringResource(id = R.string.error_category_unavailable)
    PartyGameError.NO_TOPICS_AVAILABLE -> stringResource(id = R.string.error_no_topics_available)
    PartyGameError.MISSING_CONTENT_INDEX -> stringResource(id = R.string.error_missing_content_index)
    PartyGameError.ROUND_RESTORE_FAILED -> stringResource(id = R.string.error_round_restore_failed)
    PartyGameError.SENSOR_UNAVAILABLE -> stringResource(id = R.string.error_sensor_unavailable)
    PartyGameError.ROUND_NOT_ACTIVE -> stringResource(id = R.string.error_round_not_active)
    PartyGameError.GENERIC -> stringResource(id = R.string.error_generic)
}

@Composable
private fun infoMessage(error: PartyGameError): String = when (error) {
    PartyGameError.SENSOR_UNAVAILABLE -> stringResource(id = R.string.info_shake_fallback)
    PartyGameError.CATEGORY_UNAVAILABLE,
    PartyGameError.NO_TOPICS_AVAILABLE,
    PartyGameError.MISSING_CONTENT_INDEX,
    PartyGameError.ROUND_RESTORE_FAILED,
    PartyGameError.ROUND_NOT_ACTIVE,
    PartyGameError.GENERIC,
    -> errorMessage(error)
}

private fun secondsRemaining(endMillis: Long, nowMillis: Long): Int =
    ceil(((endMillis - nowMillis).coerceAtLeast(0L)) / 1_000.0).toInt()

private const val SHAKE_DEBOUNCE_MILLIS = 1_200L
private const val SHAKE_THRESHOLD_G = 2.2


private data class TopicTextLayout(
    val displayText: String,
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val maxLines: Int,
)

@Composable
private fun rememberTopicTextLayout(
    topicText: String,
    availableWidth: androidx.compose.ui.unit.Dp,
    availableHeight: androidx.compose.ui.unit.Dp,
    isLandscape: Boolean,
): TopicTextLayout {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.displayMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        shadow = gameplayTextShadow(),
    )

    return remember(topicText, availableWidth, availableHeight, isLandscape, density, baseStyle) {
        calculateTopicTextLayout(
            topicText = topicText,
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            isLandscape = isLandscape,
            density = density,
            textMeasurer = textMeasurer,
            baseStyle = baseStyle,
        )
    }
}

private fun calculateTopicTextLayout(
    topicText: String,
    availableWidth: androidx.compose.ui.unit.Dp,
    availableHeight: androidx.compose.ui.unit.Dp,
    isLandscape: Boolean,
    density: Density,
    textMeasurer: TextMeasurer,
    baseStyle: TextStyle,
): TopicTextLayout {
    val normalizedTopicText = topicText
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifEmpty { topicText.trim() }

    if (normalizedTopicText.isEmpty()) {
        return TopicTextLayout(
            displayText = topicText,
            fontSize = 48.sp,
            lineHeight = 56.sp,
            maxLines = 1,
        )
    }

    val maxFontSize = when {
        isLandscape && availableWidth >= 900.dp -> 88f
        isLandscape -> 76f
        availableWidth > availableHeight -> 48f
        else -> 64f
    }
    val maxLineHeight = when {
        isLandscape && availableWidth >= 900.dp -> 98f
        isLandscape -> 86f
        availableWidth > availableHeight -> 58f
        else -> 74f
    }
    val lineHeightMultiplier = maxLineHeight / maxFontSize
    val minFontSize = if (isLandscape) 22f else 24f
    val maxLines = if (isLandscape) 5 else 6
    val widthPx = with(density) { availableWidth.roundToPx() }
    val heightPx = with(density) { availableHeight.roundToPx() }
    val words = normalizedTopicText.split(' ').filter { it.isNotBlank() }

    var candidateFontSize = maxFontSize
    while (candidateFontSize >= minFontSize) {
        val lineHeight = candidateFontSize * lineHeightMultiplier
        val candidateStyle = baseStyle.copy(
            fontSize = candidateFontSize.sp,
            lineHeight = lineHeight.sp,
        )
        val lines = buildTopicLinesWithoutWordBreaks(
            words = words,
            maxWidthPx = widthPx,
            textMeasurer = textMeasurer,
            textStyle = candidateStyle,
        )
        if (lines != null && lines.size <= maxLines) {
            val layout = measureTopicTextLayout(
                text = lines.joinToString(separator = "\n"),
                maxWidthPx = widthPx,
                maxHeightPx = heightPx,
                textMeasurer = textMeasurer,
                textStyle = candidateStyle,
                maxLines = lines.size,
            )
            if (layout != null) {
                return TopicTextLayout(
                    displayText = lines.joinToString(separator = "\n"),
                    fontSize = candidateFontSize.sp,
                    lineHeight = lineHeight.sp,
                    maxLines = lines.size,
                )
            }
        }
        candidateFontSize -= if (candidateFontSize > 48f) 2f else 1f
    }

    val fallbackFontSize = minFontSize.sp
    val fallbackLineHeight = (minFontSize * lineHeightMultiplier).sp
    return TopicTextLayout(
        displayText = normalizedTopicText,
        fontSize = fallbackFontSize,
        lineHeight = fallbackLineHeight,
        maxLines = 1,
    )
}

private fun buildTopicLinesWithoutWordBreaks(
    words: List<String>,
    maxWidthPx: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
): List<String>? {
    if (words.isEmpty()) return emptyList()

    val lines = mutableListOf<String>()
    var currentLine = words.first()

    if (measureSingleTopicLineWidth(currentLine, textMeasurer, textStyle) > maxWidthPx) {
        return null
    }

    for (word in words.drop(1)) {
        val candidateLine = "$currentLine $word"
        if (measureSingleTopicLineWidth(candidateLine, textMeasurer, textStyle) <= maxWidthPx) {
            currentLine = candidateLine
        } else {
            lines += currentLine
            if (measureSingleTopicLineWidth(word, textMeasurer, textStyle) > maxWidthPx) {
                return null
            }
            currentLine = word
        }
    }

    lines += currentLine
    return lines
}

private fun measureSingleTopicLineWidth(
    text: String,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
): Int = textMeasurer.measure(
    text = AnnotatedString(text),
    style = textStyle,
    softWrap = false,
    maxLines = 1,
    overflow = TextOverflow.Clip,
).size.width

private fun measureTopicTextLayout(
    text: String,
    maxWidthPx: Int,
    maxHeightPx: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    maxLines: Int,
): TextLayoutResult? {
    val layout = textMeasurer.measure(
        text = AnnotatedString(text),
        style = textStyle,
        softWrap = false,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        constraints = Constraints(maxWidth = maxWidthPx),
    )
    return layout.takeIf { it.size.height <= maxHeightPx }
}
