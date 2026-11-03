package app.lenga

import app.lenga.focus.FocusMode
import app.lenga.focus.FocusModeCodec
import app.lenga.focus.ModeSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeCodecTest {

    @Test
    fun roundTripsEveryScheduleType() {
        val modes = listOf(
            FocusMode("a", "Session", ModeSchedule.Timed(1_000L, 2_000L), setOf("com.whatsapp")),
            FocusMode("b", "Detox", ModeSchedule.Detox(1_000L, 5_000L)),
            FocusMode("c", "School week", ModeSchedule.Weekly(setOf(1, 2, 3, 4, 5), 480, 960), setOf("com.google.android.apps.classroom")),
            FocusMode("d", "Exam", ModeSchedule.OneOff(10_000L, 20_000L))
        )
        val loaded = FocusModeCodec.decode(FocusModeCodec.encode(modes))
        assertEquals(modes, loaded.modes)
        assertFalse(loaded.corrupt)
    }

    @Test
    fun missingDataIsAnEmptyListNotCorrupt() {
        assertEquals(emptyList<FocusMode>(), FocusModeCodec.decode(null).modes)
        assertFalse(FocusModeCodec.decode("").corrupt)
    }

    @Test
    fun unreadableDataIsFlaggedCorrupt() {
        val loaded = FocusModeCodec.decode("{not json")
        assertTrue(loaded.corrupt)
        assertEquals(emptyList<FocusMode>(), loaded.modes)
    }
}
