package io.github.offlinepartygame.domain.service

import io.github.offlinepartygame.domain.model.ActiveRound
import io.github.offlinepartygame.domain.model.AppSettings
import io.github.offlinepartygame.domain.model.GameMode
import io.github.offlinepartygame.domain.model.PartyGameError
import io.github.offlinepartygame.domain.model.RoundPhase
import io.github.offlinepartygame.domain.model.RoundProgressResult
import io.github.offlinepartygame.domain.model.RoundSummary
import io.github.offlinepartygame.domain.model.Topic
import io.github.offlinepartygame.domain.repository.ActiveRoundRepository
import io.github.offlinepartygame.domain.repository.ContentRepository
import io.github.offlinepartygame.domain.repository.HistoryRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoundCoordinator(
    private val contentRepository: ContentRepository,
    private val historyRepository: HistoryRepository,
    private val activeRoundRepository: ActiveRoundRepository,
    private val topicSelector: TopicSelector,
    private val timeProvider: TimeProvider,
) {
    private val mutex = Mutex()

    suspend fun startRound(
        mode: GameMode,
        categoryId: String,
        settings: AppSettings,
    ): Result<RoundProgressResult> = runCatching {
        mutex.withLock {
            val sanitizedSettings = settings.sanitized()
            val categoryTopics = contentRepository.getCategoryTopics(categoryId)
                ?: throw PartyGameException(
                    PartyGameError.CATEGORY_UNAVAILABLE,
                    "Category '$categoryId' does not exist in the content catalog.",
                )

            if (!categoryTopics.isUsable) {
                throw PartyGameException(
                    PartyGameError.CATEGORY_UNAVAILABLE,
                    "Category '$categoryId' is marked as unusable.",
                )
            }

            if (categoryTopics.topics.isEmpty()) {
                throw PartyGameException(
                    PartyGameError.NO_TOPICS_AVAILABLE,
                    "Category '$categoryId' does not contain any valid topics.",
                )
            }

            val now = timeProvider.nowMillis()
            historyRepository.pruneHistory(olderThanMillis = now - HISTORY_PRUNE_WINDOW_MILLIS)

            val firstTopic = selectAndRecordTopic(
                categoryId = categoryId,
                allTopics = categoryTopics.topics,
                roundUsedTopicIds = emptySet(),
                shownAtMillis = now,
            )

            val round = ActiveRound(
                modeId = mode.id,
                categoryId = categoryTopics.category.id,
                categoryNamePl = categoryTopics.category.namePl,
                categoryNameEn = categoryTopics.category.nameEn,
                totalTopics = sanitizedSettings.topicsPerRound,
                topicDurationSec = sanitizedSettings.topicDurationSec,
                countdownDurationSec = sanitizedSettings.preRoundCountdownSec,
                timeoutDurationSec = sanitizedSettings.timeoutMessageDurationSec,
                completedCount = 0,
                timedOutCount = 0,
                skippedCount = 0,
                phase = RoundPhase.COUNTDOWN,
                phaseStartedAtMillis = now,
                phaseEndsAtMillis = now + sanitizedSettings.preRoundCountdownSec * 1_000L,
                currentTopic = firstTopic,
                shownTopicIds = listOf(firstTopic.stableId),
            )
            activeRoundRepository.saveActiveRound(round)
            RoundProgressResult(activeRound = round)
        }
    }

    suspend fun restoreActiveRound(): Result<RoundProgressResult> = runCatching {
        mutex.withLock {
            val existing = activeRoundRepository.getActiveRound() ?: return@withLock RoundProgressResult()
            advanceExpiredPhases(existing, timeProvider.nowMillis())
        }
    }

    suspend fun completeCurrentTopic(): Result<RoundProgressResult> = runCatching {
        mutex.withLock {
            val now = timeProvider.nowMillis()
            val existing = activeRoundRepository.getActiveRound()
                ?: throw PartyGameException(
                    PartyGameError.ROUND_NOT_ACTIVE,
                    "Tried to complete a topic without an active round.",
                )

            val currentState = advanceExpiredPhases(existing, now)
            currentState.summary?.let { return@withLock currentState }

            val round = currentState.activeRound
                ?: throw PartyGameException(
                    PartyGameError.ROUND_NOT_ACTIVE,
                    "The round disappeared while handling a completion signal.",
                )

            if (round.phase != RoundPhase.TOPIC) {
                return@withLock currentState
            }

            val completedRound = round.copy(completedCount = round.completedCount + 1)
            if (completedRound.processedTopicsCount >= completedRound.totalTopics) {
                activeRoundRepository.clearActiveRound()
                return@withLock RoundProgressResult(summary = completedRound.toSummary())
            }

            val topics = requireTopics(round.categoryId)
            val nextTopic = selectAndRecordTopic(
                categoryId = round.categoryId,
                allTopics = topics,
                roundUsedTopicIds = round.shownTopicIds.toSet(),
                shownAtMillis = now,
            )

            val updated = completedRound.copy(
                phase = RoundPhase.TOPIC,
                phaseStartedAtMillis = now,
                phaseEndsAtMillis = now + completedRound.topicDurationSec * 1_000L,
                currentTopic = nextTopic,
                shownTopicIds = completedRound.shownTopicIds + nextTopic.stableId,
            )
            activeRoundRepository.saveActiveRound(updated)
            RoundProgressResult(activeRound = updated)
        }
    }


    suspend fun skipCurrentTopic(): Result<RoundProgressResult> = runCatching {
        mutex.withLock {
            val now = timeProvider.nowMillis()
            val existing = activeRoundRepository.getActiveRound()
                ?: throw PartyGameException(
                    PartyGameError.ROUND_NOT_ACTIVE,
                    "Tried to skip a topic without an active round.",
                )

            val currentState = advanceExpiredPhases(existing, now)
            currentState.summary?.let { return@withLock currentState }

            val round = currentState.activeRound
                ?: throw PartyGameException(
                    PartyGameError.ROUND_NOT_ACTIVE,
                    "The round disappeared while handling a skip signal.",
                )

            if (round.phase != RoundPhase.TOPIC) {
                return@withLock currentState
            }

            val skippedRound = round.copy(skippedCount = round.skippedCount + 1)
            if (skippedRound.processedTopicsCount >= skippedRound.totalTopics) {
                activeRoundRepository.clearActiveRound()
                return@withLock RoundProgressResult(summary = skippedRound.toSummary())
            }

            val topics = requireTopics(round.categoryId)
            val nextTopic = selectAndRecordTopic(
                categoryId = round.categoryId,
                allTopics = topics,
                roundUsedTopicIds = round.shownTopicIds.toSet(),
                shownAtMillis = now,
            )

            val updated = skippedRound.copy(
                phase = RoundPhase.TOPIC,
                phaseStartedAtMillis = now,
                phaseEndsAtMillis = now + skippedRound.topicDurationSec * 1_000L,
                currentTopic = nextTopic,
                shownTopicIds = skippedRound.shownTopicIds + nextTopic.stableId,
            )
            activeRoundRepository.saveActiveRound(updated)
            RoundProgressResult(activeRound = updated)
        }
    }

    suspend fun clearActiveRound() = mutex.withLock {
        activeRoundRepository.clearActiveRound()
    }

    private suspend fun advanceExpiredPhases(
        round: ActiveRound,
        nowMillis: Long,
    ): RoundProgressResult {
        var current = round
        while (nowMillis >= current.phaseEndsAtMillis) {
            when (current.phase) {
                RoundPhase.COUNTDOWN -> {
                    val nextStart = current.phaseEndsAtMillis
                    current = current.copy(
                        phase = RoundPhase.TOPIC,
                        phaseStartedAtMillis = nextStart,
                        phaseEndsAtMillis = nextStart + current.topicDurationSec * 1_000L,
                    )
                    activeRoundRepository.saveActiveRound(current)
                }

                RoundPhase.TOPIC -> {
                    val nextStart = current.phaseEndsAtMillis
                    current = current.copy(
                        timedOutCount = current.timedOutCount + 1,
                        phase = RoundPhase.TIME_UP,
                        phaseStartedAtMillis = nextStart,
                        phaseEndsAtMillis = nextStart + current.timeoutDurationSec * 1_000L,
                    )
                    activeRoundRepository.saveActiveRound(current)
                }

                RoundPhase.TIME_UP -> {
                    if (current.processedTopicsCount >= current.totalTopics) {
                        activeRoundRepository.clearActiveRound()
                        return RoundProgressResult(summary = current.toSummary())
                    }

                    val topics = requireTopics(current.categoryId)
                    val nextTopic = selectAndRecordTopic(
                        categoryId = current.categoryId,
                        allTopics = topics,
                        roundUsedTopicIds = current.shownTopicIds.toSet(),
                        shownAtMillis = current.phaseEndsAtMillis,
                    )
                    val nextStart = current.phaseEndsAtMillis
                    current = current.copy(
                        phase = RoundPhase.TOPIC,
                        phaseStartedAtMillis = nextStart,
                        phaseEndsAtMillis = nextStart + current.topicDurationSec * 1_000L,
                        currentTopic = nextTopic,
                        shownTopicIds = current.shownTopicIds + nextTopic.stableId,
                    )
                    activeRoundRepository.saveActiveRound(current)
                }
            }
        }

        return RoundProgressResult(activeRound = current)
    }

    private suspend fun requireTopics(categoryId: String): List<Topic> {
        val result = contentRepository.getCategoryTopics(categoryId)
            ?: throw PartyGameException(
                PartyGameError.CATEGORY_UNAVAILABLE,
                "Category '$categoryId' disappeared while restoring a round.",
            )
        if (!result.isUsable || result.topics.isEmpty()) {
            throw PartyGameException(
                PartyGameError.NO_TOPICS_AVAILABLE,
                "Category '$categoryId' has no playable topics.",
            )
        }
        return result.topics
    }

    private suspend fun selectAndRecordTopic(
        categoryId: String,
        allTopics: List<Topic>,
        roundUsedTopicIds: Set<String>,
        shownAtMillis: Long,
    ): Topic {
        val blockedSince = shownAtMillis - REPEAT_BLOCK_WINDOW_MILLIS
        val recentIds = historyRepository.getRecentlyShownTopicIds(
            categoryId = categoryId,
            newerThanMillis = blockedSince,
        )
        val selection = topicSelector.selectNextTopic(
            allTopics = allTopics,
            recentlyShownTopicIds = recentIds,
            roundUsedTopicIds = roundUsedTopicIds,
        )
        historyRepository.recordTopicShown(
            categoryId = categoryId,
            topicId = selection.topic.stableId,
            shownAtMillis = shownAtMillis,
        )
        return selection.topic
    }

    private fun ActiveRound.toSummary(): RoundSummary = RoundSummary(
        modeId = modeId,
        categoryId = categoryId,
        categoryNamePl = categoryNamePl,
        categoryNameEn = categoryNameEn,
        completedCount = completedCount,
        timedOutCount = timedOutCount,
        skippedCount = skippedCount,
        totalTopics = totalTopics,
    )

    companion object {
        const val REPEAT_BLOCK_WINDOW_MILLIS: Long = 8L * 60L * 60L * 1_000L
        private const val HISTORY_PRUNE_WINDOW_MILLIS: Long = 14L * 24L * 60L * 60L * 1_000L
    }
}
