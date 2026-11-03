package app.lenga.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.lenga.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.*

// Extension for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "study_lock_preferences")

/**
 * Repository class that handles all data operations for the Lenga app
 * Uses DataStore for local persistence and provides in-memory caching
 */
class StudyRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    // Keys for DataStore
    private object PreferencesKeys {
        val USER_ID = stringPreferencesKey("user_id")
        val TOTAL_STUDY_TIME_MINUTES = longPreferencesKey("total_study_time_minutes")
        val QUIZZES_PASSED = intPreferencesKey("quizzes_passed")
        val QUIZZES_ATTEMPTED = intPreferencesKey("quizzes_attempted")
        val CURRENT_STREAK_DAYS = intPreferencesKey("current_streak_days")
        val LAST_STUDY_DATE = stringPreferencesKey("last_study_date")
        val COMPLETED_MODULES = stringPreferencesKey("completed_modules")
        val MODULE_PROGRESS = stringPreferencesKey("module_progress")
        val ACHIEVEMENTS = stringPreferencesKey("achievements")
        val STUDY_SESSIONS = stringPreferencesKey("study_sessions")
        val EMERGENCY_PIN_HASH = stringPreferencesKey("emergency_pin_hash")
        val EMERGENCY_UNLOCKS = stringPreferencesKey("emergency_unlocks")
        val SETTINGS = stringPreferencesKey("settings")
        val DAILY_QUOTE = stringPreferencesKey("daily_quote")
        val QUOTE_DATE = stringPreferencesKey("quote_date")
    }

    // ==================== USER DATA ====================

    /**
     * Get or create unique user ID
     */
    val userId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USER_ID] ?: UUID.randomUUID().toString().also {
                context.dataStore.edit { prefs ->
                    prefs[PreferencesKeys.USER_ID] = it
                }
            }
        }

    // ==================== STUDY PROGRESS ====================

    /**
     * Total study time in minutes
     */
    val totalStudyTimeMinutes: Flow<Long> = context.dataStore.data
        .map { it[PreferencesKeys.TOTAL_STUDY_TIME_MINUTES] ?: 0L }

    /**
     * Add study time to total
     */
    suspend fun addStudyTime(minutes: Long) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.TOTAL_STUDY_TIME_MINUTES] ?: 0L
            preferences[PreferencesKeys.TOTAL_STUDY_TIME_MINUTES] = current + minutes
        }
        updateStreak()
        checkStreakAchievements(currentStreak.first())
    }

    /**
     * Log a study session and add its time to the study total and streak
     */
    suspend fun recordStudySession(name: String, minutes: Long, completed: Boolean, quizScore: Int?) {
        logSession(
            StudySession(
                moduleName = name,
                duration = "$minutes min",
                date = Calendar.getInstance().toDateString(),
                completed = completed,
                quizScore = quizScore
            )
        )
        if (minutes > 0) addStudyTime(minutes)
    }

    /**
     * Format total study time as readable string
     */
    fun formatStudyTime(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }

    // ==================== STREAK MANAGEMENT ====================

    /**
     * Current study streak in days
     */
    val currentStreak: Flow<Int> = context.dataStore.data
        .map { it[PreferencesKeys.CURRENT_STREAK_DAYS] ?: 0 }

    /**
     * Update streak based on last study date
     */
    private suspend fun updateStreak() {
        val today = Calendar.getInstance().toDateString()
        val lastStudyDate = context.dataStore.data.first()[PreferencesKeys.LAST_STUDY_DATE]

        context.dataStore.edit { preferences ->
            when {
                lastStudyDate == null -> {
                    // First time studying
                    preferences[PreferencesKeys.CURRENT_STREAK_DAYS] = 1
                }
                lastStudyDate == today -> {
                    // Already studied today, no change
                }
                isYesterday(lastStudyDate) -> {
                    // Consecutive day, increment streak
                    val current = preferences[PreferencesKeys.CURRENT_STREAK_DAYS] ?: 0
                    preferences[PreferencesKeys.CURRENT_STREAK_DAYS] = current + 1
                }
                else -> {
                    // Streak broken, reset to 1
                    preferences[PreferencesKeys.CURRENT_STREAK_DAYS] = 1
                }
            }
            preferences[PreferencesKeys.LAST_STUDY_DATE] = today
        }
    }

    private fun isYesterday(dateString: String): Boolean {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        return calendar.toDateString() == dateString
    }

    private fun Calendar.toDateString(): String {
        return "${get(Calendar.YEAR)}-${get(Calendar.MONTH) + 1}-${get(Calendar.DAY_OF_MONTH)}"
    }

    // ==================== MODULE PROGRESS ====================

    /**
     * Get all modules with their completion status
     */
    fun getModules(): Flow<List<StudyModule>> = context.dataStore.data
        .map { preferences ->
            val completedIds = getCompletedModuleIds(preferences)
            sampleModules.map { module ->
                module.copy(
                    isCompleted = module.id in completedIds,
                    isLocked = !module.isAvailable(completedIds.toList())
                )
            }
        }

    /**
     * Mark module as completed
     */
    suspend fun completeModule(moduleId: String) {
        context.dataStore.edit { preferences ->
            val completed = getCompletedModuleIds(preferences).toMutableSet()
            completed.add(moduleId)
            preferences[PreferencesKeys.COMPLETED_MODULES] = json.encodeToString(completed.toList())
        }
    }

    private fun getCompletedModuleIds(preferences: Preferences): Set<String> {
        val jsonString = preferences[PreferencesKeys.COMPLETED_MODULES] ?: "[]"
        return try {
            json.decodeFromString<List<String>>(jsonString).toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Get progress for specific module (0-100)
     */
    suspend fun getModuleProgress(moduleId: String): Int {
        val progressMap = getModuleProgressMap()
        return progressMap[moduleId] ?: 0
    }

    /**
     * Update module progress percentage
     */
    suspend fun updateModuleProgress(moduleId: String, progress: Int) {
        context.dataStore.edit { preferences ->
            val progressMap = getModuleProgressMap(preferences).toMutableMap()
            progressMap[moduleId] = progress.coerceIn(0, 100)
            preferences[PreferencesKeys.MODULE_PROGRESS] = json.encodeToString(progressMap)
        }
    }

    private suspend fun getModuleProgressMap(): Map<String, Int> {
        return getModuleProgressMap(context.dataStore.data.first())
    }

    private fun getModuleProgressMap(preferences: Preferences): Map<String, Int> {
        val jsonString = preferences[PreferencesKeys.MODULE_PROGRESS] ?: "{}"
        return try {
            json.decodeFromString<Map<String, Int>>(jsonString)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ==================== QUIZ MANAGEMENT ====================

    /**
     * Get quiz for specific module
     */
    fun getQuizForModule(moduleId: String): Flow<QuizState> = flow {
        val questions = when (moduleId) {
            "math_basics" -> sampleQuestions.filter { it.subject == "Mathematics" }
            "physics_concepts" -> sampleQuestions.filter { it.subject == "Physics" }
            "chemistry_101" -> sampleQuestions.filter { it.subject == "Chemistry" }
            else -> sampleQuestions.shuffled().take(5)
        }.map { it.shuffledOptions() }

        emit(QuizState(
            questions = questions,
            passingScore = 70,
            rewardMinutes = calculateRewardMinutes(questions.size),
            isLastQuestion = questions.size == 1
        ))
    }

    private fun calculateRewardMinutes(questionCount: Int): Int {
        return when {
            questionCount <= 3 -> 10
            questionCount <= 5 -> 15
            questionCount <= 10 -> 20
            else -> 30
        }
    }

    /**
     * Submit quiz answers and calculate score
     */
    suspend fun submitQuiz(quizState: QuizState, answers: Map<String, String>): QuizResult {
        var correctCount = 0
        var totalPoints = 0
        var earnedPoints = 0

        quizState.questions.forEach { question ->
            totalPoints += question.points
            if (answers[question.id] == question.correctAnswerId) {
                correctCount++
                earnedPoints += question.points
            }
        }

        val percentage = if (totalPoints > 0) (earnedPoints * 100) / totalPoints else 0
        val passed = percentage >= quizState.passingScore

        // Update stats
        context.dataStore.edit { preferences ->
            val attempted = (preferences[PreferencesKeys.QUIZZES_ATTEMPTED] ?: 0) + 1
            preferences[PreferencesKeys.QUIZZES_ATTEMPTED] = attempted

            if (passed) {
                val passedCount = (preferences[PreferencesKeys.QUIZZES_PASSED] ?: 0) + 1
                preferences[PreferencesKeys.QUIZZES_PASSED] = passedCount
            }
        }

        // Add achievement if perfect score
        if (percentage == 100) {
            addAchievement(
                Achievement(
                    icon = "🎯",
                    title = "Perfect Score!",
                    description = "Got 100% on a quiz",
                    date = Calendar.getInstance().toDateString()
                )
            )
        }

        return QuizResult(
            score = percentage,
            passed = passed,
            correctAnswers = correctCount,
            totalQuestions = quizState.questions.size,
            rewardMinutes = if (passed) quizState.rewardMinutes else 0,
            earnedPoints = earnedPoints
        )
    }

    // ==================== ACHIEVEMENTS ====================

    /**
     * Get all earned achievements
     */
    val achievements: Flow<List<Achievement>> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[PreferencesKeys.ACHIEVEMENTS] ?: "[]"
            try {
                json.decodeFromString<List<Achievement>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Add new achievement
     */
    suspend fun addAchievement(achievement: Achievement) {
        context.dataStore.edit { preferences ->
            val current = try {
                val jsonString = preferences[PreferencesKeys.ACHIEVEMENTS] ?: "[]"
                json.decodeFromString<List<Achievement>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }

            // Check if already earned
            if (current.none { it.title == achievement.title }) {
                val updated = current + achievement
                preferences[PreferencesKeys.ACHIEVEMENTS] = json.encodeToString(updated)
            }
        }
    }

    /**
     * Check and award streak achievements
     */
    suspend fun checkStreakAchievements(streakDays: Int) {
        when (streakDays) {
            3 -> addAchievement(Achievement("🔥", "Getting Started", "3-day study streak", Calendar.getInstance().toDateString()))
            7 -> addAchievement(Achievement("⚡", "Week Warrior", "7-day study streak", Calendar.getInstance().toDateString()))
            30 -> addAchievement(Achievement("🏆", "Month Master", "30-day study streak!", Calendar.getInstance().toDateString()))
        }
    }

    // ==================== STUDY SESSIONS ====================

    /**
     * Get recent study sessions
     */
    val recentSessions: Flow<List<StudySession>> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[PreferencesKeys.STUDY_SESSIONS] ?: "[]"
            try {
                json.decodeFromString<List<StudySession>>(jsonString)
                    .sortedByDescending { parseDate(it.date) }
                    .take(10)
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Log new study session
     */
    suspend fun logSession(session: StudySession) {
        context.dataStore.edit { preferences ->
            val current = try {
                val jsonString = preferences[PreferencesKeys.STUDY_SESSIONS] ?: "[]"
                json.decodeFromString<List<StudySession>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }

            val updated = (listOf(session) + current).take(50) // Keep last 50
            preferences[PreferencesKeys.STUDY_SESSIONS] = json.encodeToString(updated)
        }
    }

    private fun parseDate(dateString: String): Long {
        return try {
            val parts = dateString.split("-")
            val cal = Calendar.getInstance()
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            cal.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }

    // ==================== PROGRESS DATA ====================

    /**
     * Get complete progress data for UI
     */
    fun getProgressData(): Flow<ProgressData> = combine(
        totalStudyTimeMinutes,
        currentStreak,
        context.dataStore.data.map { getCompletedModuleIds(it).size },
        recentSessions,
        achievements
    ) { totalMinutes, streak, completedCount, sessions, achievementList ->

        // Calculate weekly data
        val weeklyData = generateWeeklyData(sessions)

        // Calculate average score
        val avgScore = calculateAverageScore(sessions)

        ProgressData(
            totalStudyHours = formatStudyTime(totalMinutes),
            quizzesPassed = context.dataStore.data.first()[PreferencesKeys.QUIZZES_PASSED] ?: 0,
            currentStreak = streak,
            avgScore = avgScore,
            weeklyData = weeklyData,
            recentAchievements = achievementList.take(3),
            recentSessions = sessions.take(5),
            emergencyUnlocksThisWeek = EmergencyUnlockPolicy.usedThisWeek(
                getEmergencyUnlocks(context.dataStore.data.first()),
                System.currentTimeMillis()
            )
        )
    }

    private fun generateWeeklyData(sessions: List<StudySession>): List<DailyStudyData> {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val today = Calendar.getInstance()
        // Calendar.SUNDAY == 1, so this maps Monday -> 0 ... Sunday -> 6
        val daysSinceMonday = (today.get(Calendar.DAY_OF_WEEK) + 5) % 7

        return days.mapIndexed { index, dayName ->
            val dayCal = today.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, index - daysSinceMonday)
            val dateStr = dayCal.toDateString()

            val minutes = sessions
                .filter { it.date == dateStr }
                .sumOf { parseDuration(it.duration) }

            DailyStudyData(dayName, minutes.coerceAtMost(180)) // Cap at 3 hours for chart
        }
    }

    private fun parseDuration(duration: String): Int {
        return try {
            val parts = duration.split(" ")
            val num = parts[0].toInt()
            when {
                parts[1].contains("h") -> num * 60
                parts[1].contains("m") -> num
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun calculateAverageScore(sessions: List<StudySession>): String {
        val scoredSessions = sessions.filter { it.quizScore != null }
        return if (scoredSessions.isNotEmpty()) {
            val avg = scoredSessions.map { it.quizScore!! }.average().toInt()
            "$avg"
        } else {
            "0"
        }
    }

    // ==================== CONTENT MANAGEMENT ====================

    /**
     * Get study content by ID
     */
    fun getContent(contentId: String): Flow<StudyContent> = flow {
        val content = when (contentId) {
            "formulas" -> StudyContent(
                id = contentId,
                title = "Essential Formulas",
                body = buildString {
                    appendLine("## Area Formulas")
                    appendLine("• Circle: A = πr²")
                    appendLine("• Rectangle: A = l × w")
                    appendLine("• Triangle: A = ½ × b × h")
                    appendLine()
                    appendLine("## Algebraic Formulas")
                    appendLine("• Quadratic Formula: x = (-b ± √(b²-4ac)) / 2a")
                    appendLine("• Pythagorean Theorem: a² + b² = c²")
                    appendLine("• Slope: m = (y₂-y₁) / (x₂-x₁)")
                    appendLine()
                    appendLine("## Physics Formulas")
                    appendLine("• Force: F = ma")
                    appendLine("• Velocity: v = d/t")
                    appendLine("• Kinetic Energy: KE = ½mv²")
                },
                requiredReadTime = 2,
                readTime = 5,
                subject = "Mathematics"
            )
            "periodic_table" -> StudyContent(
                id = contentId,
                title = "Periodic Table Guide",
                body = buildString {
                    appendLine("## Structure")
                    appendLine("The periodic table organizes elements by atomic number and properties.")
                    appendLine()
                    appendLine("## Groups (Columns)")
                    appendLine("• Group 1: Alkali metals (very reactive)")
                    appendLine("• Group 2: Alkaline earth metals")
                    appendLine("• Groups 3-12: Transition metals")
                    appendLine("• Group 17: Halogens")
                    appendLine("• Group 18: Noble gases (inert)")
                    appendLine()
                    appendLine("## Periods (Rows)")
                    appendLine("Elements in the same period have the same number of electron shells.")
                    appendLine()
                    appendLine("## Key Elements to Remember")
                    appendLine("• H (Hydrogen) - Atomic #1")
                    appendLine("• C (Carbon) - Basis of life")
                    appendLine("• O (Oxygen) - Essential for respiration")
                    appendLine("• Au (Gold) - Valuable metal")
                },
                requiredReadTime = 3,
                readTime = 8,
                subject = "Chemistry"
            )
            "physics_laws" -> StudyContent(
                id = contentId,
                title = "Newton's Laws of Motion",
                body = buildString {
                    appendLine("## First Law (Inertia)")
                    appendLine("An object at rest stays at rest, and an object in motion stays in motion unless acted upon by an external force.")
                    appendLine()
                    appendLine("## Second Law (F = ma)")
                    appendLine("The acceleration of an object depends on the net force acting on it and its mass.")
                    appendLine("• More force = more acceleration")
                    appendLine("• More mass = less acceleration")
                    appendLine()
                    appendLine("## Third Law (Action-Reaction)")
                    appendLine("For every action, there is an equal and opposite reaction.")
                    appendLine("• Forces always come in pairs")
                    appendLine("• The two forces act on different objects")
                },
                requiredReadTime = 2,
                readTime = 6,
                subject = "Physics"
            )
            else -> StudyContent(
                id = contentId,
                title = "Study Material",
                body = "Content not found. Please try another resource.",
                requiredReadTime = 1,
                readTime = 1,
                subject = "General"
            )
        }
        emit(content)
    }

    /**
     * Mark content as read
     */
    suspend fun markContentAsRead(contentId: String) {
        val content = getContent(contentId).first()
        recordStudySession(
            name = "Read: ${content.title}",
            minutes = content.requiredReadTime.toLong(),
            completed = true,
            quizScore = null
        )
    }

    // ==================== SECURITY ====================

    /**
     * Set emergency PIN
     */
    suspend fun setEmergencyPin(pin: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EMERGENCY_PIN_HASH] = hashPin(pin)
        }
    }

    /**
     * Verify emergency PIN
     */
    suspend fun verifyEmergencyPin(pin: String): Boolean {
        val storedHash = context.dataStore.data.first()[PreferencesKeys.EMERGENCY_PIN_HASH]
        return storedHash != null && storedHash == hashPin(pin)
    }

    /**
     * Check if PIN is set
     */
    val isPinSet: Flow<Boolean> = context.dataStore.data
        .map { it[PreferencesKeys.EMERGENCY_PIN_HASH] != null }

    /**
     * Emergency unlocks left today. Recomputed on each subscription, so a screen
     * opened after midnight sees the reset.
     */
    val emergencyUnlocksLeftToday: Flow<Int> = context.dataStore.data
        .map { EmergencyUnlockPolicy.remainingToday(getEmergencyUnlocks(it), System.currentTimeMillis()) }

    /**
     * Records an emergency unlock if today's limit allows it. The check and the write
     * happen in one edit so repeated taps can't exceed the limit.
     * @return true if the unlock was recorded, false if the daily limit is used up
     */
    suspend fun tryRecordEmergencyUnlock(): Boolean {
        var recorded = false
        context.dataStore.edit { preferences ->
            val updated = EmergencyUnlockPolicy.tryRecord(getEmergencyUnlocks(preferences), System.currentTimeMillis())
            if (updated != null) {
                preferences[PreferencesKeys.EMERGENCY_UNLOCKS] = json.encodeToString(updated)
                recorded = true
            }
        }
        return recorded
    }

    private fun getEmergencyUnlocks(preferences: Preferences): List<Long> {
        val jsonString = preferences[PreferencesKeys.EMERGENCY_UNLOCKS] ?: "[]"
        return try {
            json.decodeFromString<List<Long>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun hashPin(pin: String): String {
        // In production, use proper hashing like BCrypt
        // This is a simple hash for demonstration
        return pin.hashCode().toString() + "salt" + pin.reversed()
    }

    // ==================== DAILY QUOTE ====================

    private val quotes = listOf(
        "The expert in anything was once a beginner.",
        "Success is the sum of small efforts, repeated day in and day out.",
        "Don't watch the clock; do what it does. Keep going.",
        "The future belongs to those who believe in the beauty of their dreams.",
        "Strive for progress, not perfection.",
        "It always seems impossible until it's done.",
        "Learning is never done without errors and defeat.",
        "The beautiful thing about learning is that no one can take it away from you."
    )

    /**
     * Get daily motivational quote
     */
    val dailyQuote: Flow<String> = context.dataStore.data
        .map { preferences ->
            val savedDate = preferences[PreferencesKeys.QUOTE_DATE]
            val today = Calendar.getInstance().toDateString()

            if (savedDate == today) {
                preferences[PreferencesKeys.DAILY_QUOTE] ?: quotes.random()
            } else {
                // New day, new quote
                val newQuote = quotes.random()
                context.dataStore.edit {
                    it[PreferencesKeys.DAILY_QUOTE] = newQuote
                    it[PreferencesKeys.QUOTE_DATE] = today
                }
                newQuote
            }
        }

    // ==================== SETTINGS ====================

    /**
     * App settings data class
     */
    @kotlinx.serialization.Serializable
    data class AppSettings(
        val notificationsEnabled: Boolean = true,
        val soundEnabled: Boolean = true,
        val vibrationEnabled: Boolean = true,
        val defaultQuizDifficulty: Difficulty = Difficulty.MEDIUM,
        val dailyGoalMinutes: Int = 60,
        val theme: String = "system"
    )

    /**
     * Get app settings
     */
    val settings: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            val jsonString = preferences[PreferencesKeys.SETTINGS]
            try {
                jsonString?.let { json.decodeFromString<AppSettings>(it) } ?: AppSettings()
            } catch (e: Exception) {
                AppSettings()
            }
        }

    /**
     * Update app settings
     */
    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SETTINGS] = json.encodeToString(settings)
        }
    }

    // ==================== DATA EXPORT/IMPORT ====================

    /**
     * Export all user data as JSON
     */
    suspend fun exportData(): String {
        val prefs = context.dataStore.data.first()

        val exportData = ExportData(
            userId = prefs[PreferencesKeys.USER_ID] ?: "",
            totalStudyTime = prefs[PreferencesKeys.TOTAL_STUDY_TIME_MINUTES] ?: 0L,
            quizzesPassed = prefs[PreferencesKeys.QUIZZES_PASSED] ?: 0,
            currentStreak = prefs[PreferencesKeys.CURRENT_STREAK_DAYS] ?: 0,
            completedModules = getCompletedModuleIds(prefs).toList(),
            achievements = try {
                prefs[PreferencesKeys.ACHIEVEMENTS]?.let { json.decodeFromString(it) } ?: emptyList()
            } catch (e: Exception) { emptyList() },
            sessions = try {
                prefs[PreferencesKeys.STUDY_SESSIONS]?.let { json.decodeFromString(it) } ?: emptyList()
            } catch (e: Exception) { emptyList() },
            exportDate = Calendar.getInstance().toDateString()
        )

        return json.encodeToString(exportData)
    }

    /**
     * Import data from JSON (for backup restore)
     */
    suspend fun importData(jsonData: String): Boolean {
        return try {
            val data = json.decodeFromString<ExportData>(jsonData)

            context.dataStore.edit { prefs ->
                prefs[PreferencesKeys.TOTAL_STUDY_TIME_MINUTES] = data.totalStudyTime
                prefs[PreferencesKeys.QUIZZES_PASSED] = data.quizzesPassed
                prefs[PreferencesKeys.CURRENT_STREAK_DAYS] = data.currentStreak
                prefs[PreferencesKeys.COMPLETED_MODULES] = json.encodeToString(data.completedModules)
                prefs[PreferencesKeys.ACHIEVEMENTS] = json.encodeToString(data.achievements)
                prefs[PreferencesKeys.STUDY_SESSIONS] = json.encodeToString(data.sessions)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== ANALYTICS ====================

    /**
     * Get study statistics summary
     */
    suspend fun getStatistics(): StudyStatistics {
        val prefs = context.dataStore.data.first()
        val sessions = try {
            prefs[PreferencesKeys.STUDY_SESSIONS]?.let {
                json.decodeFromString<List<StudySession>>(it)
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }

        val totalTime = prefs[PreferencesKeys.TOTAL_STUDY_TIME_MINUTES] ?: 0L
        val studyDays = sessions.map { it.date }.distinct().size

        return StudyStatistics(
            totalStudyMinutes = totalTime,
            totalSessions = sessions.size,
            averageSessionLength = if (sessions.isNotEmpty()) totalTime / sessions.size else 0,
            studyDays = studyDays,
            quizzesAttempted = prefs[PreferencesKeys.QUIZZES_ATTEMPTED] ?: 0,
            quizzesPassed = prefs[PreferencesKeys.QUIZZES_PASSED] ?: 0,
            completionRate = calculateCompletionRate(sessions),
            favoriteSubject = findFavoriteSubject(sessions)
        )
    }

    private fun calculateCompletionRate(sessions: List<StudySession>): Float {
        return if (sessions.isEmpty()) 0f
        else (sessions.count { it.completed }.toFloat() / sessions.size) * 100
    }

    private fun findFavoriteSubject(sessions: List<StudySession>): String {
        return sessions
            .groupingBy { it.moduleName }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: "None"
    }
}

// ==================== DATA CLASSES ====================

/**
 * Result of submitting a quiz
 */
data class QuizResult(
    val score: Int,
    val passed: Boolean,
    val correctAnswers: Int,
    val totalQuestions: Int,
    val rewardMinutes: Int,
    val earnedPoints: Int
)

/**
 * Data for export/backup
 */
@kotlinx.serialization.Serializable
data class ExportData(
    val userId: String,
    val totalStudyTime: Long,
    val quizzesPassed: Int,
    val currentStreak: Int,
    val completedModules: List<String>,
    val achievements: List<Achievement>,
    val sessions: List<StudySession>,
    val exportDate: String
)

/**
 * Comprehensive study statistics
 */
data class StudyStatistics(
    val totalStudyMinutes: Long,
    val totalSessions: Int,
    val averageSessionLength: Long,
    val studyDays: Int,
    val quizzesAttempted: Int,
    val quizzesPassed: Int,
    val completionRate: Float,
    val favoriteSubject: String
)