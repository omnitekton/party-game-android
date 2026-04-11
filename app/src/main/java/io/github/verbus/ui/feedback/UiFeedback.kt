package io.github.verbus.ui.feedback

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.platform.LocalContext
import io.github.verbus.app.ProceduralSoundPlayer
import io.github.verbus.domain.model.AppSettings
import io.github.verbus.domain.model.SoundEffect
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode

private val LocalUiFeedbackController = compositionLocalOf<UiFeedbackController> { NoOpUiFeedbackController }

@Composable
fun ProvideUiFeedback(
    settings: AppSettings,
    soundPlayer: ProceduralSoundPlayer,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember(
        context,
        soundPlayer,
        settings.soundsEnabled,
        settings.soundVolumeLevel,
        settings.touchHapticFeedbackEnabled,
        settings.touchSoundFeedbackEnabled,
    ) {
        DefaultUiFeedbackController(
            context = context,
            soundPlayer = soundPlayer,
            soundsEnabled = settings.soundsEnabled,
            soundVolumeLevel = settings.soundVolumeLevel,
            touchHapticFeedbackEnabled = settings.touchHapticFeedbackEnabled,
            touchSoundFeedbackEnabled = settings.touchSoundFeedbackEnabled,
        )
    }

    val indication = if (settings.touchVisualFeedbackEnabled) {
        ripple(color = MaterialTheme.colorScheme.primary)
    } else {
        NoIndication
    }

    CompositionLocalProvider(
        LocalUiFeedbackController provides controller,
        LocalIndication provides indication,
        content = content,
    )
}

@Composable
fun rememberUiFeedbackController(): UiFeedbackController = LocalUiFeedbackController.current

@Stable
interface UiFeedbackController {
    fun onUiInteraction()
}

private object NoOpUiFeedbackController : UiFeedbackController {
    override fun onUiInteraction() = Unit
}

private class DefaultUiFeedbackController(
    private val context: Context,
    private val soundPlayer: ProceduralSoundPlayer,
    private val soundsEnabled: Boolean,
    private val soundVolumeLevel: Int,
    private val touchHapticFeedbackEnabled: Boolean,
    private val touchSoundFeedbackEnabled: Boolean,
) : UiFeedbackController {
    override fun onUiInteraction() {
        if (touchHapticFeedbackEnabled) {
            vibrateTap(context)
        }
        if (soundsEnabled && touchSoundFeedbackEnabled) {
            soundPlayer.play(
                effect = SoundEffect.BUTTON_PRESS,
                enabled = true,
                volumeLevel = soundVolumeLevel,
            )
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateTap(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (!vibrator.hasVibrator()) return

        try {
            vibrator.vibrate(VibrationEffect.createOneShot(24L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: RuntimeException) {
            // Ignore hardware/service failures and keep the interaction functional.
        }
    }
}

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return NoIndicationNode()
    }

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
}

private class NoIndicationNode : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        drawContent()
    }
}
