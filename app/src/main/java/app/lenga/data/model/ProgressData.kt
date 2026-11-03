package app.lenga.data.model

import kotlinx.serialization.Serializable

/**
 * Data class representing overall user progress statistics
 */
data class ProgressData(
    val totalStudyHours: String = "0m",
    val quizzesPassed: Int = 0,
    val currentStreak: Int = 0,
    val avgScore: String = "0",
    val weeklyData: List<DailyStudyData> = emptyList(),
    val recentAchievements: List<Achievement> = emptyList(),
    val recentSessions: List<StudySession> = emptyList(),
    val emergencyUnlocksThisWeek: Int = 0
)

/**
 * Represents study time for a specific day of the week
 */
@Serializable
data class DailyStudyData(
    val dayName: String,
    val minutes: Int
)

/**
 * Represents an achievement/badge earned by the user
 */
@Serializable
data class Achievement(
    val icon: String,
    val title: String,
    val description: String,
    val date: String
)

/**
 * Represents a single study session record
 */
@Serializable
data class StudySession(
    val moduleName: String,
    val duration: String,
    val date: String,
    val completed: Boolean,
    val quizScore: Int? = null
)
