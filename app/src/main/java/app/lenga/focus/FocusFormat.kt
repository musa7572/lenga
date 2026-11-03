package app.lenga.focus

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Text for focus-mode UI, notifications and toasts. */
object FocusFormat {
    private val clockPattern = DateTimeFormatter.ofPattern("HH:mm")

    /** Time left, rounded up to whole minutes: "1h 23m" or "12m". */
    fun remaining(millis: Long): String {
        val minutes = (millis.coerceAtLeast(0) + 59_999) / 60_000
        val hours = minutes / 60
        return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
    }

    fun minuteOfDay(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

    fun clock(epochMillis: Long, zone: ZoneId): String = local(epochMillis, zone).format(clockPattern)

    fun dayTime(epochMillis: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        local(epochMillis, zone).format(DateTimeFormatter.ofPattern("EEE HH:mm", locale))

    fun date(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

    fun schedule(schedule: ModeSchedule, zone: ZoneId, locale: Locale = Locale.getDefault()): String = when (schedule) {
        is ModeSchedule.Weekly ->
            "${days(schedule.days, locale)} ${minuteOfDay(schedule.fromMinute)}–${minuteOfDay(schedule.toMinute)}"
        is ModeSchedule.OneOff -> span(schedule.start, schedule.end, zone, locale)
        is ModeSchedule.Timed -> "Until ${clock(schedule.end, zone)}"
        // A detox day ends at midnight, so describe the last full day rather than 00:00
        is ModeSchedule.Detox -> "Until the end of ${date(local(schedule.end - 1, zone).toLocalDate(), locale)}"
    }

    private fun days(days: Set<Int>, locale: Locale): String {
        val sorted = days.sorted()
        fun name(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)
        return when {
            sorted.size == 7 -> "Every day"
            sorted.size >= 3 && sorted.last() - sorted.first() + 1 == sorted.size ->
                "${name(sorted.first())}–${name(sorted.last())}"
            else -> sorted.joinToString(", ") { name(it) }
        }
    }

    private fun span(start: Long, end: Long, zone: ZoneId, locale: Locale): String {
        val from = local(start, zone)
        val to = local(end, zone)
        val startText = "${date(from.toLocalDate(), locale)} ${from.format(clockPattern)}"
        val endText = if (from.toLocalDate() == to.toLocalDate()) {
            to.format(clockPattern)
        } else {
            "${date(to.toLocalDate(), locale)} ${to.format(clockPattern)}"
        }
        return "$startText – $endText"
    }

    private fun local(epochMillis: Long, zone: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
}
