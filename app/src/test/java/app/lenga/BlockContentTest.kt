package app.lenga

import app.lenga.focus.BlockContent
import app.lenga.focus.BlockText
import app.lenga.focus.Decision
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BlockContentTest {
    private val zone = ZoneId.of("Africa/Nairobi")

    private fun at(hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 10, 13, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun blockedByModeShowsModeEndAndCountdown() {
        val text = BlockContent.of(Decision.BlockedByMode("Focus session", at(17, 34)), "Instagram", at(17, 34), at(17, 11), zone)
        assertEquals(BlockText("Instagram", "Focus session · until 17:34", "23m left"), text)
    }

    @Test
    fun settingsLockedCountsDownToEndOfModes() {
        val text = BlockContent.of(Decision.SettingsLocked, "Settings", at(18, 0), at(16, 30), zone)
        assertEquals(BlockText("Settings", "Settings are locked during focus sessions", "1h 30m left"), text)
    }

    @Test
    fun noFreeTimeHasNoCountdown() {
        val text = BlockContent.of(Decision.NoFreeTime, "TikTok", null, at(9, 0), zone)
        assertEquals(BlockText("TikTok", "Pass a quiz to earn free time", null), text)
    }

    @Test(expected = IllegalArgumentException::class)
    fun allowIsRejected() {
        BlockContent.of(Decision.Allow, "WhatsApp", null, at(9, 0), zone)
    }

    @Test
    fun labelFallsBackToPackageName() {
        assertEquals("Instagram", BlockContent.label("Instagram", "com.instagram.android"))
        assertEquals("com.instagram.android", BlockContent.label(null, "com.instagram.android"))
        assertEquals("com.instagram.android", BlockContent.label("  ", "com.instagram.android"))
    }
}
