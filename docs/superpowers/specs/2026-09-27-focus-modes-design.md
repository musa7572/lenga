# Focus Modes — Design

**Date:** 2026-09-27
**Status:** Approved in brainstorming, awaiting spec review
**Scope:** Sub-project 1 of 4 in the "restriction modes" initiative.

## Context and goal

Today Lenga blocks every non-essential app whenever its accessibility service runs, except
during free time earned by passing quizzes or granted by an emergency unlock (PIN, 2/day, 15 min).

Users want to commit to stricter, time-bound restrictions: a study block of a chosen length, a
detox day, a recurring school-week schedule, or a one-off future block such as an exam morning.
During such a period the restrictions must be hard to escape.

This spec covers those **focus modes** only. Later sub-projects, each with its own spec, build on
the "is a mode active?" signal defined here:

2. Pomodoro breaks with app tiers (light apps on short breaks, addictive apps on long breaks)
3. Daily per-app time limits
4. Holding notifications during focus time and releasing them at breaks or after the session

## Decisions

| Topic | Decision |
|---|---|
| Relation to current model | Keep both. Always-on blocking with quiz free time stays the default; an active mode adds stricter rules on top. |
| Allowed apps | Each mode has its own allowed-app list, on top of the always-allowed essentials. |
| Early exit | A mode cannot be ended early. The existing emergency unlock still works during a mode. |
| Mode types | Timed session, Detox day, Weekly schedule, Scheduled one-off. |
| Editing | A mode can be edited or deleted only while it is not active. |
| Tamper guard | While a mode is active, Settings, the package installer and MIUI Security are blocked. |
| Overlapping modes | An app is allowed only if every active mode allows it. Blocking lasts until the last active mode ends. |
| Quiz free time during a mode | Paused, not lost. Remaining time and any quiz rewards earned during the mode become available after it ends. |
| Enforcement model | Rules are evaluated on each window change from stored modes and the clock. No alarms. |
| Storage | DataStore (already a dependency) holding the mode list as JSON via kotlinx.serialization. |

"Can't be closed" is not achievable for a normal Android app: only kiosk / device-owner mode can
prevent it, and that needs a factory-reset provisioning flow. This design makes Lenga hard to
switch off during a mode and makes it visible when the system has stopped it. It does not claim to
make that impossible.

## Architecture

```
FocusMode (data)            id, name, type, allowedPackages
                            type = Timed(start, end) | Detox(start, end) |
                                   Weekly(days, from, to) | OneOff(start, end)

ModeRepository              DataStore + JSON; exposes Flow<List<FocusMode>>;
                            add / update / delete; rejects update/delete of an
                            active mode; drops expired Timed/Detox modes on load

FocusPolicy (pure Kotlin)   activeModes(now, modes, zone)
                            decide(pkg, now, modes, essentials, freeTime,
                                   emergencyActive) -> Allow | Block(reason, endsAt)
                            no Android dependencies; unit tested

BlockingState (existing)    adds banked free time: pauses quiz free time while a
                            mode is active and restores it afterwards

StudyAccessibilityService   collects ModeRepository into an in-memory cache;
                            calls FocusPolicy on each TYPE_WINDOW_STATE_CHANGED;
                            shows an ongoing "Focus: 1h 23m left" notification
                            while a mode is active

UI                          dashboard banner + "Start focus" card,
                            Modes list, Edit mode, App picker
```

### Data model

- `FocusMode.id`: UUID string.
- `Timed` and `Detox` store absolute start/end epoch millis. They are created by the dashboard
  quick actions, never by the Edit screen.
- `Weekly` stores a set of `DayOfWeek` values plus `from`/`to` as local times (minutes since
  midnight). If `to <= from`, the window crosses midnight and belongs to the day it starts on.
  For example, Mon 22:00–06:00 is active from Monday 22:00 until Tuesday 06:00.
- `OneOff` stores absolute start/end epoch millis.
- `allowedPackages`: set of package names chosen in the App picker.

### Decision order in `FocusPolicy.decide`

1. **Essential package** (Lenga, system UI, launcher, keyboards, dialer, SMS, emergency,
   permission controller): **Allow**. This is the existing essentials list from
   `StudyAccessibilityService.buildAllowedPackages()`, minus the Settings packages.
2. **Emergency unlock active**: **Allow** everything, including Settings.
3. **One or more modes active**:
   - Settings (`com.android.settings`), package installers (`com.android.packageinstaller`,
     `com.google.android.packageinstaller`) and MIUI Security (`com.miui.securitycenter`):
     **Block**, with reason "Settings are locked during focus sessions".
   - The package is in *every* active mode's `allowedPackages`: **Allow**.
   - Otherwise: **Block**, naming the mode that blocks the app and ending when the last active mode ends.
4. **No mode active**: current behaviour. Allow Settings; block everything else unless quiz free
   time is running.

Emergency unlock is currently implemented as a free-time grant. It must become a separate state
(`emergencyUntil`) so it can override an active mode while quiz free time does not.

### Pausing quiz free time

`BlockingState` gains `bankedMillis`.

- When the service first sees a mode active and `unlockedUntil > sessionStart`, it moves
  `unlockedUntil - sessionStart` into `bankedMillis` and clears `unlockedUntil`. `sessionStart`
  is the earliest start time among the active modes, so detecting the mode late loses nothing.
- Quiz rewards granted while a mode is active are added to `bankedMillis`.
- When the service first sees no mode active and `bankedMillis > 0`, it sets
  `unlockedUntil = now + bankedMillis` and clears `bankedMillis`.

The transitions are idempotent and are also run when the app starts, so a killed or late service
cannot double-count.

## UI

### Dashboard (`StudyDashboardScreen`)

- **Active-mode banner** at the top while any mode is active. It shows the active mode names, the
  end time and a live countdown, plus "Free time paused: 12 min" when time is banked.
- **Start focus card** when no mode is active:
  - *Timed session*: 30 min / 1 h / 2 h / custom duration. The app list starts as a copy of an
    existing mode's list or as essentials only, and can be edited before starting.
  - *Detox day*: until midnight or until a chosen date. Essentials only by default; the app list
    can be edited before starting.
  - Both show a confirmation first: "You can't end this early. Emergency unlock (2/day) still works."
- A **Modes** icon in the top bar.

### Modes screen (`modes`)

Lists Weekly and One-off modes with their next start time. Active modes show a lock icon and
can't be opened for editing. Add button → Edit mode.

### Edit mode screen (`mode_edit/{id}`, `id = new` for create)

- Name, type (Weekly / One-off), time fields (days + from/to, or start/end date-time), allowed
  apps (opens App picker).
- Save is rejected with an inline message when the mode is active, when a One-off ends in the
  past or ends before it starts, or when a Weekly mode has no days.
- Delete is available only while the mode is inactive.

### App picker (full-screen dialog)

Opened from the Edit mode screen and the Start focus dialog as a dialog rather than a separate
route, so the selection returns directly to its caller. A searchable list of launchable apps (icon, label) with checkboxes. Essentials are shown checked
and disabled. The manifest gains a `<queries>` entry for `MAIN`/`LAUNCHER`; `QUERY_ALL_PACKAGES`
is not needed.

### Blocking feedback

Lenga is brought to the front as it is today. The toast names the reason:
- "Blocked by School week until 16:00"
- "Settings are locked during focus sessions"

### Ongoing notification

While a mode is active, the accessibility service posts an ongoing notification, "Focus: School
week · 1h 23m left", updated every minute and removed when no mode is active. It needs
`POST_NOTIFICATIONS` on Android 13+; permission is requested the first time a mode is started or
saved. Blocking still works if permission is denied; only the notification is missing.

## Edge cases

- **Reboot**: the accessibility service starts after the first unlock. Modes are persisted and
  evaluated against the clock, so a mode that began while the phone was off applies immediately.
- **Service killed** (MIUI swipe-away): enforcement stops; this is a platform limit. The ongoing
  notification disappears and the dashboard shows the existing "App blocking stopped" card. The
  Xiaomi tip (lock in Recents, allow Autostart) should be followed before starting a mode.
- **Clock tampering**: changing the date or time needs Settings, which is blocked during a mode.
  Automatic time-zone changes are accepted; Weekly windows follow local time.
- **DST**: Weekly windows are computed with `java.time` in the device zone, so a window keeps
  its local times across DST shifts. A start time inside the spring-forward gap is moved later
  by the length of the gap (java.time's rule), so 01:30 becomes 02:30.
- **Emergency unlock during a mode**: unlocks everything, including Settings, for 15 min. It
  could be used to switch Lenga off; accepted, because it needs the PIN and is limited to 2 a day.
- **Corrupt stored JSON**: treated as an empty mode list; an error is shown on the Modes screen;
  the service never crashes on it.
- **Expired modes**: Timed and Detox modes are deleted on load once ended. One-off modes stay
  (shown as "Ended") until the user deletes them.

## Testing

- **`FocusPolicyTest`** (JVM, same style as `EmergencyUnlockPolicyTest`):
  - Weekly window active/inactive at boundaries, midnight crossover, day-of-week edges.
  - One-off start (inclusive) and end (exclusive) boundaries.
  - Overlapping modes: allowed only if every active mode allows; `endsAt` is the latest end.
  - Settings, package installer and MIUI Security blocked only while a mode is active.
  - Emergency unlock overrides a mode; quiz free time does not.
  - Essentials always allowed.
  - DST spring-forward and fall-back days.
- **`FreeTimeBankingTest`**: bank on mode start (including late detection), add rewards while
  banked, restore on end, idempotency.
- **`ModeValidationTest`**: reject edit/delete of an active mode; reject invalid times.
- **Manual on the Xiaomi test phone over adb**:
  - 2-min timed session: an allowed app opens; a blocked app bounces with the right toast;
    Settings is blocked; emergency unlock works and re-locks; the session ends on time and banked
    free time resumes.
  - A Weekly mode starting 1 min from now starts on its own.
  - Reboot during a mode: blocking resumes after unlock.

## Out of scope

Pomodoro breaks, daily per-app limits, notification holding (sub-projects 2–4), kiosk / device-owner
lockdown, cloud sync of modes, and parent/teacher control of modes.
