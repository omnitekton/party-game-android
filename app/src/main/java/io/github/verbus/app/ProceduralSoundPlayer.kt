package io.github.verbus.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
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
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ProceduralSoundPlayer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val assets = appContext.assets
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val proceduralMutex = Mutex()
    private val cacheRoot = File(appContext.cacheDir, "soundsets")

    private val soundPool: SoundPool = SoundPool.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setMaxStreams(8)
        .build()

    private val catalog: Map<String, Map<SoundEffect, AssetSoundFile>> = buildCatalog()
    private val options: List<SoundSetOption> = buildList {
        add(SoundSetOption(AppSettings.DEFAULT_SOUND_SET_ID, "Built-in procedural"))
        addAll(
            catalog.keys.sorted().map { setId ->
                SoundSetOption(id = setId, displayName = prettifySoundSetName(setId))
            },
        )
    }

    private val pendingSoundIds = ConcurrentHashMap<String, Int>()
    private val readySoundIds = ConcurrentHashMap<String, Int>()
    private val soundIdToKey = ConcurrentHashMap<Int, String>()

    @Volatile
    private var activeSoundSetId: String = AppSettings.DEFAULT_SOUND_SET_ID

    init {
        cacheRoot.mkdirs()
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            val key = soundIdToKey[soundId] ?: return@setOnLoadCompleteListener
            if (status == 0) {
                readySoundIds[key] = soundId
            } else {
                pendingSoundIds.remove(key)
                readySoundIds.remove(key)
            }
        }
    }

    fun availableSoundSets(): List<SoundSetOption> = options

    fun prepareSelectedSet(setId: String) {
        val normalizedId = setId.ifBlank { AppSettings.DEFAULT_SOUND_SET_ID }
        activeSoundSetId = normalizedId
        if (normalizedId == AppSettings.DEFAULT_SOUND_SET_ID) return
        val assetMap = catalog[normalizedId] ?: return

        scope.launch {
            assetMap.forEach { (effect, assetFile) ->
                ensureSoundLoaded(normalizedId, effect, assetFile)
            }
        }
    }

    fun play(effect: SoundEffect, enabled: Boolean, volumeLevel: Int) {
        if (!enabled) return

        val setId = activeSoundSetId
        val volume = (volumeLevel.coerceIn(1, 10) / 10f).coerceIn(0.1f, 1f)
        val loadedKey = soundKey(setId, effect)
        val soundId = readySoundIds[loadedKey]

        if (setId != AppSettings.DEFAULT_SOUND_SET_ID && soundId != null) {
            try {
                soundPool.play(soundId, volume, volume, 1, 0, 1f)
                return
            } catch (_: RuntimeException) {
                readySoundIds.remove(loadedKey)
            }
        }

        if (setId != AppSettings.DEFAULT_SOUND_SET_ID && pendingSoundIds[loadedKey] == null) {
            catalog[setId]?.get(effect)?.let { assetFile ->
                scope.launch {
                    ensureSoundLoaded(setId, effect, assetFile)
                }
            }
        }

        playProcedural(effect = effect, volumeLevel = volumeLevel)
    }

    private suspend fun ensureSoundLoaded(
        setId: String,
        effect: SoundEffect,
        assetFile: AssetSoundFile,
    ) {
        val key = soundKey(setId, effect)
        if (readySoundIds.containsKey(key) || pendingSoundIds.containsKey(key)) return

        val cachedFile = copyAssetToCache(setId = setId, assetFile = assetFile)
        val soundId = try {
            soundPool.load(cachedFile.absolutePath, 1)
        } catch (_: RuntimeException) {
            return
        }

        pendingSoundIds[key] = soundId
        soundIdToKey[soundId] = key
    }

    private fun copyAssetToCache(setId: String, assetFile: AssetSoundFile): File {
        val setCacheDir = File(cacheRoot, setId).apply { mkdirs() }
        val outputFile = File(setCacheDir, assetFile.fileName)
        if (outputFile.exists() && outputFile.length() > 0L) {
            return outputFile
        }

        assets.open(assetFile.assetPath).use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outputFile
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

    private fun playProcedural(effect: SoundEffect, volumeLevel: Int) {
        val toneVolume = (volumeLevel.coerceIn(1, 10) * 10).coerceIn(10, 100)

        scope.launch(Dispatchers.Default) {
            proceduralMutex.withLock {
                val generator = try {
                    ToneGenerator(AudioManager.STREAM_MUSIC, toneVolume)
                } catch (_: RuntimeException) {
                    return@withLock
                }

                try {
                    when (effect) {
                        SoundEffect.SINGLE_TAP -> {
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 35)
                            delay(45)
                        }

                        SoundEffect.DOUBLE_TAP -> {
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 50)
                            delay(60)
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 50)
                            delay(70)
                        }

                        SoundEffect.BUTTON_PRESS -> {
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
                            delay(55)
                        }

                        SoundEffect.TOPIC_SUCCESS -> {
                            generator.startTone(ToneGenerator.TONE_PROP_ACK, 110)
                            delay(130)
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 80)
                            delay(100)
                        }

                        SoundEffect.TOPIC_SKIP -> {
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                            delay(55)
                            generator.startTone(ToneGenerator.TONE_PROP_NACK, 90)
                            delay(110)
                        }

                        SoundEffect.TOPIC_TIMEOUT -> {
                            generator.startTone(ToneGenerator.TONE_PROP_NACK, 180)
                            delay(200)
                        }

                        SoundEffect.ROUND_SUCCESS -> {
                            generator.startTone(ToneGenerator.TONE_PROP_ACK, 130)
                            delay(150)
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 110)
                            delay(130)
                            generator.startTone(ToneGenerator.TONE_PROP_PROMPT, 160)
                            delay(180)
                        }

                        SoundEffect.ROUND_FAILURE -> {
                            generator.startTone(ToneGenerator.TONE_SUP_ERROR, 180)
                            delay(190)
                            generator.startTone(ToneGenerator.TONE_PROP_NACK, 180)
                            delay(200)
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

    companion object {
        private const val SOUNDSETS_ROOT = "soundsets"
        private val SUPPORTED_EXTENSIONS = listOf("ogg", "mp3", "wav", "m4a")
    }
}
