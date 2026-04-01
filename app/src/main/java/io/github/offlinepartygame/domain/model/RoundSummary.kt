package io.github.offlinepartygame.domain.model

data class RoundSummary(
    val modeId: String,
    val categoryId: String,
    val categoryNamePl: String,
    val categoryNameEn: String,
    val completedCount: Int,
    val timedOutCount: Int,
    val skippedCount: Int,
    val totalTopics: Int,
) {
    fun categoryDisplayName(languageCode: String): String = if (languageCode.startsWith("pl", ignoreCase = true)) {
        categoryNamePl
    } else {
        categoryNameEn
    }
}
