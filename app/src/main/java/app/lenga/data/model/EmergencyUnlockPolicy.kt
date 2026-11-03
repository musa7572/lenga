package app.lenga.data.model

import java.util.Calendar
import java.util.TimeZone

/**
 * Rules for emergency unlocks: how many are allowed per day and how usage is counted.
 * Pure functions over timestamps so they can be unit tested without Android.
 */
object EmergencyUnlockPolicy {
    const val DAILY_LIMIT = 2

    /** How long unlock timestamps are kept; enough to cover the current week. */
    private const val RETENTION_MILLIS = 8L * 24 * 60 * 60 * 1000

    fun startOfDay(now: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Midnight at the start of the current week, which begins on Monday (matching the weekly chart). */
    fun startOfWeek(now: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = startOfDay(now, timeZone)
            // Calendar.SUNDAY == 1, so this maps Monday -> 0 ... Sunday -> 6
            val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
            add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        }.timeInMillis

    fun usedToday(unlocks: List<Long>, now: Long, timeZone: TimeZone = TimeZone.getDefault()): Int {
        val start = startOfDay(now, timeZone)
        return unlocks.count { it in start..now }
    }

    fun usedThisWeek(unlocks: List<Long>, now: Long, timeZone: TimeZone = TimeZone.getDefault()): Int {
        val start = startOfWeek(now, timeZone)
        return unlocks.count { it in start..now }
    }

    fun remainingToday(unlocks: List<Long>, now: Long, timeZone: TimeZone = TimeZone.getDefault()): Int =
        (DAILY_LIMIT - usedToday(unlocks, now, timeZone)).coerceAtLeast(0)

    /**
     * Returns the updated timestamp list with [now] recorded, or null if today's limit is used up.
     * Old entries are pruned so the stored list stays small.
     */
    fun tryRecord(unlocks: List<Long>, now: Long, timeZone: TimeZone = TimeZone.getDefault()): List<Long>? {
        if (remainingToday(unlocks, now, timeZone) == 0) return null
        return unlocks.filter { it > now - RETENTION_MILLIS } + now
    }
}
