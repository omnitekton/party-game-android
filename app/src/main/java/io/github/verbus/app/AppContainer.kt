package io.github.verbus.app

import android.content.Context
import androidx.room.Room
import io.github.verbus.data.content.AssetContentRepository
import io.github.verbus.data.content.TopicFileParser
import io.github.verbus.data.local.AppDatabase
import io.github.verbus.data.preferences.SettingsRepositoryImpl
import io.github.verbus.data.repository.ActiveRoundRepositoryImpl
import io.github.verbus.data.repository.HistoryRepositoryImpl
import io.github.verbus.domain.service.RoundCoordinator
import io.github.verbus.domain.service.TopicSelector
import java.security.SecureRandom

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val secureRandom = SecureRandom()

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "party_game.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
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
    val soundPlayer = ProceduralSoundPlayer(appContext)

    private val topicSelector = TopicSelector(
        randomProvider = { until -> secureRandom.nextInt(until) },
    )

    val roundCoordinator = RoundCoordinator(
        contentRepository = contentRepository,
        historyRepository = historyRepository,
        activeRoundRepository = activeRoundRepository,
        topicSelector = topicSelector,
        timeProvider = { System.currentTimeMillis() },
    )
}
