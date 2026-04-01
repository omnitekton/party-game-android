package io.github.offlinepartygame.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_round")
data class ActiveRoundEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
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
    val phase: String,
    val phaseStartedAtMillis: Long,
    val phaseEndsAtMillis: Long,
    val currentTopicId: String,
    val currentTopicTextPl: String,
    val currentTopicTextEn: String,
    val shownTopicIdsCsv: String,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
