package app.lenga

import app.lenga.data.model.EmergencyUnlockPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EmergencyUnlockPolicyTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    // 2026-09-26 is a Saturday
    private val saturdayNoon = at(2026, 9, 26)

    @Test
    fun allowsTwoUnlocksPerDayThenRefuses() {
        val first = EmergencyUnlockPolicy.tryRecord(emptyList(), saturdayNoon, utc)
        assertNotNull(first)
        val second = EmergencyUnlockPolicy.tryRecord(first!!, saturdayNoon + 60_000, utc)
        assertNotNull(second)
        assertNull(EmergencyUnlockPolicy.tryRecord(second!!, saturdayNoon + 120_000, utc))
        assertEquals(0, EmergencyUnlockPolicy.remainingToday(second, saturdayNoon + 120_000, utc))
    }

    @Test
    fun limitResetsAtMidnight() {
        val lateLastNight = listOf(at(2026, 9, 25, 23, 0), at(2026, 9, 25, 23, 30))
        assertEquals(2, EmergencyUnlockPolicy.remainingToday(lateLastNight, at(2026, 9, 26, 0, 1), utc))
    }

    @Test
    fun weekStartsOnMonday() {
        val unlocks = listOf(
            at(2026, 9, 20), // Sunday of the previous week
            at(2026, 9, 21, 0, 5), // Monday
            at(2026, 9, 24) // Thursday
        )
        assertEquals(2, EmergencyUnlockPolicy.usedThisWeek(unlocks, saturdayNoon, utc))
    }

    @Test
    fun sundayCountsTowardTheWeekThatStartedMonday() {
        val unlocks = listOf(at(2026, 9, 21), at(2026, 9, 27, 9, 0))
        assertEquals(2, EmergencyUnlockPolicy.usedThisWeek(unlocks, at(2026, 9, 27, 10, 0), utc))
    }

    @Test
    fun recordingPrunesOldEntries() {
        val old = at(2026, 9, 1)
        val updated = EmergencyUnlockPolicy.tryRecord(listOf(old), saturdayNoon, utc)
        assertEquals(listOf(saturdayNoon), updated)
    }
}
