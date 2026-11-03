package app.lenga.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import app.lenga.data.repository.EmergencyUnlocker
import app.lenga.data.repository.StudyRepository
import app.lenga.focus.ActiveMode
import app.lenga.focus.BlockContent
import app.lenga.focus.Decision
import app.lenga.focus.FocusFormat
import app.lenga.focus.FocusMode
import app.lenga.focus.FocusPolicy
import app.lenga.focus.ModeRepository
import app.lenga.focus.OverlayAction
import app.lenga.focus.OverlayRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Covers any app that [FocusPolicy] doesn't allow right now with the blocking overlay.
 */
class StudyAccessibilityService : AccessibilityService() {

    private var essentials: Set<String> = emptySet()
    /** Windows that open over the blocked app without replacing it; see [OverlayRule]. */
    private var passThrough: Set<String> = emptySet()
    /** Null until the saved modes have loaded; free time isn't paused or resumed before then. */
    private var modes: List<FocusMode>? = null
    private var lastFallbackAt = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var notification: FocusNotification? = null
    private var overlay: BlockOverlay? = null

    private val tick = object : Runnable {
        override fun run() {
            refreshFocus()
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** While the overlay shows: refreshes its countdown and hides it once the covered app is allowed. */
    private val overlayTick = Runnable {
        val pkg = overlay?.coveredPackage ?: return@Runnable
        val now = System.currentTimeMillis()
        val active = activeModes(now)
        val decision = decide(pkg, active, now)
        if (decision == Decision.Allow) overlay?.hide() else showOverlay(pkg, decision, active, now)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        essentials = EssentialApps.resolve(this)
        passThrough = EssentialApps.systemOverlays(this) + packageName
        notification = FocusNotification(this)
        overlay = BlockOverlay(this, scope, EmergencyUnlocker(StudyRepository(this)))
        BlockingState.setServiceRunning(true)
        scope.launch {
            ModeRepository(this@StudyAccessibilityService).modes.collect {
                modes = it
                refreshFocus()
            }
        }
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val now = System.currentTimeMillis()
        val active = activeModes(now)
        val decision = decide(packageName, active, now)
        when (OverlayRule.onWindow(packageName, decision, passThrough, overlay?.isShowing == true)) {
            OverlayAction.SHOW -> showOverlay(packageName, decision, active, now)
            OverlayAction.HIDE -> overlay?.hide()
            OverlayAction.KEEP -> {}
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(overlayTick)
        overlay?.hide()
        BlockingState.setServiceRunning(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(overlayTick)
        overlay?.destroy()
        scope.cancel()
        notification?.cancel()
        BlockingState.setServiceRunning(false)
        super.onDestroy()
    }

    private fun decide(pkg: String, active: List<ActiveMode>, now: Long): Decision = FocusPolicy.decide(
        pkg = pkg,
        active = active,
        essentials = essentials,
        freeTimeActive = BlockingState.isFreeTimeActive(now),
        emergencyActive = BlockingState.isEmergencyActive(now)
    )

    /** Shows or updates the overlay over [pkg] and keeps its countdown ticking. */
    private fun showOverlay(pkg: String, decision: Decision, active: List<ActiveMode>, now: Long) {
        val text = BlockContent.of(decision, appLabel(pkg), active.maxOfOrNull { it.end }, now, ZoneId.systemDefault())
        handler.removeCallbacks(overlayTick)
        if (overlay?.show(pkg, text) == true) {
            handler.postDelayed(overlayTick, OVERLAY_TICK_MS)
        } else {
            leave(decision, now)
        }
    }

    /** Fallback when the overlay can't be added: leave the app with HOME and explain with a toast. */
    private fun leave(decision: Decision, now: Long) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        // An app can fire several window events while opening; toast only once.
        if (now - lastFallbackAt > TOAST_DEBOUNCE_MS) {
            Toast.makeText(this, blockMessage(decision), Toast.LENGTH_SHORT).show()
        }
        lastFallbackAt = now
    }

    private fun appLabel(pkg: String): String = BlockContent.label(
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)) }.getOrNull(),
        pkg
    )

    /** Modes active at [now]. Also pauses or resumes quiz free time as modes start and end. */
    private fun activeModes(now: Long): List<ActiveMode> {
        val loaded = modes ?: return emptyList()
        val active = FocusPolicy.activeModes(now, loaded, ZoneId.systemDefault())
        BlockingState.syncWithSession(active.minOfOrNull { it.start }, now)
        return active
    }

    /** Updates the ongoing notification; runs every [TICK_MS] and whenever the modes change. */
    private fun refreshFocus() {
        val now = System.currentTimeMillis()
        val active = activeModes(now)
        if (active.isEmpty()) {
            notification?.cancel()
        } else {
            val end = active.maxOf { it.end }
            notification?.show("${active.joinToString { it.mode.name }} · ${FocusFormat.remaining(end - now)} left")
        }
    }

    private fun blockMessage(decision: Decision): String = when (decision) {
        is Decision.BlockedByMode ->
            "Blocked by ${decision.modeName} until ${FocusFormat.clock(decision.until, ZoneId.systemDefault())}"
        Decision.SettingsLocked -> "Settings are locked during focus sessions"
        else -> "Blocked during study time. Pass a quiz to earn free time."
    }

    private companion object {
        const val TOAST_DEBOUNCE_MS = 3_000L
        const val TICK_MS = 60_000L
        const val OVERLAY_TICK_MS = 1_000L
    }
}
