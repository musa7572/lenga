package app.lenga.focus

import java.time.ZoneId

/** Checks applied before a mode is saved or deleted. Each returns an error message or null. */
object ModeValidation {
    const val RUNNING_ERROR = "This mode is running and can't be changed until it ends."

    /** A mode can't be edited or deleted while it is active. [existing] is null for a new mode. */
    fun checkModify(existing: FocusMode?, now: Long, zone: ZoneId): String? =
        if (existing != null && FocusPolicy.window(existing.schedule, now, zone) != null) RUNNING_ERROR else null

    fun validate(mode: FocusMode, now: Long): String? {
        if (mode.name.isBlank()) return "Give the mode a name."
        return when (val schedule = mode.schedule) {
            is ModeSchedule.Weekly -> when {
                schedule.days.isEmpty() -> "Pick at least one day."
                schedule.days.any { it !in 1..7 } ||
                    schedule.fromMinute !in 0 until MINUTES_PER_DAY ||
                    schedule.toMinute !in 0 until MINUTES_PER_DAY -> "That time isn't valid."
                schedule.fromMinute == schedule.toMinute -> "Start and end times must be different."
                else -> null
            }
            is ModeSchedule.OneOff -> checkSpan(schedule.start, schedule.end, now)
            is ModeSchedule.Timed -> checkSpan(schedule.start, schedule.end, now)
            is ModeSchedule.Detox -> checkSpan(schedule.start, schedule.end, now)
        }
    }

    private fun checkSpan(start: Long, end: Long, now: Long): String? = when {
        end <= start -> "The end must be after the start."
        end <= now -> "The end must be in the future."
        else -> null
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
