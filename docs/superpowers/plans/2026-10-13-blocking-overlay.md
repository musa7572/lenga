# Blocking Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace "bring Lenga to the front" with a full-screen "Stay focused" overlay that the accessibility service draws over blocked apps, including emergency unlock with the PIN pad.

**Architecture:** The accessibility service keeps deciding with `FocusPolicy` on every window event. A pure `OverlayRule` turns each event into show / hide / keep, and a pure `BlockContent` produces the overlay's text. `BlockOverlay` owns a `TYPE_ACCESSIBILITY_OVERLAY` window hosting a `ComposeView` (with `OverlayLifecycleOwner` supplying the lifecycle Compose needs). Emergency-unlock rules move into `EmergencyUnlocker`, shared by the overlay and the in-app screen.

**Tech Stack:** Kotlin 2.0, Jetpack Compose (Material3 via BOM 2024.04.01), lifecycle 2.8.7, savedstate (via activity-compose 1.9.3), DataStore Preferences, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-13-blocking-overlay-design.md`

**Conventions used throughout:**
- Build and test from the repo root in Git Bash. Kotlin incremental compilation corrupts its cache on this machine, so always pass `-Pkotlin.incremental=false`:
  - Unit tests: `./gradlew -q -Pkotlin.incremental=false :app:testDebugUnitTest --tests "app.lenga.<TestClass>"`
  - Build: `./gradlew -q -Pkotlin.incremental=false :app:assembleDebug`
  - "Daemon compilation failed" followed by exit code 0 is harmless.
- Commit on the current branch `feature/focus-modes`. End every commit message with the line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Unit tests live flat in `app/src/test/java/app/lenga/`.
- On-device steps use `export MSYS_NO_PATHCONV=1; ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`. The test phone is a Redmi Note 11 Pro (MIUI 14, Android 13). Shell input injection is not permitted on it, so taps must be done by the user. Keep Xiaomi's background-window permission at its default: `"$ADB" shell appops get app.lenga | grep 10021` must print `ignore`.

---

## File structure

| File | Responsibility |
|---|---|
| `focus/BlockContent.kt` (new) | `BlockText` and the decision → overlay text mapping; app label fallback |
| `focus/OverlayRule.kt` (new) | `OverlayAction` and the show / hide / keep rule for window events |
| `service/EssentialApps.kt` (modify) | Adds `systemOverlays()`; `resolve()` reuses it |
| `service/OverlayLifecycleOwner.kt` (new) | Lifecycle + saved state for a `ComposeView` outside an activity |
| `service/BlockOverlay.kt` (new) | Adds/removes the overlay window, holds its state, handles its buttons |
| `service/StudyAccessibilityService.kt` (modify) | Drives the overlay from window events and a 1-second tick |
| `ui/components/BlockScreen.kt` (new) | Compose UI: lock page, PIN page, limit page, no-PIN page |
| `ui/components/PinPad.kt` (new) | `PinPad`, `NumpadButton`, `PIN_LENGTH`, moved from `EmergencyUnlockScreen.kt` |
| `data/repository/EmergencyUnlocker.kt` (new) | `EmergencyUnlockResult` and the PIN / limit / grant rules |
| `viewmodel/StudyViewModel.kt` (modify) | Delegates emergency unlock to `EmergencyUnlocker` |
| `ui/screens/EmergencyUnlockScreen.kt` (modify) | Uses the moved `PinPad` and `EmergencyUnlocker` constants |
| `ui/components/LockedAppOverlay.kt` (delete) | Unused; replaced by `BlockScreen` |

---

### Task 1: Overlay text

**Files:**
- Create: `app/src/main/java/app/lenga/focus/BlockContent.kt`
- Test: `app/src/test/java/app/lenga/BlockContentTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew -q -Pkotlin.incremental=false :app:testDebugUnitTest --tests "app.lenga.BlockContentTest"`
Expected: compilation fails with "Unresolved reference 'BlockContent'".

- [ ] **Step 3: Implement**

```kotlin
package app.lenga.focus

import java.time.ZoneId

/** What the blocking overlay says. [remaining] is null when the block has no end time. */
data class BlockText(val appLabel: String, val reason: String, val remaining: String?)

/** Text for the blocking overlay. Pure, so it can be unit tested without Android. */
object BlockContent {

    /**
     * Text for a block [decision] on [appLabel]. [endsAt] is the latest end of the active modes,
     * used for [Decision.SettingsLocked], which carries no end time; null when no mode is active.
     */
    fun of(decision: Decision, appLabel: String, endsAt: Long?, now: Long, zone: ZoneId): BlockText = when (decision) {
        is Decision.BlockedByMode -> BlockText(
            appLabel,
            "${decision.modeName} · until ${FocusFormat.clock(decision.until, zone)}",
            left(decision.until, now)
        )
        Decision.SettingsLocked ->
            BlockText(appLabel, "Settings are locked during focus sessions", endsAt?.let { left(it, now) })
        Decision.NoFreeTime -> BlockText(appLabel, "Pass a quiz to earn free time", null)
        Decision.Allow -> throw IllegalArgumentException("Allowed apps aren't blocked")
    }

    /** The app's launcher label, or its package name when the label is missing or blank. */
    fun label(loaded: CharSequence?, packageName: String): String =
        loaded?.toString()?.takeIf { it.isNotBlank() } ?: packageName

    private fun left(until: Long, now: Long) = "${FocusFormat.remaining(until - now)} left"
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew -q -Pkotlin.incremental=false :app:testDebugUnitTest --tests "app.lenga.BlockContentTest"`
Expected: exit 0; `app/build/test-results/testDebugUnitTest/TEST-app.lenga.BlockContentTest.xml` shows `tests="5" failures="0" errors="0"`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lenga/focus/BlockContent.kt app/src/test/java/app/lenga/BlockContentTest.kt
git commit -m "Add text for the blocking overlay" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Show / hide rule and pass-through packages

**Files:**
- Create: `app/src/main/java/app/lenga/focus/OverlayRule.kt`
- Modify: `app/src/main/java/app/lenga/service/EssentialApps.kt` (whole file)
- Test: `app/src/test/java/app/lenga/OverlayRuleTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew -q -Pkotlin.incremental=false :app:testDebugUnitTest --tests "app.lenga.OverlayRuleTest"`
Expected: compilation fails with "Unresolved reference 'OverlayRule'".

- [ ] **Step 3: Implement `OverlayRule.kt`**

```kotlin
package app.lenga.focus

/** What to do with the blocking overlay when a window comes to the foreground. */
enum class OverlayAction { SHOW, HIDE, KEEP }

object OverlayRule {

    /**
     * [pkg] is the app whose window just appeared and [decision] is FocusPolicy's answer for it.
     * [passThrough] are windows that open over the current app without replacing it (status bar,
     * notification shade, system dialogs, keyboards) plus Lenga itself. They never change the
     * overlay: the blocked app is still underneath, and closing them fires no event for it.
     */
    fun onWindow(pkg: String, decision: Decision, passThrough: Set<String>, showing: Boolean): OverlayAction = when {
        pkg in passThrough -> OverlayAction.KEEP
        decision != Decision.Allow -> OverlayAction.SHOW
        showing -> OverlayAction.HIDE
        else -> OverlayAction.KEEP
    }
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew -q -Pkotlin.incremental=false :app:testDebugUnitTest --tests "app.lenga.OverlayRuleTest"`
Expected: exit 0; the result XML shows `tests="4" failures="0" errors="0"`.

- [ ] **Step 5: Replace `EssentialApps.kt`** so the system-overlay packages are defined once and `resolve()` reuses them:

```kotlin
package app.lenga.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/**
 * Apps that must always stay reachable: Lenga itself, the system UI, the launcher,
 * keyboards, permission dialogs, and phone/SMS for emergencies. Settings isn't here: it's
 * allowed outside focus modes and locked during them (see FocusPolicy.SETTINGS_PACKAGES).
 */
object EssentialApps {

    fun resolve(context: Context): Set<String> {
        val allowed = mutableSetOf(
            context.packageName,
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.contacts",
            "com.android.messaging",
            "com.google.android.apps.messaging"
        )
        allowed.addAll(systemOverlays(context))
        val packageManager = context.packageManager

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapTo(allowed) { it.activityInfo.packageName }

        (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
            ?.defaultDialerPackage
            ?.let { allowed.add(it) }

        Telephony.Sms.getDefaultSmsPackage(context)?.let { allowed.add(it) }

        return allowed
    }

    /**
     * Windows that open on top of the current app without replacing it: system dialogs, the
     * status bar and notification shade, and keyboards.
     */
    fun systemOverlays(context: Context): Set<String> {
        val packages = mutableSetOf("android", "com.android.systemui")
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.enabledInputMethodList
            ?.mapTo(packages) { it.packageName }
        return packages
    }
}
```

- [ ] **Step 6: Build and run all unit tests**

Run: `./gradlew -q -Pkotlin.incremental=false :app:testDebugUnitTest :app:assembleDebug`
Expected: exit 0.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/lenga/focus/OverlayRule.kt app/src/test/java/app/lenga/OverlayRuleTest.kt app/src/main/java/app/lenga/service/EssentialApps.kt
git commit -m "Decide when the blocking overlay shows and hides" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Overlay window and service wiring (on-device spike)

This task proves on the Redmi that an accessibility overlay can cover a blocked app. The screen is minimal (lock page with Close app only); Task 5 completes it.

**Files:**
- Create: `app/src/main/java/app/lenga/service/OverlayLifecycleOwner.kt`
- Create: `app/src/main/java/app/lenga/service/BlockOverlay.kt`
- Create: `app/src/main/java/app/lenga/ui/components/BlockScreen.kt`
- Modify: `app/src/main/java/app/lenga/service/StudyAccessibilityService.kt` (whole file)

- [ ] **Step 1: Create `OverlayLifecycleOwner.kt`**

```kotlin
package app.lenga.service

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Lifecycle for a ComposeView in a window that isn't part of an activity. Compose needs a
 * LifecycleOwner and a SavedStateRegistryOwner on the view tree to run.
 */
class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    /** Moves to RESUMED. Call once, before the first view is attached. */
    fun start() {
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }

    fun attach(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
}
```

- [ ] **Step 2: Create the minimal `BlockScreen.kt`**

```kotlin
package app.lenga.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lenga.focus.BlockText
import app.lenga.ui.theme.PrimaryBlue

/** The "Stay focused" overlay over a blocked app. */
@Composable
fun BlockScreen(text: BlockText, onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(PrimaryBlue).safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Stay focused", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("${text.appLabel} is locked", color = Color.White)
            Text(text.reason, color = Color.White)
            text.remaining?.let { Text(it, color = Color.White) }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close app") }
        }
    }
}
```

- [ ] **Step 3: Create `BlockOverlay.kt`**

```kotlin
package app.lenga.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import app.lenga.focus.BlockText
import app.lenga.ui.components.BlockScreen
import app.lenga.ui.theme.LengaTheme

/**
 * Full-screen "Stay focused" window drawn by the accessibility service over a blocked app.
 * Accessibility overlays need no extra permission and aren't affected by OEM limits on
 * opening activities from the background.
 */
class BlockOverlay(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayLifecycleOwner().apply { start() }
    private var view: ComposeView? = null
    private var text by mutableStateOf<BlockText?>(null)

    /** Package of the app under the overlay; null when hidden. */
    var coveredPackage: String? = null
        private set

    val isShowing: Boolean get() = view != null

    /** Shows [newText] over [pkg], or updates it if already showing. Returns false if the window couldn't be added. */
    fun show(pkg: String, newText: BlockText): Boolean {
        text = newText
        coveredPackage = pkg
        if (view != null) return true
        val composeView = ComposeView(service).apply {
            owner.attach(this)
            setContent {
                LengaTheme {
                    text?.let { BlockScreen(it, onClose = ::close) }
                }
            }
        }
        return try {
            windowManager.addView(composeView, layoutParams())
            view = composeView
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't add the blocking overlay", e)
            coveredPackage = null
            false
        }
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        coveredPackage = null
    }

    fun destroy() {
        hide()
        owner.destroy()
    }

    private fun close() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        hide()
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE
    )

    private companion object {
        const val TAG = "BlockOverlay"
    }
}
```

- [ ] **Step 4: Replace `StudyAccessibilityService.kt`**

```kotlin
package app.lenga.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
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
        overlay = BlockOverlay(this)
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
```

- [ ] **Step 5: Build and run all unit tests**

Run: `./gradlew -q -Pkotlin.incremental=false :app:testDebugUnitTest :app:assembleDebug`
Expected: exit 0.

- [ ] **Step 6: Spike on the phone.** Install, confirm the service is bound, open Instagram and screenshot:

```bash
export MSYS_NO_PATHCONV=1; ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell dumpsys accessibility | grep -E "Bound services|Crashed"
"$ADB" shell appops get app.lenga | grep 10021
"$ADB" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
"$ADB" shell 'monkey -p com.instagram.android -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 2; screencap -p /sdcard/overlay.png'
"$ADB" pull /sdcard/overlay.png "$TEMP/overlay.png"
"$ADB" shell dumpsys window windows | grep -iE "Window #.*(lenga|AccessibilityOverlay)"
```

Expected: Lenga bound, `MIUIOP(10021): ignore`, the screenshot (read it) shows the blue "Stay focused" screen naming Instagram, and the window dump lists an accessibility overlay window owned by Lenga. Then ask the user to try scrolling where the overlay is (nothing behind it should move) and to tap **Close app** (home screen appears, overlay gone).

If the overlay does not appear or touches reach Instagram, STOP: report the evidence and revisit the design with the user before Task 4.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/lenga/service app/src/main/java/app/lenga/ui/components/BlockScreen.kt
git commit -m "Cover blocked apps with an accessibility overlay" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Share the PIN pad and emergency-unlock rules

Pure refactor: the in-app Emergency Unlock screen behaves the same afterwards.

**Files:**
- Create: `app/src/main/java/app/lenga/ui/components/PinPad.kt`
- Create: `app/src/main/java/app/lenga/data/repository/EmergencyUnlocker.kt`
- Modify: `app/src/main/java/app/lenga/ui/screens/EmergencyUnlockScreen.kt`
- Modify: `app/src/main/java/app/lenga/viewmodel/StudyViewModel.kt`

- [ ] **Step 1: Create `PinPad.kt`** (moved from `EmergencyUnlockScreen.kt`, now public):

```kotlin
package app.lenga.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lenga.ui.theme.AccentRed
import app.lenga.ui.theme.TextSecondary

const val PIN_LENGTH = 4

/** On-screen number pad for entering a [PIN_LENGTH]-digit PIN. Needs no keyboard. */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    pin: String,
    errorText: String?,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(64.dp), tint = AccentRed)

        Spacer(modifier = Modifier.height(24.dp))

        Text(title, style = MaterialTheme.typography.headlineMedium, color = AccentRed)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(PIN_LENGTH) { index ->
                Box(
                    modifier = Modifier.size(20.dp).clip(CircleShape).background(if (index < pin.length) AccentRed else Color.LightGray)
                )
            }
        }

        if (errorText != null) {
            Text(errorText, color = AccentRed, modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(3) { col ->
                        val number = row * 3 + col + 1
                        NumpadButton(number.toString()) {
                            if (pin.length < PIN_LENGTH) onPinChange(pin + number)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(modifier = Modifier.size(72.dp))
                NumpadButton("0") {
                    if (pin.length < PIN_LENGTH) onPinChange(pin + "0")
                }
                NumpadButton("⌫") {
                    onPinChange(pin.dropLast(1))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }

            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text(confirmLabel)
            }
        }
    }
}

@Composable
fun NumpadButton(number: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.size(72.dp), shape = CircleShape) {
        Text(number, style = MaterialTheme.typography.headlineMedium)
    }
}
```

- [ ] **Step 2: Create `EmergencyUnlocker.kt`**

```kotlin
package app.lenga.data.repository

import app.lenga.service.BlockingState
import kotlinx.coroutines.flow.Flow

enum class EmergencyUnlockResult { UNLOCKED, WRONG_PIN, LIMIT_REACHED }

/** Emergency-unlock rules shared by the in-app screen and the blocking overlay. */
class EmergencyUnlocker(private val repository: StudyRepository) {

    val isPinSet: Flow<Boolean> = repository.isPinSet

    /** Emergency unlocks left today. Recomputed on each subscription, so midnight resets apply. */
    val unlocksLeftToday: Flow<Int> = repository.emergencyUnlocksLeftToday

    /**
     * Verifies [pin] and, if today's limit allows, records the unlock and unlocks apps
     * for [MINUTES]. A wrong PIN doesn't use up an unlock.
     */
    suspend fun unlock(pin: String): EmergencyUnlockResult = when {
        !repository.verifyEmergencyPin(pin) -> EmergencyUnlockResult.WRONG_PIN
        !repository.tryRecordEmergencyUnlock() -> EmergencyUnlockResult.LIMIT_REACHED
        else -> {
            BlockingState.grantEmergency(MINUTES)
            EmergencyUnlockResult.UNLOCKED
        }
    }

    companion object {
        const val MINUTES = 15
        /** Wrong PINs allowed before the PIN screen closes. */
        const val MAX_ATTEMPTS = 3
    }
}
```

- [ ] **Step 3: Update `EmergencyUnlockScreen.kt`**
  - Delete everything from the `@Composable` line directly above `private fun PinPad(` to the end of the file (this removes `PinPad` and `NumpadButton`).
  - Delete the lines `private const val PIN_LENGTH = 4` and `private const val MAX_ATTEMPTS = 3`.
  - Replace `import app.lenga.viewmodel.EmergencyUnlockResult` with:

```kotlin
import app.lenga.data.repository.EmergencyUnlockResult
import app.lenga.data.repository.EmergencyUnlocker
import app.lenga.ui.components.PIN_LENGTH
import app.lenga.ui.components.PinPad
```

  - Replace both uses of `MAX_ATTEMPTS` with `EmergencyUnlocker.MAX_ATTEMPTS`.

- [ ] **Step 4: Update `StudyViewModel.kt`**
  - Replace the whole `emergencyUnlock` function and its KDoc (from `    /**\n     * Verifies [pin] and, if today's limit allows` to the function's closing `    }`) with:

```kotlin
    private val emergencyUnlocker = EmergencyUnlocker(repository)

    /** See [EmergencyUnlocker.unlock]. */
    fun emergencyUnlock(pin: String, onResult: (EmergencyUnlockResult) -> Unit) {
        viewModelScope.launch { onResult(emergencyUnlocker.unlock(pin)) }
    }
```

  - Replace `        const val EMERGENCY_UNLOCK_MINUTES = 15` with `        const val EMERGENCY_UNLOCK_MINUTES = EmergencyUnlocker.MINUTES`.
  - Delete the line `enum class EmergencyUnlockResult { UNLOCKED, WRONG_PIN, LIMIT_REACHED }` (it now lives in `EmergencyUnlocker.kt`; `app.lenga.data.repository.*` is already imported).

- [ ] **Step 5: Build and run all unit tests**

Run: `./gradlew -q -Pkotlin.incremental=false :app:testDebugUnitTest :app:assembleDebug`
Expected: exit 0. Then `git grep -n "EmergencyUnlockResult\|PinPad\|PIN_LENGTH" -- app/src/main` shows no references to `app.lenga.viewmodel.EmergencyUnlockResult` or a private `PinPad`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/lenga
git commit -m "Share the PIN pad and emergency unlock rules" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Complete overlay screen with Open Lenga and emergency unlock

**Files:**
- Modify: `app/src/main/java/app/lenga/ui/components/BlockScreen.kt` (whole file)
- Modify: `app/src/main/java/app/lenga/service/BlockOverlay.kt` (whole file)
- Modify: `app/src/main/java/app/lenga/service/StudyAccessibilityService.kt` (one line)
- Delete: `app/src/main/java/app/lenga/ui/components/LockedAppOverlay.kt`

- [ ] **Step 1: Replace `BlockScreen.kt`**

```kotlin
package app.lenga.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lenga.data.model.EmergencyUnlockPolicy
import app.lenga.data.repository.EmergencyUnlockResult
import app.lenga.data.repository.EmergencyUnlocker
import app.lenga.focus.BlockText
import app.lenga.ui.theme.AccentRed
import app.lenga.ui.theme.PrimaryBlue
import app.lenga.ui.theme.PrimaryDark
import app.lenga.ui.theme.TextSecondary

private enum class BlockPage { LOCK, PIN }

/**
 * The "Stay focused" overlay over a blocked app (PRD section 35). [pinSet] and [unlocksLeft]
 * are null until loaded. [onUnlock] checks a PIN and reports the result.
 */
@Composable
fun BlockScreen(
    text: BlockText,
    pinSet: Boolean?,
    unlocksLeft: Int?,
    onClose: () -> Unit,
    onOpenLenga: () -> Unit,
    onUnlock: (pin: String, onResult: (EmergencyUnlockResult) -> Unit) -> Unit
) {
    // A different blocked app starts again on the lock page
    var page by remember(text.appLabel) { mutableStateOf(BlockPage.LOCK) }
    val backToLock = { page = BlockPage.LOCK }

    when (page) {
        BlockPage.LOCK -> LockPage(text, onClose, onOpenLenga, onEmergency = { page = BlockPage.PIN })
        BlockPage.PIN -> Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
                when {
                    pinSet == null || unlocksLeft == null -> CircularProgressIndicator()
                    !pinSet -> MessagePage(
                        title = "No emergency PIN set",
                        body = "Create one in Lenga to use emergency unlock.",
                        primaryLabel = "Open Lenga",
                        onPrimary = onOpenLenga,
                        onBack = backToLock
                    )
                    unlocksLeft == 0 -> MessagePage(
                        title = "Daily limit reached",
                        body = "You've used all ${EmergencyUnlockPolicy.DAILY_LIMIT} emergency unlocks today. " +
                            "Your phone and messaging apps still work.",
                        primaryLabel = null,
                        onPrimary = {},
                        onBack = backToLock
                    )
                    else -> UnlockPage(unlocksLeft, onUnlock, onBack = backToLock)
                }
            }
        }
    }
}

@Composable
private fun LockPage(text: BlockText, onClose: () -> Unit, onOpenLenga: () -> Unit, onEmergency: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PrimaryDark, PrimaryBlue)))
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Text("Stay focused", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "${text.appLabel} is locked",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            Text(
                text.reason,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            text.remaining?.let {
                Text(it, style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = PrimaryBlue)
            ) { Text("Close app") }
            OutlinedButton(
                onClick = onOpenLenga,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text("Open Lenga") }
            TextButton(
                onClick = onEmergency,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
            ) { Text("Emergency unlock") }
        }
    }
}

@Composable
private fun UnlockPage(
    unlocksLeft: Int,
    onUnlock: (pin: String, onResult: (EmergencyUnlockResult) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }

    PinPad(
        title = "Emergency unlock",
        subtitle = "Enter your emergency PIN to unlock apps for ${EmergencyUnlocker.MINUTES} minutes. " +
            "$unlocksLeft of ${EmergencyUnlockPolicy.DAILY_LIMIT} emergency unlocks left today.",
        pin = pin,
        errorText = if (attempts > 0) "Incorrect PIN (${EmergencyUnlocker.MAX_ATTEMPTS - attempts} attempts remaining)" else null,
        confirmLabel = "Unlock",
        confirmEnabled = pin.length == PIN_LENGTH && !checking,
        onPinChange = { pin = it },
        onConfirm = {
            checking = true
            onUnlock(pin) { result ->
                checking = false
                pin = ""
                // UNLOCKED hides the overlay; LIMIT_REACHED shows the limit page once the count updates
                if (result == EmergencyUnlockResult.WRONG_PIN) {
                    attempts++
                    if (attempts >= EmergencyUnlocker.MAX_ATTEMPTS) onBack()
                }
            }
        },
        onCancel = onBack
    )
}

@Composable
private fun MessagePage(title: String, body: String, primaryLabel: String?, onPrimary: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(64.dp), tint = AccentRed)
        Spacer(modifier = Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = AccentRed, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        primaryLabel?.let { Button(onClick = onPrimary) { Text(it) } }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
```

- [ ] **Step 2: Replace `BlockOverlay.kt`**

```kotlin
package app.lenga.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import app.lenga.data.repository.EmergencyUnlockResult
import app.lenga.data.repository.EmergencyUnlocker
import app.lenga.focus.BlockText
import app.lenga.ui.components.BlockScreen
import app.lenga.ui.theme.LengaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Full-screen "Stay focused" window drawn by the accessibility service over a blocked app.
 * Accessibility overlays need no extra permission and aren't affected by OEM limits on
 * opening activities from the background.
 */
class BlockOverlay(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val unlocker: EmergencyUnlocker
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayLifecycleOwner().apply { start() }
    private var view: ComposeView? = null
    private var text by mutableStateOf<BlockText?>(null)
    private var pinSet by mutableStateOf<Boolean?>(null)
    private var unlocksLeft by mutableStateOf<Int?>(null)

    /** Package of the app under the overlay; null when hidden. */
    var coveredPackage: String? = null
        private set

    val isShowing: Boolean get() = view != null

    /** Shows [newText] over [pkg], or updates it if already showing. Returns false if the window couldn't be added. */
    fun show(pkg: String, newText: BlockText): Boolean {
        if (pkg != coveredPackage) refreshEmergencyInfo()
        text = newText
        coveredPackage = pkg
        if (view != null) return true
        val composeView = ComposeView(service).apply {
            owner.attach(this)
            setContent {
                LengaTheme {
                    text?.let {
                        BlockScreen(
                            text = it,
                            pinSet = pinSet,
                            unlocksLeft = unlocksLeft,
                            onClose = ::close,
                            onOpenLenga = ::openLenga,
                            onUnlock = ::unlock
                        )
                    }
                }
            }
        }
        return try {
            windowManager.addView(composeView, layoutParams())
            view = composeView
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't add the blocking overlay", e)
            coveredPackage = null
            false
        }
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        coveredPackage = null
    }

    fun destroy() {
        hide()
        owner.destroy()
    }

    private fun close() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        hide()
    }

    /**
     * Goes HOME, then opens Lenga once the launcher is up (HOME is asynchronous and would
     * otherwise land on top of Lenga). Where the phone refuses the launch, the user stays home.
     */
    private fun openLenga() {
        close()
        scope.launch {
            delay(OPEN_LENGA_DELAY_MS)
            service.packageManager.getLaunchIntentForPackage(service.packageName)?.let {
                service.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun unlock(pin: String, onResult: (EmergencyUnlockResult) -> Unit) {
        scope.launch {
            val result = unlocker.unlock(pin)
            unlocksLeft = unlocker.unlocksLeftToday.first()
            onResult(result)
            if (result == EmergencyUnlockResult.UNLOCKED) hide()
        }
    }

    /** Re-reads PIN state and today's unlocks, so a new day or a new PIN shows up. */
    private fun refreshEmergencyInfo() {
        scope.launch {
            pinSet = unlocker.isPinSet.first()
            unlocksLeft = unlocker.unlocksLeftToday.first()
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE
    )

    private companion object {
        const val TAG = "BlockOverlay"
        const val OPEN_LENGA_DELAY_MS = 300L
    }
}
```

- [ ] **Step 3: Update the service.** In `StudyAccessibilityService.kt`, replace

```kotlin
        overlay = BlockOverlay(this)
```

with

```kotlin
        overlay = BlockOverlay(this, scope, EmergencyUnlocker(StudyRepository(this)))
```

and add these imports after `import app.lenga.focus.ActiveMode`:

```kotlin
import app.lenga.data.repository.EmergencyUnlocker
import app.lenga.data.repository.StudyRepository
```

- [ ] **Step 4: Delete the unused component**

```bash
git rm -q app/src/main/java/app/lenga/ui/components/LockedAppOverlay.kt
```

- [ ] **Step 5: Build and run all unit tests**

Run: `./gradlew -q -Pkotlin.incremental=false :app:testDebugUnitTest :app:assembleDebug`
Expected: exit 0.

- [ ] **Step 6: Commit**

```bash
git add -A app/src/main/java/app/lenga
git commit -m "Add Open Lenga and emergency unlock to the blocking overlay" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: On-device verification

No code changes. Install and confirm the service is bound and the Xiaomi permission is at its default:

```bash
export MSYS_NO_PATHCONV=1; ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell dumpsys accessibility | grep -E "Bound services|Crashed"
"$ADB" shell appops get app.lenga | grep 10021
```

Use screenshots (`"$ADB" shell screencap -p /sdcard/s.png && "$ADB" pull /sdcard/s.png "$TEMP/s.png"`, then read the image) and `"$ADB" shell dumpsys activity activities | grep -m1 topResumedActivity` to check each result. Ask the user to do the taps.

- [ ] **Step 1:** No focus mode, no free time: open Instagram. Overlay shows "Instagram is locked" and "Pass a quiz to earn free time", no countdown.
- [ ] **Step 2:** Tap **Close app**. Home screen shows, overlay is gone.
- [ ] **Step 3:** Start a 3-minute timed session that allows WhatsApp. Open Instagram: overlay shows "Focus session · until HH:mm" and a countdown that changes each minute. Open WhatsApp: it opens normally.
- [ ] **Step 4:** With the overlay over Instagram, pull down the notification shade and close it. The overlay stays.
- [ ] **Step 5:** Open Settings during the session: overlay shows "Settings are locked during focus sessions" with a countdown.
- [ ] **Step 6:** On the overlay, tap **Emergency unlock**, enter a wrong PIN three times: back on the lock page. Then enter the right PIN: overlay disappears, Instagram is usable, and Lenga's Progress screen counts one more emergency unlock.
- [ ] **Step 7:** Tap **Open Lenga** on the overlay: home screen, then Lenga if the phone allows it (with `10021: ignore` it stays on the home screen; both are acceptable).
- [ ] **Step 8:** Pass a quiz to start free time, start a 2-minute timed session, open Instagram (overlay shows). When the session ends, the overlay disappears on its own and Instagram is usable.
- [ ] **Step 9:** Report which checks passed and failed, with screenshots or logcat lines for failures. Fix failures with superpowers:systematic-debugging before claiming the feature works.
