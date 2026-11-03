# Blocking Overlay — Design

**Date:** 2026-10-13
**Status:** Approved in brainstorming, awaiting spec review
**PRD:** section 35 (Blocking Screen), section 37 (Emergency Unlock)

## Context and goal

When a blocked app comes to the foreground, `StudyAccessibilityService` starts Lenga's
`MainActivity` over it. Xiaomi refuses that background activity start unless the user has
allowed "Open new windows while running in the background", which is off for new installs.
On a Redmi Note 11 Pro (MIUI 14, Android 13) the blocked app stayed usable and logcat showed
`MIUILOG- Permission Denied Activity`. Commit `fcbf2d7` added a stopgap: if the blocked app is
still in front 800 ms later, press HOME.

This design replaces the activity launch with a full-screen "Stay Focused" overlay that the
accessibility service draws over the blocked app. Accessibility services may add windows of
type `TYPE_ACCESSIBILITY_OVERLAY` over any app without `SYSTEM_ALERT_WINDOW` or Xiaomi's
background-window permission, so blocking no longer depends on OEM settings.

## Decisions

| Topic | Decision |
|---|---|
| Mechanism | `TYPE_ACCESSIBILITY_OVERLAY` window added by the accessibility service, hosting a `ComposeView`. |
| UI toolkit | Jetpack Compose, reusing the app theme and the existing PIN pad. |
| Emergency unlock | Done on the overlay with the on-screen PIN pad. Same rules as the in-app screen: 15 minutes, 2 per day, 3 wrong attempts return to the lock view. |
| Keyboard | Not needed. The overlay is not focusable; the PIN pad is on-screen buttons. |
| Old behaviour | The activity launch, the toast and the 800 ms HOME fallback are removed. HOME + toast remains only as the fallback when the overlay window can't be added. |
| "Open Lenga" button | Presses HOME, then tries to start Lenga. Where the start is refused, the user is left on the home screen, which still blocks. |
| PIN not set | The emergency button shows "No emergency PIN set. Create one in Lenga." with the Open Lenga button. Creating a PIN stays in the app. |

## Behaviour

### When the overlay shows

On each `TYPE_WINDOW_STATE_CHANGED` event, the service calls `FocusPolicy.decide` as today:

- **Block decision** for package P: show the overlay covering P, or update it if it is
  already showing (for example, a different blocked app).
- **Allow decision** for any other package outside the pass-through set: hide the overlay.
  This covers the launcher (including MIUI's recents screen), allowed apps and the phone app.
- **Pass-through packages** never change the overlay: `android` (system dialogs),
  `com.android.systemui` (status bar, notification shade, volume panel), the enabled keyboards,
  and Lenga itself. These windows open on top of the blocked app without replacing it, and
  closing them fires no new event for the blocked app, so hiding on them would leave it usable.
  Adding the overlay window may itself produce an event from Lenga, and Lenga's activity is only
  opened from the overlay's own button, which hides the overlay first.

### While the overlay shows

A once-a-second tick, which also drives the countdown, re-runs `decide` for the covered
package. When the answer becomes Allow, because a focus mode ended, free time started or an
emergency unlock was granted, the overlay hides and the app underneath is usable.

The overlay consumes all touches, so the app underneath can't be used. System navigation
(home, back, recents gestures) still works. Back reaches the blocked app, which stays covered.
Home and recents change the foreground window, which hides the overlay through the Allow rule.

### Screen content (lock view)

```
            [lock icon]
          Stay focused
     Instagram is locked

  Focus session · until 17:34
           23m left

          [ Close app ]
          [ Open Lenga ]
       [ Emergency unlock ]
```

Reason line by decision:

| Decision | Reason text | Countdown |
|---|---|---|
| `BlockedByMode(name, until)` | "*name* · until *HH:mm*" | "*remaining* left" |
| `SettingsLocked` | "Settings are locked during focus sessions" | "*remaining* left", to the latest end of the active modes |
| `NoFreeTime` | "Pass a quiz to earn free time" | none |

`Decision.SettingsLocked` carries no end time, so `BlockContent` takes the latest end of the
active modes as a separate `endsAt: Long?` argument (null when no mode is active).

The app name is the launcher label from `PackageManager`, falling back to the package name.

### Buttons

- **Close app:** `performGlobalAction(GLOBAL_ACTION_HOME)`, then hide the overlay.
- **Open Lenga:** HOME, hide the overlay, then start Lenga's launch intent.
- **Emergency unlock:** switch to the PIN view.

### PIN view

The existing PIN pad, titled "Emergency unlock", with "Enter your emergency PIN to unlock apps
for 15 minutes. *n* of 2 emergency unlocks left today." and a Cancel button that returns to the
lock view.

| Result | Overlay |
|---|---|
| Unlocked | Hides. The covered app is usable for 15 minutes. |
| Wrong PIN | "Incorrect PIN (*n* attempts remaining)". After 3 wrong attempts, returns to the lock view. |
| Limit reached (or 0 left on open) | "You've used both emergency unlocks today." with Back. |
| No PIN set | "No emergency PIN set. Create one in Lenga." with Open Lenga and Back. |

## Architecture

```
StudyAccessibilityService   decides on each window event (unchanged FocusPolicy);
                            tells BlockOverlay to show(pkg) or hide()

BlockOverlay (service)      owns the overlay window: WindowManager add/remove, a
                            lifecycle + saved-state owner for the ComposeView, the
                            1-second tick, and the screen state (lock view / PIN view)

BlockContent (pure Kotlin)  Decision + app label + now -> texts for the lock view;
                            unit tested

OverlayRule (pure Kotlin)   (event package, decision, own package, showing) ->
                            Show / Hide / Keep; unit tested

BlockScreen (Compose)       stateless UI for the lock view and PIN view; restyled from
                            the unused LockedAppOverlay component

EmergencyUnlocker           PIN check + daily limit + grant, moved out of
                            StudyViewModel.emergencyUnlock so the overlay and the
                            in-app Emergency Unlock screen share one implementation
```

### Files

| File | Change |
|---|---|
| `service/BlockOverlay.kt` | New. Window, lifecycle owner, tick, view state. |
| `service/OverlayLifecycleOwner.kt` | New. `LifecycleOwner` + `SavedStateRegistryOwner` for a `ComposeView` outside an activity. |
| `focus/BlockContent.kt` | New. Pure text mapping. |
| `focus/OverlayRule.kt` | New. Pure show/hide rule. |
| `ui/components/BlockScreen.kt` | New. Replaces `LockedAppOverlay.kt`, which is deleted. |
| `ui/components/PinPad.kt` | New. `PinPad` and `NumpadButton` moved out of `EmergencyUnlockScreen.kt` and made internal. |
| `data/repository/EmergencyUnlocker.kt` | New. Moved logic from `StudyViewModel.emergencyUnlock`. |
| `service/StudyAccessibilityService.kt` | Uses `BlockOverlay`; removes the launch, toast and 800 ms fallback. |
| `service/EssentialApps.kt` | Adds `systemOverlays()` (system UI, system dialogs, keyboards) for the pass-through set. |
| `viewmodel/StudyViewModel.kt` | `emergencyUnlock` delegates to `EmergencyUnlocker`. |
| `ui/screens/EmergencyUnlockScreen.kt` | Imports the moved `PinPad`. |

### Window parameters

- Type `TYPE_ACCESSIBILITY_OVERLAY`, `MATCH_PARENT` both ways.
- Flags `FLAG_NOT_FOCUSABLE` (no keyboard needed, back goes to the app underneath),
  `FLAG_LAYOUT_IN_SCREEN`, `FLAG_LAYOUT_NO_LIMITS`.
- Opaque background, so the blocked app's content isn't visible behind it.
- Added through the service's own `WindowManager` (`getSystemService(WINDOW_SERVICE)` on the
  service context), which is required for the accessibility overlay type.

### Error handling

- `addView` failing (for example `BadTokenException` while the service is reconnecting):
  catch, log, and fall back to HOME + toast for that event.
- Service destroyed or unbound while the overlay shows: remove the view and stop the tick in
  `onDestroy` / `onUnbind`.
- Lifecycle: moved to RESUMED when shown and DESTROYED when the service stops. Hiding removes
  the view but keeps the owner, so showing again is cheap.

## Testing

### Unit tests (JVM)

- `BlockContentTest`: each decision maps to the right reason and countdown text; the
  countdown is absent for `NoFreeTime`; the app label falls back to the package name.
- `OverlayRuleTest`: Block shows; Allow for another package hides; pass-through packages
  (system UI, keyboards, Lenga itself) keep the current state; Allow while not showing keeps it
  hidden.
- Existing `FocusPolicyTest`, `EmergencyUnlockPolicyTest` and the others keep passing.

### On the Redmi, with Xiaomi's background-window permission at its default (`ignore`)

1. **Spike first:** add a plain overlay from the service and confirm it appears over Instagram
   and blocks touches. If MIUI blocks accessibility overlays, stop and revisit the design.
2. Opening Instagram with no free time shows the overlay with "Pass a quiz to earn free time".
3. During a timed focus session, the overlay shows the mode name, end time and a live countdown.
4. Close app goes to the home screen and the overlay is gone.
5. Opening an allowed app (WhatsApp in a mode that allows it, or the phone app) hides the overlay.
6. Emergency unlock with the right PIN hides the overlay and Instagram stays usable; the
   unlocks-left count drops by one.
7. Three wrong PINs return to the lock view.
8. Pass a quiz to start free time, then start a 2-minute focus session (which pauses the free
   time) and open Instagram. When the session ends, the paused free time resumes and the
   overlay disappears on its own.
9. Settings during a focus session shows the overlay with "Settings are locked during focus
   sessions".
10. Pulling down the notification shade over the overlay and closing it again leaves the
    overlay in place.

## Out of scope

- Study-requirement progress on the overlay ("42 / 60 minutes" in PRD section 35). It depends
  on the study requirements from the Release 1 scope, which don't exist yet.
- "View Progress" button from PRD section 35. Progress is reachable through Open Lenga.
- Logging blocked-app attempts for the Progress screen (PRD section 42). A separate change.
- Creating an emergency PIN on the overlay.
