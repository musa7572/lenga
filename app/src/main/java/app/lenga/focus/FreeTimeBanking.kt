package app.lenga.focus

/**
 * Unlock state. Quiz free time runs until [unlockedUntil]; [totalMillis] is its full length, for
 * progress display. [bankedMillis] is quiz free time paused by a focus mode. [emergencyUntil] is
 * a separate emergency unlock that also works during a focus mode.
 */
data class FreeTime(
    val unlockedUntil: Long = 0L,
    val totalMillis: Long = 0L,
    val bankedMillis: Long = 0L,
    val emergencyUntil: Long = 0L
)

/** Pure arithmetic for granting, pausing and resuming quiz free time around focus modes. */
object FreeTimeBanking {

    /** Adds [minutes] of quiz free time: banked during a session, otherwise started or extended. */
    fun grant(freeTime: FreeTime, minutes: Int, sessionActive: Boolean, now: Long): FreeTime {
        if (minutes <= 0) return freeTime
        val grantMillis = minutes * 60_000L
        if (sessionActive) return freeTime.copy(bankedMillis = freeTime.bankedMillis + grantMillis)
        val stillRunning = freeTime.unlockedUntil > now
        val start = if (stillRunning) freeTime.unlockedUntil else now
        return freeTime.copy(
            unlockedUntil = start + grantMillis,
            totalMillis = if (stillRunning) freeTime.totalMillis + grantMillis else grantMillis
        )
    }

    /**
     * Pauses or resumes free time to match whether a session is running. [sessionStart] is the
     * earliest start among active modes, or null when none is active. Safe to call repeatedly.
     */
    fun sync(freeTime: FreeTime, sessionStart: Long?, now: Long): FreeTime = when {
        sessionStart != null && freeTime.unlockedUntil > sessionStart -> freeTime.copy(
            unlockedUntil = 0L,
            totalMillis = 0L,
            bankedMillis = freeTime.bankedMillis + (freeTime.unlockedUntil - sessionStart)
        )
        sessionStart == null && freeTime.bankedMillis > 0 -> freeTime.copy(
            unlockedUntil = now + freeTime.bankedMillis,
            totalMillis = freeTime.bankedMillis,
            bankedMillis = 0L
        )
        else -> freeTime
    }
}
