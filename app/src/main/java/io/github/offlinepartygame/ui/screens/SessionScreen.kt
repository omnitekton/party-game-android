package io.github.offlinepartygame.ui.screens

import android.content.res.Configuration
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.offlinepartygame.R
import io.github.offlinepartygame.app.ProceduralSoundPlayer
import io.github.offlinepartygame.domain.model.ActiveRound
import io.github.offlinepartygame.domain.model.PartyGameError
import io.github.offlinepartygame.domain.model.RoundPhase
import io.github.offlinepartygame.domain.model.RoundSummary
import io.github.offlinepartygame.domain.model.SignalMethod
import io.github.offlinepartygame.domain.model.SoundEffect
import io.github.offlinepartygame.ui.currentLanguageCode
import io.github.offlinepartygame.ui.findActivity
import io.github.offlinepartygame.ui.viewmodel.RoundUiState
import kotlin.math.ceil
import kotlin.math.sqrt
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun SessionScreen(
    uiState: RoundUiState,
    soundPlayer: ProceduralSoundPlayer,
    onComplete: () -> Unit,
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
    CompletionHapticsEffect(
        triggerKey = uiState.completionFeedbackToken,
        enabled = uiState.settings.hapticFeedbackEnabled,
    )
    SessionSoundEffects(uiState = uiState, soundPlayer = soundPlayer)

    var isProcessingCompletion by remember { mutableStateOf(false) }
    var showCompletionOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.activeRound?.currentTopic?.stableId, uiState.summary) {
        if (uiState.activeRound != null || uiState.summary != null) {
            isProcessingCompletion = false
            showCompletionOverlay = false
        }
    }

    val signalComplete: () -> Unit = {
        if (!isProcessingCompletion) {
            isProcessingCompletion = true
            soundPlayer.play(
                effect = SoundEffect.TOPIC_SUCCESS,
                enabled = uiState.settings.soundsEnabled,
                volumeLevel = uiState.settings.soundVolumeLevel,
            )
            showCompletionOverlay = true
        }
    }

    LaunchedEffect(showCompletionOverlay) {
        if (showCompletionOverlay) {
            kotlinx.coroutines.delay(650)
            onComplete()
            showCompletionOverlay = false
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
                    soundPlayer = soundPlayer,
                    onSignalComplete = signalComplete,
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

        if (showCompletionOverlay) {
            CompletionOverlay()
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
    soundPlayer: ProceduralSoundPlayer,
    onSignalComplete: () -> Unit,
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
                soundPlayer = soundPlayer,
                onSignalComplete = onSignalComplete,
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
                soundPlayer = soundPlayer,
                onSignalComplete = onSignalComplete,
                showButton = isButton,
            )
        }
    }
}

@Composable
private fun PortraitRoundLayout(
    round: ActiveRound,
    languageCode: String,
    remainingSeconds: Int,
    signalMethod: SignalMethod,
    soundsEnabled: Boolean,
    soundVolumeLevel: Int,
    soundPlayer: ProceduralSoundPlayer,
    onSignalComplete: () -> Unit,
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
            soundPlayer = soundPlayer,
            onSignalComplete = onSignalComplete,
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
    soundPlayer: ProceduralSoundPlayer,
    onSignalComplete: () -> Unit,
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
            soundPlayer = soundPlayer,
            onSignalComplete = onSignalComplete,
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
                PortraitStatCell(
                    text = stringResource(id = R.string.round_completed_counter, round.completedCount),
                    modifier = Modifier.weight(1f),
                )
                PortraitStatCell(
                    text = stringResource(id = R.string.round_timed_out_counter, round.timedOutCount),
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
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
                contentDescription = stringResource(id = R.string.summary_category, round.categoryDisplayName(languageCode)),
                emphasized = true,
                modifier = Modifier.weight(1.9f),
            )
            CompactLandscapeStat(
                visualText = "${remainingSeconds}s",
                contentDescription = stringResource(id = R.string.round_timer_label, remainingSeconds),
                modifier = Modifier.weight(0.9f),
            )
            CompactLandscapeStat(
                visualText = "${round.currentTopicNumber}/${round.totalTopics}",
                contentDescription = stringResource(id = R.string.round_topic_counter, round.currentTopicNumber, round.totalTopics),
                modifier = Modifier.weight(0.95f),
            )
            CompactLandscapeStat(
                visualText = "✓ ${round.completedCount}",
                contentDescription = stringResource(id = R.string.round_completed_counter, round.completedCount),
                modifier = Modifier.weight(0.85f),
            )
            CompactLandscapeStat(
                visualText = "⌛ ${round.timedOutCount}",
                contentDescription = stringResource(id = R.string.round_timed_out_counter, round.timedOutCount),
                modifier = Modifier.weight(0.95f),
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
private fun RoundPhaseBody(
    round: ActiveRound,
    languageCode: String,
    signalMethod: SignalMethod,
    soundsEnabled: Boolean,
    soundVolumeLevel: Int,
    soundPlayer: ProceduralSoundPlayer,
    onSignalComplete: () -> Unit,
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
                        style = MaterialTheme.typography.headlineLarge,
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
                soundPlayer = soundPlayer,
                onSignalComplete = onSignalComplete,
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
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = round.currentTopic.displayText(languageCode),
                        style = MaterialTheme.typography.headlineLarge,
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
    soundPlayer: ProceduralSoundPlayer,
    onSignalComplete: () -> Unit,
    showButton: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val topicAreaDescription = stringResource(id = R.string.round_center_zone_label)
    val singleTapEnabled = rememberUpdatedState(soundsEnabled)
    val singleTapVolume = rememberUpdatedState(soundVolumeLevel)

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
                .pointerInput(signalMethod, soundsEnabled, soundVolumeLevel) {
                    detectTapGestures(
                        onTap = {
                            soundPlayer.play(
                                effect = SoundEffect.SINGLE_TAP,
                                enabled = singleTapEnabled.value,
                                volumeLevel = singleTapVolume.value,
                            )
                        },
                        onDoubleTap = { offset ->
                            if (signalMethod == SignalMethod.DOUBLE_TAP && isInCenterActivationZone(offset, size)) {
                                soundPlayer.play(
                                    effect = SoundEffect.DOUBLE_TAP,
                                    enabled = singleTapEnabled.value,
                                    volumeLevel = singleTapVolume.value,
                                )
                                onSignalComplete()
                            }
                        },
                    )
                },
        ) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight
            val topicFontSize = when {
                isLandscape && availableWidth >= 900.dp -> 82.sp
                isLandscape -> 70.sp
                availableWidth > availableHeight -> 44.sp
                else -> 58.sp
            }
            val topicLineHeight = when {
                isLandscape && availableWidth >= 900.dp -> 92.sp
                isLandscape -> 80.sp
                availableWidth > availableHeight -> 52.sp
                else -> 68.sp
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    text = topicText,
                    fontSize = topicFontSize,
                    lineHeight = topicLineHeight,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth(if (isLandscape) 0.9f else 1f)
                        .widthIn(max = if (isLandscape) 960.dp else 760.dp)
                        .padding(horizontal = if (isLandscape) 24.dp else 12.dp),
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

        if (showButton) {
            val completedButtonDescription = stringResource(id = R.string.accessibility_completed_button)
            Button(
                onClick = onSignalComplete,
                modifier = if (isLandscape) {
                    Modifier
                        .fillMaxWidth(0.56f)
                        .widthIn(max = 420.dp)
                        .heightIn(min = 58.dp)
                        .semantics { contentDescription = completedButtonDescription }
                } else {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .semantics { contentDescription = completedButtonDescription }
                },
            ) {
                Text(
                    text = stringResource(id = R.string.action_completed),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
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

@Composable
private fun SummaryContent(
    summary: RoundSummary,
    onPlayAgain: () -> Unit,
    onBackToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val languageCode = currentLanguageCode()
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(id = R.string.summary_title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Card(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(20.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.summary_category, summary.categoryDisplayName(languageCode)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(id = R.string.summary_mode, stringResource(id = R.string.mode_storytelling_name)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(id = R.string.summary_completed, summary.completedCount),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(id = R.string.summary_timed_out, summary.timedOutCount),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(id = R.string.summary_total, summary.totalTopics),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(id = R.string.action_play_again), style = MaterialTheme.typography.titleLarge)
            }
            TextButton(onClick = onBackToMenu, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(id = R.string.action_back_to_menu), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
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
private fun CompletionHapticsEffect(triggerKey: Int, enabled: Boolean) {
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(triggerKey, enabled) {
        if (enabled && triggerKey > 0) {
            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }
}

@Composable
private fun SessionSoundEffects(
    uiState: RoundUiState,
    soundPlayer: ProceduralSoundPlayer,
) {
    val activeRound = uiState.activeRound
    LaunchedEffect(activeRound?.phase, activeRound?.phaseStartedAtMillis) {
        if (activeRound?.phase == RoundPhase.TIME_UP) {
            soundPlayer.play(
                effect = SoundEffect.TOPIC_TIMEOUT,
                enabled = uiState.settings.soundsEnabled,
                volumeLevel = uiState.settings.soundVolumeLevel,
            )
        }
    }
    LaunchedEffect(uiState.summary) {
        val summary = uiState.summary ?: return@LaunchedEffect
        soundPlayer.play(
            effect = if (summary.completedCount >= summary.timedOutCount) SoundEffect.ROUND_SUCCESS else SoundEffect.ROUND_FAILURE,
            enabled = uiState.settings.soundsEnabled,
            volumeLevel = uiState.settings.soundVolumeLevel,
        )
    }
}

@Composable
private fun CompletionOverlay() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xCC48C774), Color(0xCCFFD166)),
                ),
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "✓", fontSize = 110.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(
                text = stringResource(id = R.string.completed_overlay_title),
                style = MaterialTheme.typography.headlineLarge,
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
