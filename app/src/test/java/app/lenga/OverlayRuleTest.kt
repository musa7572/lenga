package app.lenga

import app.lenga.focus.Decision
import app.lenga.focus.OverlayAction
import app.lenga.focus.OverlayRule
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayRuleTest {
    private val passThrough = setOf("android", "com.android.systemui", "com.google.android.inputmethod.latin", "app.lenga")

    @Test
    fun blockedAppShowsOverlay() {
        assertEquals(OverlayAction.SHOW, OverlayRule.onWindow("com.instagram.android", Decision.NoFreeTime, passThrough, showing = false))
        // Another blocked app while showing updates the overlay
        assertEquals(OverlayAction.SHOW, OverlayRule.onWindow("com.zhiliaoapp.musically", Decision.NoFreeTime, passThrough, showing = true))
    }

    @Test
    fun allowedAppHidesOverlay() {
        assertEquals(OverlayAction.HIDE, OverlayRule.onWindow("com.miui.home", Decision.Allow, passThrough, showing = true))
    }

    @Test
    fun allowedAppWhileHiddenDoesNothing() {
        assertEquals(OverlayAction.KEEP, OverlayRule.onWindow("com.whatsapp", Decision.Allow, passThrough, showing = false))
    }

    @Test
    fun passThroughWindowsKeepOverlay() {
        assertEquals(OverlayAction.KEEP, OverlayRule.onWindow("com.android.systemui", Decision.Allow, passThrough, showing = true))
        assertEquals(OverlayAction.KEEP, OverlayRule.onWindow("com.google.android.inputmethod.latin", Decision.Allow, passThrough, showing = true))
        assertEquals(OverlayAction.KEEP, OverlayRule.onWindow("app.lenga", Decision.Allow, passThrough, showing = true))
    }
}
