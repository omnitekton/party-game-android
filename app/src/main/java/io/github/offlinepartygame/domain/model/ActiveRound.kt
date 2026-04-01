package io.github.offlinepartygame.domain.model

data class ActiveRound(
    val modeId: String,
    val categoryId: String,
    val categoryNamePl: String,
    val categoryNameEn: String,
    val totalTopics: Int,
    val topicDurationSec: Int,
    val countdownDurationSec: Int,
    val timeoutDurationSec: Int,
    val completedCount: Int,
    val timedOutCount: Int,
    val skippedCount: Int,
    val phase: RoundPhase,
    val phaseStartedAtMillis: Long,
    val phaseEndsAtMillis: Long,
    val currentTopic: Topic,
    val shownTopicIds: List<String>,
) {
    val currentTopicNumber: Int
        get() = shownTopicIds.size

    val processedTopicsCount: Int
        get() = completedCount + timedOutCount + skippedCount

    fun categoryDisplayName(languageCode: String): String = if (languageCode.startsWith("pl", ignoreCase = true)) {
        categoryNamePl
    } else {
        categoryNameEn
    }
}
