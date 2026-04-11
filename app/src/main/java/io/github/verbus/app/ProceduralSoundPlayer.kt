package io.github.verbus.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.util.Log
import io.github.verbus.domain.model.AppSettings
import io.github.verbus.domain.model.SoundEffect
import io.github.verbus.domain.model.SoundSetOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import io.github.verbus.R

class ProceduralSoundPlayer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val assets = appContext.assets
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val toneFallbackMutex = Mutex()
    private val loadLock = Any()
    private val cacheRoot = File(appContext.cacheDir, "soundsets")

    private val soundPool: SoundPool = SoundPool.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setMaxStreams(16)
        .build()

    private val catalog: Map<String, Map<SoundEffect, AssetSoundFile>> = buildCatalog()
    private val options: List<SoundSetOption> = buildList {
        add(
            SoundSetOption(
                BUILT_IN_SOUND_SET_ID,
                appContext.getString(R.string.options_sound_set_builtin),
            ),
        )
        addAll(
            catalog.keys.sorted().map { setId ->
                SoundSetOption(
                    id = setId,
                    displayName = prettifySoundSetName(setId),
                )
            },
        )
    }

    private val pendingSoundIds = ConcurrentHashMap<String, Int>()
    private val readySoundIds = ConcurrentHashMap<String, Int>()
    private val soundIdToKey = ConcurrentHashMap<Int, String>()

    @Volatile
    private var activeSoundSetId: String = normalizeSelectedSoundSetId(AppSettings.DEFAULT_SOUND_SET_ID)

    init {
        cacheRoot.mkdirs()
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            val key = soundIdToKey[soundId] ?: return@setOnLoadCompleteListener
            pendingSoundIds.remove(key)
            if (status == 0) {
                readySoundIds[key] = soundId
            } else {
                readySoundIds.remove(key)
            }
        }
        scope.launch {
            preloadBuiltInProceduralSet()
            if (activeSoundSetId != BUILT_IN_SOUND_SET_ID) {
                prepareSelectedSet(activeSoundSetId)
            }
        }
    }

    fun availableSoundSets(): List<SoundSetOption> = options

    fun prepareSelectedSet(setId: String) {
        val normalizedId = normalizeSelectedSoundSetId(setId)
        activeSoundSetId = normalizedId

        scope.launch {
            if (normalizedId == BUILT_IN_SOUND_SET_ID) {
                preloadBuiltInProceduralSet()
                return@launch
            }

            val assetMap = catalog[normalizedId]
            if (assetMap.isNullOrEmpty()) {
                Log.w(TAG, "Requested sound set '$normalizedId' is unavailable. Falling back to built-in procedural sounds.")
                activeSoundSetId = BUILT_IN_SOUND_SET_ID
                preloadBuiltInProceduralSet()
                return@launch
            }

            assetMap.forEach { (effect, assetFile) ->
                if (!ensureAssetSoundLoaded(normalizedId, effect, assetFile)) {
                    ensureProceduralSoundLoaded(effect)
                }
            }
        }
    }

    fun play(effect: SoundEffect, enabled: Boolean, volumeLevel: Int) {
        if (!enabled) return

        val selectedSetId = activeSoundSetId
        val volume = (volumeLevel.coerceIn(1, 10) / 10f).coerceIn(0.1f, 1f)
        val selectedKey = soundKey(selectedSetId, effect)
        val proceduralKey = soundKey(BUILT_IN_SOUND_SET_ID, effect)

        playLoadedSound(selectedKey, volume)?.let { return }

        if (selectedSetId == BUILT_IN_SOUND_SET_ID) {
            if (pendingSoundIds[selectedKey] == null) {
                scope.launch { ensureProceduralSoundLoaded(effect) }
            }
        } else {
            if (pendingSoundIds[selectedKey] == null) {
                catalog[selectedSetId]?.get(effect)?.let { assetFile ->
                    scope.launch { ensureAssetSoundLoaded(selectedSetId, effect, assetFile) }
                }
            }

            playLoadedSound(proceduralKey, volume)?.let { return }
            if (pendingSoundIds[proceduralKey] == null) {
                scope.launch { ensureProceduralSoundLoaded(effect) }
            }
        }

        playToneFallback(effect = effect, volumeLevel = volumeLevel)
    }

    private fun playLoadedSound(key: String, volume: Float): Unit? {
        val soundId = readySoundIds[key] ?: return null
        return try {
            val streamId = soundPool.play(soundId, volume, volume, 1, 0, 1f)
            if (streamId != 0) Unit else null
        } catch (_: RuntimeException) {
            readySoundIds.remove(key)
            null
        }
    }

    private fun preloadBuiltInProceduralSet() {
        SoundEffect.entries.forEach(::ensureProceduralSoundLoaded)
    }

    private fun ensureProceduralSoundLoaded(effect: SoundEffect): Boolean {
        val key = soundKey(BUILT_IN_SOUND_SET_ID, effect)
        val cachedFile = buildProceduralCacheFile(effect) ?: return false
        return ensureSoundFileLoaded(key = key, file = cachedFile)
    }

    private fun ensureAssetSoundLoaded(
        setId: String,
        effect: SoundEffect,
        assetFile: AssetSoundFile,
    ): Boolean {
        val key = soundKey(setId, effect)
        val cachedFile = copyAssetToCache(setId = setId, assetFile = assetFile) ?: return false
        return ensureSoundFileLoaded(key = key, file = cachedFile)
    }

    private fun ensureSoundFileLoaded(
        key: String,
        file: File,
    ): Boolean = synchronized(loadLock) {
        if (readySoundIds.containsKey(key) || pendingSoundIds.containsKey(key)) return@synchronized true

        val soundId = try {
            soundPool.load(file.absolutePath, 1)
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Failed to queue sound '$key' from ${file.absolutePath}.", exception)
            return@synchronized false
        }

        pendingSoundIds[key] = soundId
        soundIdToKey[soundId] = key
        true
    }

    private fun copyAssetToCache(setId: String, assetFile: AssetSoundFile): File? {
        val setCacheDir = File(cacheRoot, setId).apply { mkdirs() }
        val outputFile = File(setCacheDir, assetFile.fileName)
        if (outputFile.exists() && outputFile.length() > 0L) {
            return outputFile
        }

        return try {
            assets.open(assetFile.assetPath).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outputFile
        } catch (exception: Exception) {
            outputFile.delete()
            Log.w(TAG, "Failed to cache sound asset '${assetFile.assetPath}'. Falling back to built-in sound.", exception)
            null
        }
    }

    private fun buildProceduralCacheFile(effect: SoundEffect): File? = synchronized(loadLock) {
        val proceduralDir = File(cacheRoot, BUILT_IN_SOUND_SET_ID).apply { mkdirs() }
        val outputFile = File(proceduralDir, "${effect.name.lowercase(Locale.ROOT)}.wav")
        if (outputFile.exists() && outputFile.length() > 0L) {
            return@synchronized outputFile
        }

        return@synchronized try {
            val spec = builtInSoundSpec(effect)
            outputFile.writeBytes(renderWav(spec))
            outputFile
        } catch (exception: Exception) {
            outputFile.delete()
            Log.w(TAG, "Failed to render procedural sound for ${effect.name}.", exception)
            null
        }
    }

    private fun normalizeSelectedSoundSetId(setId: String): String {
        val preferredId = setId.ifBlank { AppSettings.DEFAULT_SOUND_SET_ID }
        return when {
            preferredId == BUILT_IN_SOUND_SET_ID -> BUILT_IN_SOUND_SET_ID
            catalog.containsKey(preferredId) -> preferredId
            catalog.containsKey(AppSettings.DEFAULT_SOUND_SET_ID) -> AppSettings.DEFAULT_SOUND_SET_ID
            else -> BUILT_IN_SOUND_SET_ID
        }
    }

    private fun buildCatalog(): Map<String, Map<SoundEffect, AssetSoundFile>> {
        val root = SOUNDSETS_ROOT
        val folderNames = assets.list(root)?.toList().orEmpty()
        if (folderNames.isEmpty()) return emptyMap()

        return folderNames.sorted().mapNotNull { folderName ->
            val files = assets.list("$root/$folderName")?.toList().orEmpty()
            if (files.isEmpty()) return@mapNotNull null

            val byLowercaseName = files.associateBy { it.lowercase(Locale.ROOT) }
            val effectMap = SoundEffect.entries.mapNotNull { effect ->
                resolveAssetFile(folderName, effect, byLowercaseName)?.let { effect to it }
            }.toMap()

            if (effectMap.isEmpty()) null else folderName to effectMap
        }.toMap()
    }

    private fun resolveAssetFile(
        folderName: String,
        effect: SoundEffect,
        filesByLowercaseName: Map<String, String>,
    ): AssetSoundFile? {
        for (baseName in candidateBaseNames(effect)) {
            for (extension in SUPPORTED_EXTENSIONS) {
                val candidateName = "$baseName.$extension"
                val actualName = filesByLowercaseName[candidateName] ?: continue
                return AssetSoundFile(
                    assetPath = "$SOUNDSETS_ROOT/$folderName/$actualName",
                    fileName = actualName,
                )
            }
        }
        return null
    }

    private fun candidateBaseNames(effect: SoundEffect): List<String> = when (effect) {
        SoundEffect.SINGLE_TAP -> listOf("tap", "single_tap", "ui_tap")
        SoundEffect.DOUBLE_TAP -> listOf("double_tap", "confirm_tap")
        SoundEffect.BUTTON_PRESS -> listOf("button_press", "press", "ui_press")
        SoundEffect.COUNTDOWN_TICK -> listOf("countdown_tick", "counter_tick", "tick")
        SoundEffect.ROUND_START -> listOf("round_start", "start_round", "ready_go")
        SoundEffect.TOPIC_SUCCESS -> listOf("topic_success", "completed", "success")
        SoundEffect.TOPIC_SKIP -> listOf("topic_skip", "skip")
        SoundEffect.TOPIC_TIMEOUT -> listOf("topic_timeout", "timeout", "time_up")
        SoundEffect.ROUND_SUCCESS -> listOf("round_success", "round_win", "win")
        SoundEffect.ROUND_FAILURE -> listOf("round_failure", "round_lose", "lose")
    }

    private fun soundKey(setId: String, effect: SoundEffect): String = "$setId|${effect.name}"

    private fun prettifySoundSetName(id: String): String = id
        .replace('-', ' ')
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
        }

    private fun builtInSoundSpec(effect: SoundEffect): SoundSpec = when (effect) {
        SoundEffect.SINGLE_TAP -> SoundSpec(
            slices = listOf(
                ToneSlice(frequencyHz = 920.0, durationMs = 56, amplitude = 0.42f),
            ),
        )
        SoundEffect.DOUBLE_TAP -> SoundSpec(
            slices = listOf(
                ToneSlice(frequencyHz = 960.0, durationMs = 46, amplitude = 0.42f),
                ToneSlice(frequencyHz = null, durationMs = 34),
                ToneSlice(frequencyHz = 1220.0, durationMs = 62, amplitude = 0.46f),
            ),
        )
        SoundEffect.BUTTON_PRESS -> SoundSpec(
            slices = listOf(
                ToneSlice(frequencyHz = 780.0, durationMs = 62, amplitude = 0.44f),
            ),
        )
        SoundEffect.COUNTDOWN_TICK -> SoundSpec(
            slices = listOf(
                ToneSlice(frequencyHz = 1040.0, durationMs = 42, amplitude = 0.36f),
                ToneSlice(frequencyHz = null, durationMs = 16),
                ToneSlice(frequencyHz = 1040.0, durationMs = 38, amplitude = 0.30f),
            ),
        )
        SoundEffect.ROUND_START -> SoundSpec(
            slices = listOf(
                ToneSlice(frequencyHz = 720.0, durationMs = 56, amplitude = 0.38f),
                ToneSlice(frequencyHz = null, durationMs = 18),
                ToneSlice(frequencyHz = 960.0, durationMs = 68, amplitude = 0.44f),
                ToneSlice(frequencyHz = null, durationMs = 18),
                ToneSlice(frequencyHz = 1320.0, durationMs = 104, amplitude = 0.52f),
            ),
        )
        SoundEffect.TOPIC_SUCCESS -> SoundSpec(
            slices = listOf(
                ToneSlice(frequencyHz = 880.0, durationMs = 78, amplitude = 0.44f),
                ToneSlice(frequencyHz = null, durationMs = 24),
                ToneSlice(frequencyHz = 1175.0, durationMs = 104, amplitude = 0.52f),
            ),
        )
        SoundEffect.TOPIC_SKIP -> SoundSpec(
            slices = listOf(
                ToneSlice(frequencyHz = 720.0, durationMs = 48, amplitude = 0.42f),
                ToneSlice(frequencyHz = null, durationMs = 18),
                ToneSlice(frequencyHz = 430.0, durationMs = 108, amplitude = 0.46f),
            ),
        )
        SoundEffect.TOPIC_TIMEOUT -> SoundSpec(
            slices = listOf(
                ToneSlice(frequencyHz = 360.0, durationMs = 210, amplitude = 0.48f),
            ),
        )
        SoundEffect.ROUND_SUCCESS -> SoundSpec(
            slices = listOf(
                ToneSlice(frequencyHz = 880.0, durationMs = 84, amplitude = 0.42f),
                ToneSlice(frequencyHz = null, durationMs = 20),
                ToneSlice(frequencyHz = 1175.0, durationMs = 92, amplitude = 0.48f),
                ToneSlice(frequencyHz = null, durationMs = 24),
                ToneSlice(frequencyHz = 1568.0, durationMs = 136, amplitude = 0.56f),
            ),
        )
        SoundEffect.ROUND_FAILURE -> SoundSpec(
            slices = listOf(
                ToneSlice(frequencyHz = 390.0, durationMs = 132, amplitude = 0.46f),
                ToneSlice(frequencyHz = null, durationMs = 24),
                ToneSlice(frequencyHz = 310.0, durationMs = 184, amplitude = 0.50f),
            ),
        )
    }

    private fun renderWav(spec: SoundSpec): ByteArray {
        val sampleRate = SAMPLE_RATE
        val pcm = ByteArrayOutputStream()

        spec.slices.forEach { slice ->
            val sampleCount = (slice.durationMs / 1000.0 * sampleRate).roundToInt().coerceAtLeast(1)
            val attackSamples = (sampleCount * 0.08f).roundToInt().coerceIn(1, 220)
            val releaseSamples = (sampleCount * 0.18f).roundToInt().coerceIn(1, 320)
            for (sampleIndex in 0 until sampleCount) {
                val normalizedAmplitude = when {
                    slice.frequencyHz == null -> 0.0
                    sampleIndex < attackSamples -> slice.amplitude * (sampleIndex.toDouble() / attackSamples)
                    sampleIndex >= sampleCount - releaseSamples -> {
                        val remaining = (sampleCount - sampleIndex).coerceAtLeast(0)
                        slice.amplitude * (remaining.toDouble() / releaseSamples)
                    }
                    else -> slice.amplitude.toDouble()
                }
                val sampleValue = if (slice.frequencyHz == null) {
                    0.0
                } else {
                    val angle = 2.0 * PI * slice.frequencyHz * sampleIndex / sampleRate
                    sin(angle) * normalizedAmplitude
                }
                val pcmSample = (sampleValue * Short.MAX_VALUE)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                writeLittleEndianShort(pcm, pcmSample)
            }
        }

        val pcmBytes = pcm.toByteArray()
        return ByteArrayOutputStream(44 + pcmBytes.size).apply {
            write("RIFF".encodeToByteArray())
            writeLittleEndianInt(this, 36 + pcmBytes.size)
            write("WAVE".encodeToByteArray())
            write("fmt ".encodeToByteArray())
            writeLittleEndianInt(this, 16)
            writeLittleEndianShort(this, 1.toShort())
            writeLittleEndianShort(this, 1.toShort())
            writeLittleEndianInt(this, sampleRate)
            writeLittleEndianInt(this, sampleRate * BYTES_PER_SAMPLE)
            writeLittleEndianShort(this, BYTES_PER_SAMPLE.toShort())
            writeLittleEndianShort(this, BITS_PER_SAMPLE.toShort())
            write("data".encodeToByteArray())
            writeLittleEndianInt(this, pcmBytes.size)
            write(pcmBytes)
        }.toByteArray()
    }

    private fun writeLittleEndianInt(stream: ByteArrayOutputStream, value: Int) {
        stream.write(value and 0xFF)
        stream.write(value shr 8 and 0xFF)
        stream.write(value shr 16 and 0xFF)
        stream.write(value shr 24 and 0xFF)
    }

    private fun writeLittleEndianShort(stream: ByteArrayOutputStream, value: Short) {
        stream.write(value.toInt() and 0xFF)
        stream.write(value.toInt() shr 8 and 0xFF)
    }

    private fun playToneFallback(effect: SoundEffect, volumeLevel: Int) {
        val toneVolume = (volumeLevel.coerceIn(1, 10) * 10).coerceIn(10, 100)

        scope.launch(Dispatchers.Default) {
            toneFallbackMutex.withLock {
                val generator = try {
                    ToneGenerator(AudioManager.STREAM_MUSIC, toneVolume)
                } catch (_: RuntimeException) {
                    return@withLock
                }

                try {
                    when (effect) {
                        SoundEffect.SINGLE_TAP -> {
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                            delay(80)
                        }

                        SoundEffect.DOUBLE_TAP -> {
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 56)
                            delay(84)
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 72)
                            delay(98)
                        }

                        SoundEffect.BUTTON_PRESS -> {
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 68)
                            delay(92)
                        }

                        SoundEffect.COUNTDOWN_TICK -> {
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 48)
                            delay(78)
                        }

                        SoundEffect.ROUND_START -> {
                            generator.startTone(ToneGenerator.TONE_PROP_ACK, 72)
                            delay(102)
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 86)
                            delay(116)
                            generator.startTone(ToneGenerator.TONE_PROP_PROMPT, 118)
                            delay(148)
                        }

                        SoundEffect.TOPIC_SUCCESS -> {
                            generator.startTone(ToneGenerator.TONE_PROP_ACK, 96)
                            delay(124)
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 108)
                            delay(138)
                        }

                        SoundEffect.TOPIC_SKIP -> {
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 58)
                            delay(86)
                            generator.startTone(ToneGenerator.TONE_PROP_NACK, 112)
                            delay(144)
                        }

                        SoundEffect.TOPIC_TIMEOUT -> {
                            generator.startTone(ToneGenerator.TONE_PROP_NACK, 210)
                            delay(244)
                        }

                        SoundEffect.ROUND_SUCCESS -> {
                            generator.startTone(ToneGenerator.TONE_PROP_ACK, 104)
                            delay(132)
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 118)
                            delay(146)
                            generator.startTone(ToneGenerator.TONE_PROP_PROMPT, 148)
                            delay(184)
                        }

                        SoundEffect.ROUND_FAILURE -> {
                            generator.startTone(ToneGenerator.TONE_SUP_ERROR, 188)
                            delay(220)
                            generator.startTone(ToneGenerator.TONE_PROP_NACK, 196)
                            delay(232)
                        }
                    }
                } finally {
                    generator.release()
                }
            }
        }
    }

    private data class AssetSoundFile(
        val assetPath: String,
        val fileName: String,
    )

    private data class SoundSpec(
        val slices: List<ToneSlice>,
    )

    private data class ToneSlice(
        val frequencyHz: Double?,
        val durationMs: Int,
        val amplitude: Float = 0f,
    )

    companion object {
        private const val TAG = "ProceduralSoundPlayer"
        private const val BUILT_IN_SOUND_SET_ID = "builtin_procedural"
        private const val SOUNDSETS_ROOT = "soundsets"
        private const val SAMPLE_RATE = 44_100
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = 2
        private val SUPPORTED_EXTENSIONS = listOf("ogg", "mp3", "wav", "m4a")
    }
}
