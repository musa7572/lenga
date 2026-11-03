package app.lenga

import app.lenga.focus.ActiveMode
import app.lenga.focus.Decision
import app.lenga.focus.FocusMode
import app.lenga.focus.FocusPolicy
import app.lenga.focus.ModeSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FocusPolicyTest {

    private val nairobi = ZoneId.of("Africa/Nairobi")
    private val london = ZoneId.of("Europe/London")

    private fun at(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun mode(schedule: ModeSchedule, allowed: Set<String> = emptySet(), name: String = "Mode") =
        FocusMode(id = name, name = name, schedule = schedule, allowedPackages = allowed)

    // 2026-09-28 is a Monday
    private val schoolWeek = ModeSchedule.Weekly(setOf(1, 2, 3, 4, 5), 8 * 60, 16 * 60)

    private fun active(now: Long, schedule: ModeSchedule, zone: ZoneId = nairobi) =
        FocusPolicy.activeModes(now, listOf(mode(schedule)), zone)

    @Test
    fun weeklyIsActiveOnlyInsideItsWindow() {
        assertTrue(active(at(nairobi, 2026, 9, 28, 7, 59), schoolWeek).isEmpty())
        assertEquals(1, active(at(nairobi, 2026, 9, 28, 8, 0), schoolWeek).size)
        assertEquals(1, active(at(nairobi, 2026, 9, 28, 15, 59), schoolWeek).size)
        assertTrue(active(at(nairobi, 2026, 9, 28, 16, 0), schoolWeek).isEmpty())
        assertTrue(active(at(nairobi, 2026, 10, 3, 10, 0), schoolWeek).isEmpty()) // Saturday
    }

    @Test
    fun weeklyWindowReportsItsStartAndEnd() {
        val window = active(at(nairobi, 2026, 9, 28, 10, 0), schoolWeek).single()
        assertEquals(at(nairobi, 2026, 9, 28, 8, 0), window.start)
        assertEquals(at(nairobi, 2026, 9, 28, 16, 0), window.end)
    }

    @Test
    fun overnightWindowBelongsToTheDayItStarts() {
        val mondayNight = ModeSchedule.Weekly(setOf(1), 22 * 60, 6 * 60)
        val window = active(at(nairobi, 2026, 9, 29, 5, 0), mondayNight).single() // Tuesday 05:00
        assertEquals(at(nairobi, 2026, 9, 29, 6, 0), window.end)
        assertTrue(active(at(nairobi, 2026, 9, 28, 21, 59), mondayNight).isEmpty())
        // Monday 05:00 would belong to a Sunday-night window, and Sunday isn't selected
        assertTrue(active(at(nairobi, 2026, 9, 28, 5, 0), mondayNight).isEmpty())
    }

    @Test
    fun oneOffStartIsInclusiveAndEndExclusive() {
        val exam = ModeSchedule.OneOff(at(nairobi, 2026, 10, 3, 9), at(nairobi, 2026, 10, 3, 13))
        assertTrue(active(at(nairobi, 2026, 10, 3, 8, 59), exam).isEmpty())
        assertEquals(1, active(at(nairobi, 2026, 10, 3, 9, 0), exam).size)
        assertTrue(active(at(nairobi, 2026, 10, 3, 13, 0), exam).isEmpty())
    }

    @Test
    fun springForwardGapShiftsTheStartLater() {
        // 2026-03-29: London clocks jump from 01:00 to 02:00, so 01:30 doesn't exist that day
        val sunday = ModeSchedule.Weekly(setOf(7), 90, 180)
        assertTrue(active(at(london, 2026, 3, 29, 2, 15), sunday, london).isEmpty())
        val window = active(at(london, 2026, 3, 29, 2, 45), sunday, london).single()
        assertEquals(at(london, 2026, 3, 29, 2, 30), window.start)
    }

    @Test
    fun fallBackWindowEndsAtTheLocalEndTime() {
        // 2026-10-25: London clocks go back from 02:00 to 01:00
        val sunday = ModeSchedule.Weekly(setOf(7), 30, 180)
        assertEquals(1, active(at(london, 2026, 10, 25, 2, 59), sunday, london).size)
        assertTrue(active(at(london, 2026, 10, 25, 3, 0), sunday, london).isEmpty())
    }

    @Test
    fun nextStartSkipsToTheNextSelectedDay() {
        assertEquals(
            at(nairobi, 2026, 10, 5, 8, 0),
            FocusPolicy.nextStart(schoolWeek, at(nairobi, 2026, 10, 3, 10, 0), nairobi) // Saturday -> Monday
        )
        assertEquals(
            at(nairobi, 2026, 9, 29, 8, 0),
            FocusPolicy.nextStart(schoolWeek, at(nairobi, 2026, 9, 28, 9, 0), nairobi) // running -> tomorrow
        )
    }

    @Test
    fun nextStartOfAPastOneOffIsNull() {
        val exam = ModeSchedule.OneOff(at(nairobi, 2026, 9, 1, 9), at(nairobi, 2026, 9, 1, 13))
        assertNull(FocusPolicy.nextStart(exam, at(nairobi, 2026, 9, 28, 9), nairobi))
    }

    private val essentials = setOf("com.android.dialer")
    private val start = at(nairobi, 2026, 9, 28, 8)
    private val end = at(nairobi, 2026, 9, 28, 16)

    private fun running(allowed: Set<String>, name: String = "School week", until: Long = end) =
        ActiveMode(mode(schoolWeek, allowed, name), start, until)

    private fun decide(
        pkg: String,
        active: List<ActiveMode> = emptyList(),
        freeTime: Boolean = false,
        emergency: Boolean = false
    ) = FocusPolicy.decide(pkg, active, essentials, freeTime, emergency)

    @Test
    fun essentialsAreAlwaysAllowed() {
        assertEquals(Decision.Allow, decide("com.android.dialer", listOf(running(emptySet()))))
        assertEquals(Decision.Allow, decide("com.android.dialer"))
    }

    @Test
    fun withoutModesAppsNeedFreeTime() {
        assertEquals(Decision.NoFreeTime, decide("com.instagram.android"))
        assertEquals(Decision.Allow, decide("com.instagram.android", freeTime = true))
        assertEquals(Decision.Allow, decide("com.android.settings"))
    }

    @Test
    fun anActiveModeAllowsOnlyItsApps() {
        val active = listOf(running(setOf("com.whatsapp")))
        assertEquals(Decision.Allow, decide("com.whatsapp", active))
        assertEquals(Decision.BlockedByMode("School week", end), decide("com.instagram.android", active))
    }

    @Test
    fun overlappingModesMustAllAllowTheApp() {
        val later = at(nairobi, 2026, 9, 28, 18)
        val active = listOf(
            running(setOf("com.whatsapp", "com.google.android.apps.docs"), name = "School week"),
            running(setOf("com.google.android.apps.docs"), name = "Exam prep", until = later)
        )
        assertEquals(Decision.Allow, decide("com.google.android.apps.docs", active))
        assertEquals(Decision.BlockedByMode("Exam prep", later), decide("com.whatsapp", active))
    }

    @Test
    fun settingsAreLockedDuringAModeEvenIfListed() {
        val locked = FocusPolicy.SETTINGS_PACKAGES
        val active = listOf(running(locked))
        locked.forEach { assertEquals(Decision.SettingsLocked, decide(it, active)) }
    }

    @Test
    fun emergencyOverridesModesButFreeTimeDoesNot() {
        val active = listOf(running(emptySet()))
        assertEquals(Decision.Allow, decide("com.instagram.android", active, emergency = true))
        assertEquals(Decision.Allow, decide("com.android.settings", active, emergency = true))
        assertEquals(Decision.BlockedByMode("School week", end), decide("com.instagram.android", active, freeTime = true))
    }
}
