package io.github.offlinepartygame.app

import android.content.Context
import androidx.room.Room
import io.github.offlinepartygame.data.content.AssetContentRepository
import io.github.offlinepartygame.data.content.TopicFileParser
import io.github.offlinepartygame.data.local.AppDatabase
import io.github.offlinepartygame.data.preferences.SettingsRepositoryImpl
import io.github.offlinepartygame.data.repository.ActiveRoundRepositoryImpl
import io.github.offlinepartygame.data.repository.HistoryRepositoryImpl
import io.github.offlinepartygame.domain.service.RandomProvider
import io.github.offlinepartygame.domain.service.RoundCoordinator
import io.github.offlinepartygame.domain.service.TimeProvider
import io.github.offlinepartygame.domain.service.TopicSelector
import kotlin.random.Random

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "party_game.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val settingsRepository = SettingsRepositoryImpl(appContext)
    val contentRepository = AssetContentRepository(
        appContext = appContext,
        parser = TopicFileParser(),
    )
    val historyRepository = HistoryRepositoryImpl(database.topicHistoryDao())
    val activeRoundRepository = ActiveRoundRepositoryImpl(database.activeRoundDao())
    val shakeSupportChecker = ShakeSupportChecker(appContext)
    val soundPlayer = ProceduralSoundPlayer()

    private val topicSelector = TopicSelector(
        randomProvider = RandomProvider { until -> Random.nextInt(until) },
    )

    val roundCoordinator = RoundCoordinator(
        contentRepository = contentRepository,
        historyRepository = historyRepository,
        activeRoundRepository = activeRoundRepository,
        topicSelector = topicSelector,
        timeProvider = TimeProvider { System.currentTimeMillis() },
    )
}
