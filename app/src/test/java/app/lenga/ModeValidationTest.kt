package app.lenga

import app.lenga.focus.FocusMode
import app.lenga.focus.ModeSchedule
import app.lenga.focus.ModeValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ModeValidationTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private fun at(day: Int, hour: Int) =
        LocalDateTime.of(2026, 9, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val now = at(28, 10) // Monday 10:00
    private val schoolWeek = FocusMode("s", "School week", ModeSchedule.Weekly(setOf(1, 2, 3, 4, 5), 480, 960))

    @Test
    fun acceptsAValidWeeklyMode() {
        assertNull(ModeValidation.validate(schoolWeek, now))
    }

    @Test
    fun rejectsBlankNames() {
        assertNotNull(ModeValidation.validate(schoolWeek.copy(name = "  "), now))
    }

    @Test
    fun rejectsWeeklyWithoutDaysOrWithEqualTimes() {
        assertNotNull(ModeValidation.validate(schoolWeek.copy(schedule = ModeSchedule.Weekly(emptySet(), 480, 960)), now))
        assertNotNull(ModeValidation.validate(schoolWeek.copy(schedule = ModeSchedule.Weekly(setOf(1), 480, 480)), now))
        assertNotNull(ModeValidation.validate(schoolWeek.copy(schedule = ModeSchedule.Weekly(setOf(1), 480, 1440)), now))
    }

    @Test
    fun rejectsOneOffsThatEndBeforeStartOrInThePast() {
        val backwards = FocusMode("e", "Exam", ModeSchedule.OneOff(at(29, 13), at(29, 9)))
        val past = FocusMode("e", "Exam", ModeSchedule.OneOff(at(27, 9), at(27, 13)))
        assertNotNull(ModeValidation.validate(backwards, now))
        assertNotNull(ModeValidation.validate(past, now))
        assertNull(ModeValidation.validate(FocusMode("e", "Exam", ModeSchedule.OneOff(at(29, 9), at(29, 13))), now))
    }

    @Test
    fun runningModesCantBeChanged() {
        assertEquals(ModeValidation.RUNNING_ERROR, ModeValidation.checkModify(schoolWeek, now, zone))
        assertNull(ModeValidation.checkModify(schoolWeek, at(27, 10), zone)) // Sunday: not running
        assertNull(ModeValidation.checkModify(null, now, zone)) // brand-new mode
    }
}
