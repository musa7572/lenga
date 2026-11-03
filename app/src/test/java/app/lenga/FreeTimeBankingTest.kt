package app.lenga

import app.lenga.focus.FreeTime
import app.lenga.focus.FreeTimeBanking
import org.junit.Assert.assertEquals
import org.junit.Test

class FreeTimeBankingTest {

    private val minute = 60_000L
    private val now = 1_000_000_000L

    @Test
    fun grantOutsideASessionStartsOrExtendsFreeTime() {
        val started = FreeTimeBanking.grant(FreeTime(), 10, sessionActive = false, now = now)
        assertEquals(FreeTime(unlockedUntil = now + 10 * minute, totalMillis = 10 * minute), started)
        val extended = FreeTimeBanking.grant(started, 5, sessionActive = false, now = now + minute)
        assertEquals(now + 15 * minute, extended.unlockedUntil)
        assertEquals(15 * minute, extended.totalMillis)
    }

    @Test
    fun grantDuringASessionIsBanked() {
        val banked = FreeTimeBanking.grant(FreeTime(bankedMillis = 2 * minute), 10, sessionActive = true, now = now)
        assertEquals(FreeTime(bankedMillis = 12 * minute), banked)
    }

    @Test
    fun sessionStartBanksWhatWasLeftAtTheSessionsStart() {
        val sessionStart = now
        val running = FreeTime(unlockedUntil = sessionStart + 10 * minute, totalMillis = 15 * minute)
        // Noticed 3 minutes late: still banks the full 10 minutes left at the session start
        val synced = FreeTimeBanking.sync(running, sessionStart, now + 3 * minute)
        assertEquals(FreeTime(bankedMillis = 10 * minute), synced)
    }

    @Test
    fun syncIsIdempotent() {
        val running = FreeTime(unlockedUntil = now + 10 * minute, totalMillis = 10 * minute)
        val once = FreeTimeBanking.sync(running, now, now)
        assertEquals(once, FreeTimeBanking.sync(once, now, now + minute))
    }

    @Test
    fun sessionEndRestoresTheBank() {
        val restored = FreeTimeBanking.sync(FreeTime(bankedMillis = 10 * minute), sessionStart = null, now = now)
        assertEquals(FreeTime(unlockedUntil = now + 10 * minute, totalMillis = 10 * minute), restored)
    }

    @Test
    fun expiredFreeTimeIsNotBanked() {
        val expired = FreeTime(unlockedUntil = now - minute, totalMillis = 10 * minute)
        assertEquals(expired, FreeTimeBanking.sync(expired, now, now))
    }

    @Test
    fun emergencyUnlockIsLeftAlone() {
        val state = FreeTime(unlockedUntil = now + minute, emergencyUntil = now + 15 * minute)
        assertEquals(now + 15 * minute, FreeTimeBanking.sync(state, now, now).emergencyUntil)
    }
}
