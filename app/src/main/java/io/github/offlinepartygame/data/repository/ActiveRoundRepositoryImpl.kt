package io.github.offlinepartygame.data.repository

import io.github.offlinepartygame.data.local.ActiveRoundDao
import io.github.offlinepartygame.data.local.ActiveRoundEntity
import io.github.offlinepartygame.domain.model.ActiveRound
import io.github.offlinepartygame.domain.model.RoundPhase
import io.github.offlinepartygame.domain.model.Topic
import io.github.offlinepartygame.domain.repository.ActiveRoundRepository

class ActiveRoundRepositoryImpl(
    private val activeRoundDao: ActiveRoundDao,
) : ActiveRoundRepository {
    override suspend fun getActiveRound(): ActiveRound? = activeRoundDao.get()?.toDomain()

    override suspend fun saveActiveRound(round: ActiveRound) {
        activeRoundDao.upsert(round.toEntity())
    }

    override suspend fun clearActiveRound() {
        activeRoundDao.clear()
    }
}

private fun ActiveRoundEntity.toDomain(): ActiveRound = ActiveRound(
    modeId = modeId,
    categoryId = categoryId,
    categoryNamePl = categoryNamePl,
    categoryNameEn = categoryNameEn,
    totalTopics = totalTopics,
    topicDurationSec = topicDurationSec,
    countdownDurationSec = countdownDurationSec,
    timeoutDurationSec = timeoutDurationSec,
    completedCount = completedCount,
    timedOutCount = timedOutCount,
    skippedCount = skippedCount,
    phase = RoundPhase.valueOf(phase),
    phaseStartedAtMillis = phaseStartedAtMillis,
    phaseEndsAtMillis = phaseEndsAtMillis,
    currentTopic = Topic(
        stableId = currentTopicId,
        textPl = currentTopicTextPl,
        textEn = currentTopicTextEn,
    ),
    shownTopicIds = shownTopicIdsCsv.split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() },
)

private fun ActiveRound.toEntity(): ActiveRoundEntity = ActiveRoundEntity(
    modeId = modeId,
    categoryId = categoryId,
    categoryNamePl = categoryNamePl,
    categoryNameEn = categoryNameEn,
    totalTopics = totalTopics,
    topicDurationSec = topicDurationSec,
    countdownDurationSec = countdownDurationSec,
    timeoutDurationSec = timeoutDurationSec,
    completedCount = completedCount,
    timedOutCount = timedOutCount,
    skippedCount = skippedCount,
    phase = phase.name,
    phaseStartedAtMillis = phaseStartedAtMillis,
    phaseEndsAtMillis = phaseEndsAtMillis,
    currentTopicId = currentTopic.stableId,
    currentTopicTextPl = currentTopic.textPl,
    currentTopicTextEn = currentTopic.textEn,
    shownTopicIdsCsv = shownTopicIds.joinToString(","),
)
