package app.lenga.focus

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A mode that is active now, with the start and end of its current window (epoch millis). */
data class ActiveMode(val mode: FocusMode, val start: Long, val end: Long)

/** What to do when an app comes to the foreground. */
sealed interface Decision {
    data object Allow : Decision
    data object SettingsLocked : Decision
    data class BlockedByMode(val modeName: String, val until: Long) : Decision
    data object NoFreeTime : Decision
}

/**
 * Rules for focus modes. Pure functions over timestamps and a time zone, so they can be
 * unit tested without Android. Windows include their start and exclude their end.
 */
object FocusPolicy {

    /**
     * Apps that could switch Lenga off or remove it. Locked while a mode is active,
     * usable otherwise.
     */
    val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.securitycenter"
    )

    /**
     * Whether [pkg] may be used now. [active] are the modes active now; [essentials] are
     * always allowed; [freeTimeActive] is quiz free time; [emergencyActive] is an emergency unlock.
     */
    fun decide(
        pkg: String,
        active: List<ActiveMode>,
        essentials: Set<String>,
        freeTimeActive: Boolean,
        emergencyActive: Boolean
    ): Decision {
        if (pkg in essentials || emergencyActive) return Decision.Allow
        if (active.isNotEmpty()) {
            if (pkg in SETTINGS_PACKAGES) return Decision.SettingsLocked
            val blocker = active.firstOrNull { pkg !in it.mode.allowedPackages } ?: return Decision.Allow
            return Decision.BlockedByMode(blocker.mode.name, active.maxOf { it.end })
        }
        if (pkg in SETTINGS_PACKAGES || freeTimeActive) return Decision.Allow
        return Decision.NoFreeTime
    }

    fun activeModes(now: Long, modes: List<FocusMode>, zone: ZoneId): List<ActiveMode> =
        modes.mapNotNull { mode ->
            window(mode.schedule, now, zone)?.let { (start, end) -> ActiveMode(mode, start, end) }
        }

    /** The window of [schedule] that contains [now], as (start, end), or null if it isn't active. */
    fun window(schedule: ModeSchedule, now: Long, zone: ZoneId): Pair<Long, Long>? = when (schedule) {
        is ModeSchedule.Timed -> absolute(schedule.start, schedule.end, now)
        is ModeSchedule.Detox -> absolute(schedule.start, schedule.end, now)
        is ModeSchedule.OneOff -> absolute(schedule.start, schedule.end, now)
        is ModeSchedule.Weekly -> weeklyWindow(schedule, now, zone)
    }

    /** When [schedule] next starts after [now], or null if it never will. */
    fun nextStart(schedule: ModeSchedule, now: Long, zone: ZoneId): Long? = when (schedule) {
        is ModeSchedule.Weekly -> {
            val today = localDate(now, zone)
            (0L..7L).asSequence()
                .map { today.plusDays(it) }
                .filter { it.dayOfWeek.value in schedule.days }
                .map { startOf(it, schedule.fromMinute, zone) }
                .firstOrNull { it > now }
        }
        is ModeSchedule.OneOff -> schedule.start.takeIf { it > now }
        is ModeSchedule.Timed, is ModeSchedule.Detox -> null
    }

    /** Timed sessions and detox days are thrown away once they end. */
    fun isDisposable(schedule: ModeSchedule, now: Long): Boolean = when (schedule) {
        is ModeSchedule.Timed -> schedule.end <= now
        is ModeSchedule.Detox -> schedule.end <= now
        is ModeSchedule.Weekly, is ModeSchedule.OneOff -> false
    }

    private fun absolute(start: Long, end: Long, now: Long): Pair<Long, Long>? =
        if (now >= start && now < end) start to end else null

    private fun weeklyWindow(schedule: ModeSchedule.Weekly, now: Long, zone: ZoneId): Pair<Long, Long>? {
        val today = localDate(now, zone)
        // A window that crosses midnight belongs to the day it starts, so also check yesterday's
        for (date in listOf(today, today.minusDays(1))) {
            if (date.dayOfWeek.value !in schedule.days) continue
            val start = startOf(date, schedule.fromMinute, zone)
            val endDate = if (schedule.toMinute <= schedule.fromMinute) date.plusDays(1) else date
            val end = startOf(endDate, schedule.toMinute, zone)
            if (now >= start && now < end) return start to end
        }
        return null
    }

    /**
     * [minute] of [date] as epoch millis. A local time inside a spring-forward gap is moved later
     * by the length of the gap (java.time's rule), so 01:30 becomes 02:30.
     */
    private fun startOf(date: LocalDate, minute: Int, zone: ZoneId): Long =
        date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zone).toInstant().toEpochMilli()

    private fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
