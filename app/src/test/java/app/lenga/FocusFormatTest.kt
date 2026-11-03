package app.lenga

import app.lenga.focus.FocusFormat
import app.lenga.focus.ModeSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class FocusFormatTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private val en = Locale.ENGLISH
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 9, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun remainingRoundsUpToWholeMinutes() {
        assertEquals("0m", FocusFormat.remaining(0))
        assertEquals("2m", FocusFormat.remaining(90_000))
        assertEquals("1h 23m", FocusFormat.remaining((60 + 23) * 60_000L))
        assertEquals("2h 0m", FocusFormat.remaining(120 * 60_000L))
    }

    @Test
    fun minuteOfDayIsZeroPadded() {
        assertEquals("08:05", FocusFormat.minuteOfDay(485))
        assertEquals("00:00", FocusFormat.minuteOfDay(0))
    }

    @Test
    fun weeklyDaysCollapseIntoRanges() {
        assertEquals("Mon–Fri 08:00–16:00", FocusFormat.schedule(ModeSchedule.Weekly(setOf(1, 2, 3, 4, 5), 480, 960), zone, en))
        assertEquals("Mon, Wed 22:00–06:00", FocusFormat.schedule(ModeSchedule.Weekly(setOf(3, 1), 1320, 360), zone, en))
        assertEquals("Every day 09:00–10:00", FocusFormat.schedule(ModeSchedule.Weekly((1..7).toSet(), 540, 600), zone, en))
    }

    @Test
    fun oneOffShowsTheEndDateOnlyWhenItDiffers() {
        assertEquals("Sat 26 Sep 09:00 – 13:00", FocusFormat.schedule(ModeSchedule.OneOff(at(26, 9), at(26, 13)), zone, en))
        assertEquals("Sat 26 Sep 22:00 – Sun 27 Sep 06:00", FocusFormat.schedule(ModeSchedule.OneOff(at(26, 22), at(27, 6)), zone, en))
    }

    @Test
    fun detoxShowsItsLastDay() {
        assertEquals("Until the end of Sun 27 Sep", FocusFormat.schedule(ModeSchedule.Detox(at(26, 9), at(28, 0)), zone, en))
    }
}
