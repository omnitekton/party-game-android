package io.github.offlinepartygame.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TopicHistoryEntity::class,
        ActiveRoundEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun topicHistoryDao(): TopicHistoryDao
    abstract fun activeRoundDao(): ActiveRoundDao
}
