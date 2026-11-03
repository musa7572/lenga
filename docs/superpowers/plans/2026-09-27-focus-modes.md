# Focus Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add focus modes (timed session, detox day, weekly schedule, one-off block), each with its own allowed apps. While a mode is active, Settings is locked and quiz free time is paused. The existing emergency unlock still works.

**Architecture:** Modes are stored as JSON in DataStore. A pure-Kotlin `FocusPolicy` works out which modes are active from the clock and decides, for each app that opens, whether to allow it. The accessibility service calls it on every window change, so no alarms are needed. `BlockingState` gains saved ("banked") free time and a separate emergency-unlock end time.

**Tech Stack:** Kotlin 2.0, Jetpack Compose (Material3 via BOM 2024.04.01), DataStore Preferences 1.0, kotlinx.serialization 1.7, java.time (minSdk 26), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-27-focus-modes-design.md`

**Conventions used throughout:**
- Run unit tests: `./gradlew :app:testDebugUnitTest --tests "app.lenga.<TestClass>"` from the repo root (Git Bash).
- Build: `./gradlew :app:assembleDebug -q`. A "Daemon compilation failed" message followed by exit code 0 is harmless. Gradle falls back to in-process compilation.
- Commit on the current branch `feature/focus-modes`. End every commit message with the line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Unit tests live flat in `app/src/test/java/app/lenga/`, like `EmergencyUnlockPolicyTest.kt`.

---

## File structure

New package `app.lenga.focus` (`app/src/main/java/app/lenga/focus/`). Everything in it except `ModeRepository` is pure Kotlin:

| File | Responsibility |
|---|---|
| `FocusMode.kt` | `FocusMode` and `ModeSchedule` (Timed, Detox, Weekly, OneOff) data model |
| `FocusModeCodec.kt` | JSON encode/decode of the mode list, with corrupt-data detection (`ModesLoad`) |
| `FocusPolicy.kt` | `ActiveMode`, `Decision`, time windows, `activeModes`, `nextStart`, `decide` |
| `ModeValidation.kt` | Validation of new or edited modes, and the "can't change a running mode" rule |
| `FreeTimeBanking.kt` | `FreeTime` state and pure grant/pause/resume arithmetic |
| `FocusFormat.kt` | Text formatting: remaining time, clock times, schedule descriptions |
| `FocusStatus.kt` | `FocusStatus` (what the UI shows) and the `FocusKind` enum |
| `ModeRepository.kt` | DataStore-backed save/delete/load of modes |

Other new files:

| File | Responsibility |
|---|---|
| `service/EssentialApps.kt` | Always-allowed packages, moved out of the service |
| `service/FocusNotification.kt` | The ongoing "Focus: … left" notification |
| `data/apps/LaunchableApps.kt` | List of launchable apps with labels and icons, for the picker |
| `ui/components/FocusCards.kt` | Dashboard `FocusBanner` and `StartFocusCard` |
| `ui/components/StartFocusDialog.kt` | Dialog for starting a timed session or detox day |
| `ui/components/AppPickerDialog.kt` | Full-screen searchable app picker |
| `ui/components/PickerDialogs.kt` | `TimeDialog`, `DateDialog` |
| `ui/components/NotificationPermission.kt` | `rememberNotificationPermissionRequest()` |
| `ui/screens/ModesScreen.kt` | List of modes |
| `ui/screens/EditModeScreen.kt` | Create or edit a weekly or one-off mode |

Modified: `service/BlockingState.kt`, `service/StudyAccessibilityService.kt`, `viewmodel/StudyViewModel.kt`, `ui/screens/StudyDashboardScreen.kt`, `ui/navigation/StudyNavHost.kt`, `AndroidManifest.xml`.

---

### Task 1: Mode data model and JSON codec

**Files:**
- Create: `app/src/main/java/app/lenga/focus/FocusMode.kt`
- Create: `app/src/main/java/app/lenga/focus/FocusModeCodec.kt`
- Test: `app/src/test/java/app/lenga/FocusModeCodecTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.FocusModeCodecTest"`
Expected: FAIL. Compilation error "Unresolved reference: focus".

- [ ] **Step 3: Write the model**

`FocusMode.kt`:

```kotlin
package app.lenga.focus

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A saved restriction: when it applies and which apps stay usable while it does. */
@Serializable
data class FocusMode(
    val id: String,
    val name: String,
    val schedule: ModeSchedule,
    /** Apps usable during this mode on top of the always-allowed essentials. */
    val allowedPackages: Set<String> = emptySet()
)

@Serializable
sealed class ModeSchedule {
    /** Started from the dashboard for a chosen length. Deleted once it ends. */
    @Serializable
    @SerialName("timed")
    data class Timed(val start: Long, val end: Long) : ModeSchedule()

    /** Started from the dashboard; runs until midnight or a chosen date. Deleted once it ends. */
    @Serializable
    @SerialName("detox")
    data class Detox(val start: Long, val end: Long) : ModeSchedule()

    /**
     * Repeats on [days] (ISO numbers, Monday = 1) from [fromMinute] to [toMinute], both in minutes
     * since local midnight. If [toMinute] <= [fromMinute], the window ends the next day.
     */
    @Serializable
    @SerialName("weekly")
    data class Weekly(val days: Set<Int>, val fromMinute: Int, val toMinute: Int) : ModeSchedule()

    /** A single future block. Kept after it ends until the user deletes it. */
    @Serializable
    @SerialName("one_off")
    data class OneOff(val start: Long, val end: Long) : ModeSchedule()
}
```

`FocusModeCodec.kt`:

```kotlin
package app.lenga.focus

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stored modes plus whether the stored data couldn't be read. */
data class ModesLoad(val modes: List<FocusMode>, val corrupt: Boolean = false)

object FocusModeCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(modes: List<FocusMode>): String = json.encodeToString(modes)

    fun decode(text: String?): ModesLoad {
        if (text.isNullOrBlank()) return ModesLoad(emptyList())
        return try {
            ModesLoad(json.decodeFromString<List<FocusMode>>(text))
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException
            ModesLoad(emptyList(), corrupt = true)
        }
    }
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.FocusModeCodecTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lenga/focus app/src/test/java/app/lenga/FocusModeCodecTest.kt
git commit -m "Add focus mode model and JSON codec" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Active-mode windows and next start

**Files:**
- Create: `app/src/main/java/app/lenga/focus/FocusPolicy.kt`
- Test: `app/src/test/java/app/lenga/FocusPolicyTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
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
}
```

- [ ] **Step 2: Run the tests to confirm they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.FocusPolicyTest"`
Expected: FAIL. "Unresolved reference: FocusPolicy". The imports of `ActiveMode` and `Decision` also fail until this task and Task 3 are done.

- [ ] **Step 3: Implement windows**

`FocusPolicy.kt`. `Decision` and `decide` are added in Task 3; define `Decision` now so the test file compiles:

```kotlin
package app.lenga.focus

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A mode that is active now, with the start and end of its current window (epoch millis). */
data class ActiveMode(val mode: FocusMode, val start: Long, val end: Long)

/** What to do when an app comes to the foreground. */
sealed interface Decision {
    data object Allow : Decision
    data object SettingsLocked : Decision
    data class BlockedByMode(val modeName: String, val until: Long) : Decision
    data object NoFreeTime : Decision
}

/**
 * Rules for focus modes. Pure functions over timestamps and a time zone, so they can be
 * unit tested without Android. Windows include their start and exclude their end.
 */
object FocusPolicy {

    fun activeModes(now: Long, modes: List<FocusMode>, zone: ZoneId): List<ActiveMode> =
        modes.mapNotNull { mode ->
            window(mode.schedule, now, zone)?.let { (start, end) -> ActiveMode(mode, start, end) }
        }

    /** The window of [schedule] that contains [now], as (start, end), or null if it isn't active. */
    fun window(schedule: ModeSchedule, now: Long, zone: ZoneId): Pair<Long, Long>? = when (schedule) {
        is ModeSchedule.Timed -> absolute(schedule.start, schedule.end, now)
        is ModeSchedule.Detox -> absolute(schedule.start, schedule.end, now)
        is ModeSchedule.OneOff -> absolute(schedule.start, schedule.end, now)
        is ModeSchedule.Weekly -> weeklyWindow(schedule, now, zone)
    }

    /** When [schedule] next starts after [now], or null if it never will. */
    fun nextStart(schedule: ModeSchedule, now: Long, zone: ZoneId): Long? = when (schedule) {
        is ModeSchedule.Weekly -> {
            val today = localDate(now, zone)
            (0L..7L).asSequence()
                .map { today.plusDays(it) }
                .filter { it.dayOfWeek.value in schedule.days }
                .map { startOf(it, schedule.fromMinute, zone) }
                .firstOrNull { it > now }
        }
        is ModeSchedule.OneOff -> schedule.start.takeIf { it > now }
        is ModeSchedule.Timed, is ModeSchedule.Detox -> null
    }

    /** Timed sessions and detox days are thrown away once they end. */
    fun isDisposable(schedule: ModeSchedule, now: Long): Boolean = when (schedule) {
        is ModeSchedule.Timed -> schedule.end <= now
        is ModeSchedule.Detox -> schedule.end <= now
        is ModeSchedule.Weekly, is ModeSchedule.OneOff -> false
    }

    private fun absolute(start: Long, end: Long, now: Long): Pair<Long, Long>? =
        if (now >= start && now < end) start to end else null

    private fun weeklyWindow(schedule: ModeSchedule.Weekly, now: Long, zone: ZoneId): Pair<Long, Long>? {
        val today = localDate(now, zone)
        // A window that crosses midnight belongs to the day it starts, so also check yesterday's
        for (date in listOf(today, today.minusDays(1))) {
            if (date.dayOfWeek.value !in schedule.days) continue
            val start = startOf(date, schedule.fromMinute, zone)
            val endDate = if (schedule.toMinute <= schedule.fromMinute) date.plusDays(1) else date
            val end = startOf(endDate, schedule.toMinute, zone)
            if (now >= start && now < end) return start to end
        }
        return null
    }

    /**
     * [minute] of [date] as epoch millis. A local time inside a spring-forward gap is moved later
     * by the length of the gap (java.time's rule), so 01:30 becomes 02:30.
     */
    private fun startOf(date: LocalDate, minute: Int, zone: ZoneId): Long =
        date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zone).toInstant().toEpochMilli()

    private fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
```

The test file imports `Decision` and `ActiveMode`. Both are now defined, so it compiles.

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.FocusPolicyTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lenga/focus/FocusPolicy.kt app/src/test/java/app/lenga/FocusPolicyTest.kt
git commit -m "Work out active focus mode windows" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Allow/block decision

**Files:**
- Modify: `app/src/main/java/app/lenga/focus/FocusPolicy.kt`
- Test: `app/src/test/java/app/lenga/FocusPolicyTest.kt`

- [ ] **Step 1: Add failing tests** at the end of `FocusPolicyTest`, before the final `}`:

```kotlin
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
```

- [ ] **Step 2: Run the tests to confirm they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.FocusPolicyTest"`
Expected: FAIL. "Unresolved reference: decide" and "SETTINGS_PACKAGES".

- [ ] **Step 3: Implement `decide`.** Add inside `object FocusPolicy`, directly after the `object FocusPolicy {` line:

```kotlin
    /**
     * Apps that could switch Lenga off or remove it. Locked while a mode is active,
     * usable otherwise.
     */
    val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.securitycenter"
    )

    /**
     * Whether [pkg] may be used now. [active] are the modes active now; [essentials] are
     * always allowed; [freeTimeActive] is quiz free time; [emergencyActive] is an emergency unlock.
     */
    fun decide(
        pkg: String,
        active: List<ActiveMode>,
        essentials: Set<String>,
        freeTimeActive: Boolean,
        emergencyActive: Boolean
    ): Decision {
        if (pkg in essentials || emergencyActive) return Decision.Allow
        if (active.isNotEmpty()) {
            if (pkg in SETTINGS_PACKAGES) return Decision.SettingsLocked
            val blocker = active.firstOrNull { pkg !in it.mode.allowedPackages } ?: return Decision.Allow
            return Decision.BlockedByMode(blocker.mode.name, active.maxOf { it.end })
        }
        if (pkg in SETTINGS_PACKAGES || freeTimeActive) return Decision.Allow
        return Decision.NoFreeTime
    }
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.FocusPolicyTest"`
Expected: PASS (14 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lenga/focus/FocusPolicy.kt app/src/test/java/app/lenga/FocusPolicyTest.kt
git commit -m "Decide whether an app may open under focus modes" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Mode validation

**Files:**
- Create: `app/src/main/java/app/lenga/focus/ModeValidation.kt`
- Test: `app/src/test/java/app/lenga/ModeValidationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.lenga

import app.lenga.focus.FocusMode
import app.lenga.focus.ModeSchedule
import app.lenga.focus.ModeValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ModeValidationTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private fun at(day: Int, hour: Int) =
        LocalDateTime.of(2026, 9, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val now = at(28, 10) // Monday 10:00
    private val schoolWeek = FocusMode("s", "School week", ModeSchedule.Weekly(setOf(1, 2, 3, 4, 5), 480, 960))

    @Test
    fun acceptsAValidWeeklyMode() {
        assertNull(ModeValidation.validate(schoolWeek, now))
    }

    @Test
    fun rejectsBlankNames() {
        assertNotNull(ModeValidation.validate(schoolWeek.copy(name = "  "), now))
    }

    @Test
    fun rejectsWeeklyWithoutDaysOrWithEqualTimes() {
        assertNotNull(ModeValidation.validate(schoolWeek.copy(schedule = ModeSchedule.Weekly(emptySet(), 480, 960)), now))
        assertNotNull(ModeValidation.validate(schoolWeek.copy(schedule = ModeSchedule.Weekly(setOf(1), 480, 480)), now))
        assertNotNull(ModeValidation.validate(schoolWeek.copy(schedule = ModeSchedule.Weekly(setOf(1), 480, 1440)), now))
    }

    @Test
    fun rejectsOneOffsThatEndBeforeStartOrInThePast() {
        val backwards = FocusMode("e", "Exam", ModeSchedule.OneOff(at(29, 13), at(29, 9)))
        val past = FocusMode("e", "Exam", ModeSchedule.OneOff(at(27, 9), at(27, 13)))
        assertNotNull(ModeValidation.validate(backwards, now))
        assertNotNull(ModeValidation.validate(past, now))
        assertNull(ModeValidation.validate(FocusMode("e", "Exam", ModeSchedule.OneOff(at(29, 9), at(29, 13))), now))
    }

    @Test
    fun runningModesCantBeChanged() {
        assertEquals(ModeValidation.RUNNING_ERROR, ModeValidation.checkModify(schoolWeek, now, zone))
        assertNull(ModeValidation.checkModify(schoolWeek, at(27, 10), zone)) // Sunday: not running
        assertNull(ModeValidation.checkModify(null, now, zone)) // brand-new mode
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.ModeValidationTest"`
Expected: FAIL. "Unresolved reference: ModeValidation".

- [ ] **Step 3: Implement**

```kotlin
package app.lenga.focus

import java.time.ZoneId

/** Checks applied before a mode is saved or deleted. Each returns an error message or null. */
object ModeValidation {
    const val RUNNING_ERROR = "This mode is running and can't be changed until it ends."

    /** A mode can't be edited or deleted while it is active. [existing] is null for a new mode. */
    fun checkModify(existing: FocusMode?, now: Long, zone: ZoneId): String? =
        if (existing != null && FocusPolicy.window(existing.schedule, now, zone) != null) RUNNING_ERROR else null

    fun validate(mode: FocusMode, now: Long): String? {
        if (mode.name.isBlank()) return "Give the mode a name."
        return when (val schedule = mode.schedule) {
            is ModeSchedule.Weekly -> when {
                schedule.days.isEmpty() -> "Pick at least one day."
                schedule.days.any { it !in 1..7 } ||
                    schedule.fromMinute !in 0 until MINUTES_PER_DAY ||
                    schedule.toMinute !in 0 until MINUTES_PER_DAY -> "That time isn't valid."
                schedule.fromMinute == schedule.toMinute -> "Start and end times must be different."
                else -> null
            }
            is ModeSchedule.OneOff -> checkSpan(schedule.start, schedule.end, now)
            is ModeSchedule.Timed -> checkSpan(schedule.start, schedule.end, now)
            is ModeSchedule.Detox -> checkSpan(schedule.start, schedule.end, now)
        }
    }

    private fun checkSpan(start: Long, end: Long, now: Long): String? = when {
        end <= start -> "The end must be after the start."
        end <= now -> "The end must be in the future."
        else -> null
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.ModeValidationTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lenga/focus/ModeValidation.kt app/src/test/java/app/lenga/ModeValidationTest.kt
git commit -m "Validate focus modes and lock running ones" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Free-time banking and a separate emergency unlock

**Files:**
- Create: `app/src/main/java/app/lenga/focus/FreeTimeBanking.kt`
- Modify: `app/src/main/java/app/lenga/service/BlockingState.kt` (whole file)
- Modify: `app/src/main/java/app/lenga/viewmodel/StudyViewModel.kt` (emergency unlock and free-time total)
- Test: `app/src/test/java/app/lenga/FreeTimeBankingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.FreeTimeBankingTest"`
Expected: FAIL. "Unresolved reference: FreeTime".

- [ ] **Step 3: Implement `FreeTimeBanking.kt`**

```kotlin
package app.lenga.focus

/**
 * Unlock state. Quiz free time runs until [unlockedUntil]; [totalMillis] is its full length, for
 * progress display. [bankedMillis] is quiz free time paused by a focus mode. [emergencyUntil] is
 * a separate emergency unlock that also works during a focus mode.
 */
data class FreeTime(
    val unlockedUntil: Long = 0L,
    val totalMillis: Long = 0L,
    val bankedMillis: Long = 0L,
    val emergencyUntil: Long = 0L
)

/** Pure arithmetic for granting, pausing and resuming quiz free time around focus modes. */
object FreeTimeBanking {

    /** Adds [minutes] of quiz free time: banked during a session, otherwise started or extended. */
    fun grant(freeTime: FreeTime, minutes: Int, sessionActive: Boolean, now: Long): FreeTime {
        if (minutes <= 0) return freeTime
        val grantMillis = minutes * 60_000L
        if (sessionActive) return freeTime.copy(bankedMillis = freeTime.bankedMillis + grantMillis)
        val stillRunning = freeTime.unlockedUntil > now
        val start = if (stillRunning) freeTime.unlockedUntil else now
        return freeTime.copy(
            unlockedUntil = start + grantMillis,
            totalMillis = if (stillRunning) freeTime.totalMillis + grantMillis else grantMillis
        )
    }

    /**
     * Pauses or resumes free time to match whether a session is running. [sessionStart] is the
     * earliest start among active modes, or null when none is active. Safe to call repeatedly.
     */
    fun sync(freeTime: FreeTime, sessionStart: Long?, now: Long): FreeTime = when {
        sessionStart != null && freeTime.unlockedUntil > sessionStart -> freeTime.copy(
            unlockedUntil = 0L,
            totalMillis = 0L,
            bankedMillis = freeTime.bankedMillis + (freeTime.unlockedUntil - sessionStart)
        )
        sessionStart == null && freeTime.bankedMillis > 0 -> freeTime.copy(
            unlockedUntil = now + freeTime.bankedMillis,
            totalMillis = freeTime.bankedMillis,
            bankedMillis = 0L
        )
        else -> freeTime
    }
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.FreeTimeBankingTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Replace `BlockingState.kt`**

Replace the whole of `app/src/main/java/app/lenga/service/BlockingState.kt` with:

```kotlin
package app.lenga.service

import android.content.Context
import android.content.SharedPreferences
import app.lenga.focus.FreeTime
import app.lenga.focus.FreeTimeBanking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for unlock state: quiz free time, free time paused by a focus mode,
 * and emergency unlocks. Times are stored as absolute timestamps, so they survive process
 * death and expire on their own without a timer.
 */
object BlockingState {
    private const val PREFS_NAME = "study_mode"
    private const val KEY_UNLOCKED_UNTIL = "unlocked_until"
    private const val KEY_FREE_TIME_TOTAL = "free_time_total_ms"
    private const val KEY_BANKED = "free_time_banked_ms"
    private const val KEY_EMERGENCY_UNTIL = "emergency_until"

    private lateinit var prefs: SharedPreferences

    private val _freeTime = MutableStateFlow(FreeTime())
    val freeTime: StateFlow<FreeTime> = _freeTime.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    /** True while [StudyAccessibilityService] is connected, i.e. blocking can be enforced. */
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _freeTime.value = FreeTime(
            unlockedUntil = prefs.getLong(KEY_UNLOCKED_UNTIL, 0L),
            totalMillis = prefs.getLong(KEY_FREE_TIME_TOTAL, 0L),
            bankedMillis = prefs.getLong(KEY_BANKED, 0L),
            emergencyUntil = prefs.getLong(KEY_EMERGENCY_UNTIL, 0L)
        )
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    /** Grants [minutes] of quiz free time; during a focus mode it's saved for after the mode. */
    fun grantFreeTime(minutes: Int, sessionActive: Boolean, now: Long = System.currentTimeMillis()) {
        save(FreeTimeBanking.grant(_freeTime.value, minutes, sessionActive, now))
    }

    /** Unlocks everything, including during a focus mode, for [minutes]. */
    fun grantEmergency(minutes: Int, now: Long = System.currentTimeMillis()) {
        save(_freeTime.value.copy(emergencyUntil = now + minutes * 60_000L))
    }

    /** Pauses or resumes quiz free time; [sessionStart] is null when no focus mode is active. */
    fun syncWithSession(sessionStart: Long?, now: Long = System.currentTimeMillis()) {
        val next = FreeTimeBanking.sync(_freeTime.value, sessionStart, now)
        if (next != _freeTime.value) save(next)
    }

    /** Ends running free time and any emergency unlock. Paused free time is kept. */
    fun endFreeTime() {
        save(_freeTime.value.copy(unlockedUntil = 0L, totalMillis = 0L, emergencyUntil = 0L))
    }

    fun isFreeTimeActive(now: Long = System.currentTimeMillis()): Boolean = _freeTime.value.unlockedUntil > now

    fun isEmergencyActive(now: Long = System.currentTimeMillis()): Boolean = _freeTime.value.emergencyUntil > now

    /** Seconds until apps lock again, from whichever of free time or emergency unlock ends later. */
    fun remainingFreeTimeSeconds(now: Long = System.currentTimeMillis()): Int {
        val until = maxOf(_freeTime.value.unlockedUntil, _freeTime.value.emergencyUntil)
        val remainingMillis = until - now
        return if (remainingMillis <= 0) 0 else ((remainingMillis + 999) / 1000).toInt()
    }

    private fun save(freeTime: FreeTime) {
        _freeTime.value = freeTime
        prefs.edit()
            .putLong(KEY_UNLOCKED_UNTIL, freeTime.unlockedUntil)
            .putLong(KEY_FREE_TIME_TOTAL, freeTime.totalMillis)
            .putLong(KEY_BANKED, freeTime.bankedMillis)
            .putLong(KEY_EMERGENCY_UNTIL, freeTime.emergencyUntil)
            .apply()
    }
}
```

`isBlockingNow()` is removed, and its only caller (the service) is rewritten in Task 9. Until then the build fails. Steps 6–7 temporarily fix the callers so the app still compiles.

- [ ] **Step 6: Update the ViewModel callers** in `app/src/main/java/app/lenga/viewmodel/StudyViewModel.kt`:

Replace

```kotlin
                    BlockingState.grantFreeTime(module?.rewardMinutes ?: result.rewardMinutes)
```

with (Task 10 replaces `false` with the real session check):

```kotlin
                    BlockingState.grantFreeTime(module?.rewardMinutes ?: result.rewardMinutes, sessionActive = false)
```

Replace

```kotlin
                    BlockingState.grantFreeTime(EMERGENCY_UNLOCK_MINUTES)
```

with

```kotlin
                    BlockingState.grantEmergency(EMERGENCY_UNLOCK_MINUTES)
```

Replace

```kotlin
    fun freeTimeTotalSeconds(): Int = (BlockingState.freeTime.value.totalMillis / 1000).toInt()
```

with

```kotlin
    /** Full length of the unlock the reward screen is counting down, for its progress ring. */
    fun freeTimeTotalSeconds(): Int {
        val freeTime = BlockingState.freeTime.value
        val emergencyIsLonger = freeTime.emergencyUntil > freeTime.unlockedUntil &&
            freeTime.emergencyUntil > System.currentTimeMillis()
        return if (emergencyIsLonger) EMERGENCY_UNLOCK_MINUTES * 60 else (freeTime.totalMillis / 1000).toInt()
    }
```

- [ ] **Step 7: Temporarily update the service** in `app/src/main/java/app/lenga/service/StudyAccessibilityService.kt`. Replace

```kotlin
        if (packageName in allowedPackages || !BlockingState.isBlockingNow()) return
```

with

```kotlin
        if (packageName in allowedPackages || BlockingState.isFreeTimeActive() || BlockingState.isEmergencyActive()) return
```

- [ ] **Step 8: Build and run all unit tests**

Run: `./gradlew :app:testDebugUnitTest -q && ./gradlew :app:assembleDebug -q`
Expected: both exit 0, and every test passes, including `EmergencyUnlockPolicyTest`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/app/lenga/focus/FreeTimeBanking.kt app/src/main/java/app/lenga/service app/src/main/java/app/lenga/viewmodel/StudyViewModel.kt app/src/test/java/app/lenga/FreeTimeBankingTest.kt
git commit -m "Bank quiz free time and track emergency unlocks separately" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Text formatting

**Files:**
- Create: `app/src/main/java/app/lenga/focus/FocusFormat.kt`
- Test: `app/src/test/java/app/lenga/FocusFormatTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.lenga

import app.lenga.focus.FocusFormat
import app.lenga.focus.ModeSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class FocusFormatTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private val en = Locale.ENGLISH
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 9, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun remainingRoundsUpToWholeMinutes() {
        assertEquals("0m", FocusFormat.remaining(0))
        assertEquals("2m", FocusFormat.remaining(90_000))
        assertEquals("1h 23m", FocusFormat.remaining((60 + 23) * 60_000L))
        assertEquals("2h 0m", FocusFormat.remaining(120 * 60_000L))
    }

    @Test
    fun minuteOfDayIsZeroPadded() {
        assertEquals("08:05", FocusFormat.minuteOfDay(485))
        assertEquals("00:00", FocusFormat.minuteOfDay(0))
    }

    @Test
    fun weeklyDaysCollapseIntoRanges() {
        assertEquals("Mon–Fri 08:00–16:00", FocusFormat.schedule(ModeSchedule.Weekly(setOf(1, 2, 3, 4, 5), 480, 960), zone, en))
        assertEquals("Mon, Wed 22:00–06:00", FocusFormat.schedule(ModeSchedule.Weekly(setOf(3, 1), 1320, 360), zone, en))
        assertEquals("Every day 09:00–10:00", FocusFormat.schedule(ModeSchedule.Weekly((1..7).toSet(), 540, 600), zone, en))
    }

    @Test
    fun oneOffShowsTheEndDateOnlyWhenItDiffers() {
        assertEquals("Sat 26 Sep 09:00 – 13:00", FocusFormat.schedule(ModeSchedule.OneOff(at(26, 9), at(26, 13)), zone, en))
        assertEquals("Sat 26 Sep 22:00 – Sun 27 Sep 06:00", FocusFormat.schedule(ModeSchedule.OneOff(at(26, 22), at(27, 6)), zone, en))
    }

    @Test
    fun detoxShowsItsLastDay() {
        assertEquals("Until the end of Sun 27 Sep", FocusFormat.schedule(ModeSchedule.Detox(at(26, 9), at(28, 0)), zone, en))
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.FocusFormatTest"`
Expected: FAIL. "Unresolved reference: FocusFormat".

- [ ] **Step 3: Implement**

```kotlin
package app.lenga.focus

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Text for focus-mode UI, notifications and toasts. */
object FocusFormat {
    private val clockPattern = DateTimeFormatter.ofPattern("HH:mm")

    /** Time left, rounded up to whole minutes: "1h 23m" or "12m". */
    fun remaining(millis: Long): String {
        val minutes = (millis.coerceAtLeast(0) + 59_999) / 60_000
        val hours = minutes / 60
        return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
    }

    fun minuteOfDay(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

    fun clock(epochMillis: Long, zone: ZoneId): String = local(epochMillis, zone).format(clockPattern)

    fun dayTime(epochMillis: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        local(epochMillis, zone).format(DateTimeFormatter.ofPattern("EEE HH:mm", locale))

    fun date(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

    fun schedule(schedule: ModeSchedule, zone: ZoneId, locale: Locale = Locale.getDefault()): String = when (schedule) {
        is ModeSchedule.Weekly ->
            "${days(schedule.days, locale)} ${minuteOfDay(schedule.fromMinute)}–${minuteOfDay(schedule.toMinute)}"
        is ModeSchedule.OneOff -> span(schedule.start, schedule.end, zone, locale)
        is ModeSchedule.Timed -> "Until ${clock(schedule.end, zone)}"
        // A detox day ends at midnight, so describe the last full day rather than 00:00
        is ModeSchedule.Detox -> "Until the end of ${date(local(schedule.end - 1, zone).toLocalDate(), locale)}"
    }

    private fun days(days: Set<Int>, locale: Locale): String {
        val sorted = days.sorted()
        fun name(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)
        return when {
            sorted.size == 7 -> "Every day"
            sorted.size >= 3 && sorted.last() - sorted.first() + 1 == sorted.size ->
                "${name(sorted.first())}–${name(sorted.last())}"
            else -> sorted.joinToString(", ") { name(it) }
        }
    }

    private fun span(start: Long, end: Long, zone: ZoneId, locale: Locale): String {
        val from = local(start, zone)
        val to = local(end, zone)
        val startText = "${date(from.toLocalDate(), locale)} ${from.format(clockPattern)}"
        val endText = if (from.toLocalDate() == to.toLocalDate()) {
            to.format(clockPattern)
        } else {
            "${date(to.toLocalDate(), locale)} ${to.format(clockPattern)}"
        }
        return "$startText – $endText"
    }

    private fun local(epochMillis: Long, zone: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.lenga.FocusFormatTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lenga/focus/FocusFormat.kt app/src/test/java/app/lenga/FocusFormatTest.kt
git commit -m "Format focus mode times and schedules" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Mode repository and focus status

**Files:**
- Create: `app/src/main/java/app/lenga/focus/ModeRepository.kt`
- Create: `app/src/main/java/app/lenga/focus/FocusStatus.kt`

The repository is a thin DataStore wrapper around code that's already tested, so it has no unit test. It's covered by the on-device check in Task 13.

- [ ] **Step 1: Create `ModeRepository.kt`**

```kotlin
package app.lenga.focus

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId

private val Context.focusModeStore: DataStore<Preferences> by preferencesDataStore(name = "focus_modes")

/** Saved focus modes. Finished timed sessions and detox days are dropped on load and on save. */
class ModeRepository(context: Context) {
    private val store = context.applicationContext.focusModeStore
    private val key = stringPreferencesKey("modes")

    val load: Flow<ModesLoad> = store.data.map { prefs ->
        val loaded = FocusModeCodec.decode(prefs[key])
        loaded.copy(modes = withoutFinished(loaded.modes, System.currentTimeMillis()))
    }

    val modes: Flow<List<FocusMode>> = load.map { it.modes }

    /** Adds or replaces [mode]. Returns an error message, or null when saved. */
    suspend fun save(
        mode: FocusMode,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String? {
        var error: String? = null
        store.edit { prefs ->
            val current = withoutFinished(FocusModeCodec.decode(prefs[key]).modes, now)
            val existing = current.find { it.id == mode.id }
            error = ModeValidation.checkModify(existing, now, zone) ?: ModeValidation.validate(mode, now)
            if (error == null) prefs[key] = FocusModeCodec.encode(current.filterNot { it.id == mode.id } + mode)
        }
        return error
    }

    /** Deletes the mode with [id]. Returns an error message, or null when deleted or already gone. */
    suspend fun delete(
        id: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String? {
        var error: String? = null
        store.edit { prefs ->
            val current = withoutFinished(FocusModeCodec.decode(prefs[key]).modes, now)
            error = ModeValidation.checkModify(current.find { it.id == id }, now, zone)
            if (error == null) prefs[key] = FocusModeCodec.encode(current.filterNot { it.id == id })
        }
        return error
    }

    private fun withoutFinished(modes: List<FocusMode>, now: Long) =
        modes.filterNot { FocusPolicy.isDisposable(it.schedule, now) }
}
```

- [ ] **Step 2: Create `FocusStatus.kt`**

```kotlin
package app.lenga.focus

/** Focus state at [now], for the UI. [bankedMillis] is quiz free time paused by the modes. */
data class FocusStatus(
    val active: List<ActiveMode> = emptyList(),
    val now: Long = 0L,
    val bankedMillis: Long = 0L
) {
    val isActive: Boolean get() = active.isNotEmpty()
    val endsAt: Long? get() = active.maxOfOrNull { it.end }
    val sessionStart: Long? get() = active.minOfOrNull { it.start }
}

/** The two kinds of mode started from the dashboard. */
enum class FocusKind { TIMED, DETOX }
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug -q`
Expected: exit 0.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/lenga/focus/ModeRepository.kt app/src/main/java/app/lenga/focus/FocusStatus.kt
git commit -m "Store focus modes in DataStore" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Essential apps, launchable apps and manifest

**Files:**
- Create: `app/src/main/java/app/lenga/service/EssentialApps.kt`
- Create: `app/src/main/java/app/lenga/data/apps/LaunchableApps.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `EssentialApps.kt`.** This is `buildAllowedPackages()` moved out of the service, without `com.android.settings`, which `FocusPolicy` now handles:

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
            "android",
            "com.android.systemui",
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
        val packageManager = context.packageManager

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapTo(allowed) { it.activityInfo.packageName }

        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.enabledInputMethodList
            ?.mapTo(allowed) { it.packageName }

        (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
            ?.defaultDialerPackage
            ?.let { allowed.add(it) }

        Telephony.Sms.getDefaultSmsPackage(context)?.let { allowed.add(it) }

        return allowed
    }
}
```

- [ ] **Step 2: Create `LaunchableApps.kt`**

```kotlin
package app.lenga.data.apps

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class LaunchableApp(val packageName: String, val label: String, val icon: ImageBitmap)

/** Apps with a launcher icon, excluding Lenga. Loading icons is slow: call off the main thread. */
object LaunchableApps {
    private const val ICON_SIZE_PX = 96

    fun load(context: Context): List<LaunchableApp> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(packageManager).toString(),
                    icon = it.loadIcon(packageManager).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImageBitmap()
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
```

- [ ] **Step 3: Update the manifest.** In `app/src/main/AndroidManifest.xml`, after the existing `<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />` line, add:

```xml
    <!-- Ongoing "Focus: … left" notification during focus modes (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Replace the `<queries>` block with:

```xml
    <!-- Lets the blocker resolve the launcher so it is never blocked, and lists
         launchable apps for the focus-mode app picker -->
    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.HOME" />
        </intent>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug -q`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lenga/service/EssentialApps.kt app/src/main/java/app/lenga/data/apps app/src/main/AndroidManifest.xml
git commit -m "List essential and launchable apps for focus modes" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Enforce focus modes in the accessibility service

**Files:**
- Create: `app/src/main/java/app/lenga/service/FocusNotification.kt`
- Modify: `app/src/main/java/app/lenga/service/StudyAccessibilityService.kt` (whole file)

- [ ] **Step 1: Create `FocusNotification.kt`**

```kotlin
package app.lenga.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.lenga.R

/** Ongoing notification shown while a focus mode is active. Skipped if notifications aren't allowed. */
class FocusNotification(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Focus sessions", NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_study_mode)
            .setContentTitle("Focus mode on")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    private companion object {
        const val CHANNEL_ID = "focus_session"
        const val NOTIFICATION_ID = 1001
    }
}
```

- [ ] **Step 2: Replace `StudyAccessibilityService.kt`** with:

```kotlin
package app.lenga.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import app.lenga.focus.ActiveMode
import app.lenga.focus.Decision
import app.lenga.focus.FocusFormat
import app.lenga.focus.FocusMode
import app.lenga.focus.FocusPolicy
import app.lenga.focus.ModeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Sends the user back to Lenga whenever an app comes to the foreground that
 * [FocusPolicy] doesn't allow right now.
 */
class StudyAccessibilityService : AccessibilityService() {

    private var essentials: Set<String> = emptySet()
    /** Null until the saved modes have loaded; free time isn't paused or resumed before then. */
    private var modes: List<FocusMode>? = null
    private var lastBlockedAt = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var notification: FocusNotification? = null

    private val tick = object : Runnable {
        override fun run() {
            refreshFocus()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        essentials = EssentialApps.resolve(this)
        notification = FocusNotification(this)
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
        val decision = FocusPolicy.decide(
            pkg = packageName,
            active = activeModes(now),
            essentials = essentials,
            freeTimeActive = BlockingState.isFreeTimeActive(now),
            emergencyActive = BlockingState.isEmergencyActive(now)
        )
        if (decision == Decision.Allow) return

        // Bring Lenga over the blocked app. Don't also press HOME: that action is
        // asynchronous and lands after this launch, hiding Lenga behind the launcher.
        val launchIntent = packageManager.getLaunchIntentForPackage(this.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(launchIntent)
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        // An app can fire several window events while opening; toast only once.
        if (now - lastBlockedAt > TOAST_DEBOUNCE_MS) {
            Toast.makeText(this, blockMessage(decision), Toast.LENGTH_SHORT).show()
        }
        lastBlockedAt = now
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        BlockingState.setServiceRunning(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        scope.cancel()
        notification?.cancel()
        BlockingState.setServiceRunning(false)
        super.onDestroy()
    }

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
    }
}
```

- [ ] **Step 3: Build and run all unit tests**

Run: `./gradlew :app:testDebugUnitTest -q && ./gradlew :app:assembleDebug -q`
Expected: both exit 0.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/lenga/service
git commit -m "Enforce focus modes in the accessibility service" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: ViewModel support for focus modes

**Files:**
- Modify: `app/src/main/java/app/lenga/viewmodel/StudyViewModel.kt`

- [ ] **Step 1: Add imports.** After `import app.lenga.service.StudyAccessibilityService`, add:

```kotlin
import app.lenga.data.apps.LaunchableApp
import app.lenga.data.apps.LaunchableApps
import app.lenga.focus.FocusKind
import app.lenga.focus.FocusMode
import app.lenga.focus.FocusPolicy
import app.lenga.focus.FocusStatus
import app.lenga.focus.ModeRepository
import app.lenga.focus.ModeSchedule
import app.lenga.focus.ModesLoad
import app.lenga.service.EssentialApps
import java.time.ZoneId
import java.util.UUID
```

(`kotlinx.coroutines.*` and `kotlinx.coroutines.flow.*` are already imported. They cover `Dispatchers`, `withContext`, `delay`, `flow`, `combine`, `first` and `filter`.)

- [ ] **Step 2: Add the focus-mode section.** Insert it directly before the line `    // ==================== App Blocking ====================`:

```kotlin
    // ==================== Focus Modes ====================

    private val modeRepository = ModeRepository(application)

    /** Saved modes; null until they've loaded. */
    val modesLoad: StateFlow<ModesLoad?> = modeRepository.load
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val clockTicks: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    /** Which modes are active, refreshed every second; null until modes have loaded. */
    val focusStatus: StateFlow<FocusStatus?> = combine(modesLoad, clockTicks, BlockingState.freeTime) { load, now, freeTime ->
        load?.let { FocusStatus(FocusPolicy.activeModes(now, it.modes, ZoneId.systemDefault()), now, freeTime.bankedMillis) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Pause or resume quiz free time even if the accessibility service isn't running
        viewModelScope.launch {
            focusStatus.filterNotNull().collect { BlockingState.syncWithSession(it.sessionStart, it.now) }
        }
    }

    /** Always-allowed apps, shown checked and disabled in the app picker. */
    val essentialPackages: Set<String> = EssentialApps.resolve(application)

    private val _launchableApps = MutableStateFlow<List<LaunchableApp>?>(null)
    /** Apps for the picker; null until [loadLaunchableApps] finishes. */
    val launchableApps: StateFlow<List<LaunchableApp>?> = _launchableApps.asStateFlow()

    fun loadLaunchableApps() {
        if (_launchableApps.value != null) return
        viewModelScope.launch {
            _launchableApps.value = withContext(Dispatchers.IO) { LaunchableApps.load(getApplication()) }
        }
    }

    fun mode(id: String): FocusMode? = modesLoad.value?.modes?.find { it.id == id }

    /** Starts a timed session or detox day now, ending at [end]. [onResult] receives an error or null. */
    fun startFocus(kind: FocusKind, end: Long, allowed: Set<String>, onResult: (String?) -> Unit) {
        val now = System.currentTimeMillis()
        val (name, schedule) = when (kind) {
            FocusKind.TIMED -> "Focus session" to ModeSchedule.Timed(now, end)
            FocusKind.DETOX -> "Detox day" to ModeSchedule.Detox(now, end)
        }
        saveMode(FocusMode(UUID.randomUUID().toString(), name, schedule, allowed), onResult)
    }

    fun saveMode(mode: FocusMode, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(modeRepository.save(mode)) }
    }

    fun deleteMode(id: String, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(modeRepository.delete(id)) }
    }

    private suspend fun isFocusActiveNow(): Boolean =
        FocusPolicy.activeModes(System.currentTimeMillis(), modeRepository.modes.first(), ZoneId.systemDefault())
            .isNotEmpty()
```

- [ ] **Step 3: Bank quiz rewards during a session.** Replace

```kotlin
                    BlockingState.grantFreeTime(module?.rewardMinutes ?: result.rewardMinutes, sessionActive = false)
```

with

```kotlin
                    BlockingState.grantFreeTime(module?.rewardMinutes ?: result.rewardMinutes, sessionActive = isFocusActiveNow())
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug -q`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/lenga/viewmodel/StudyViewModel.kt
git commit -m "Expose focus modes and status from the view model" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Shared UI components

**Files:**
- Create: `app/src/main/java/app/lenga/ui/components/PickerDialogs.kt`
- Create: `app/src/main/java/app/lenga/ui/components/AppPickerDialog.kt`
- Create: `app/src/main/java/app/lenga/ui/components/NotificationPermission.kt`
- Create: `app/src/main/java/app/lenga/ui/components/FocusCards.kt`
- Create: `app/src/main/java/app/lenga/ui/components/StartFocusDialog.kt`

- [ ] **Step 1: `PickerDialogs.kt`**

```kotlin
package app.lenga.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Picks a time of day; [onConfirm] receives minutes since midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) }
    )
}

/** Picks a calendar date. The Material date picker works in UTC midnights. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}
```

- [ ] **Step 2: `AppPickerDialog.kt`**

```kotlin
package app.lenga.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lenga.data.apps.LaunchableApp
import app.lenga.ui.theme.TextSecondary

/**
 * Full-screen, searchable app list. [essentials] are shown checked and can't be changed.
 * [onConfirm] receives the chosen packages, without the essentials.
 */
@Composable
fun AppPickerDialog(
    apps: List<LaunchableApp>?,
    essentials: Set<String>,
    initialSelection: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selected by remember { mutableStateOf(initialSelection) }
    var query by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Allowed apps", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                if (apps == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val visible = apps.filter { it.label.contains(query, ignoreCase = true) }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(visible, key = { it.packageName }) { app ->
                            val essential = app.packageName in essentials
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !essential) {
                                        selected = if (app.packageName in selected) {
                                            selected - app.packageName
                                        } else {
                                            selected + app.packageName
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(app.label)
                                    if (essential) {
                                        Text("Always allowed", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                                Checkbox(
                                    checked = essential || app.packageName in selected,
                                    onCheckedChange = null,
                                    enabled = !essential
                                )
                            }
                        }
                    }
                }
                val chosen = selected - essentials
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onConfirm(chosen) }) { Text("Done (${chosen.size})") }
                }
            }
        }
    }
}
```

- [ ] **Step 3: `NotificationPermission.kt`**

```kotlin
package app.lenga.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a function that asks for notification permission (Android 13+) if it isn't granted.
 * Focus modes work without it; only the ongoing countdown notification is missing.
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
```

- [ ] **Step 4: `FocusCards.kt`**

```kotlin
package app.lenga.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lenga.focus.FocusFormat
import app.lenga.focus.FocusStatus
import app.lenga.ui.theme.StudyModeActive
import app.lenga.ui.theme.TextSecondary
import java.time.ZoneId

/** Dashboard banner while a focus mode is active. */
@Composable
fun FocusBanner(status: FocusStatus) {
    val zone = remember { ZoneId.systemDefault() }
    val endsAt = status.endsAt ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StudyModeActive)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Focus mode on", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                "${status.active.joinToString { it.mode.name }} · until ${FocusFormat.clock(endsAt, zone)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                "${FocusFormat.remaining(endsAt - status.now)} left",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (status.bankedMillis > 0) {
                Text(
                    "Free time paused: ${FocusFormat.remaining(status.bankedMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/** Dashboard card for starting a timed session or detox day when no mode is active. */
@Composable
fun StartFocusCard(onTimed: () -> Unit, onDetox: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Start focus", style = MaterialTheme.typography.titleMedium)
            Text(
                "Lock distracting apps for a set time. You can't end it early.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTimed) { Text("Timed session") }
                OutlinedButton(onClick = onDetox) { Text("Detox day") }
            }
        }
    }
}
```

- [ ] **Step 5: `StartFocusDialog.kt`**

```kotlin
package app.lenga.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.lenga.data.apps.LaunchableApp
import app.lenga.focus.FocusFormat
import app.lenga.focus.FocusKind
import app.lenga.focus.FocusMode
import app.lenga.focus.ModeSchedule
import app.lenga.ui.theme.AccentRed
import java.time.LocalDate
import java.time.ZoneId

private const val CUSTOM_LIST = "custom"

/**
 * Sets up a timed session or detox day. [onStart] receives the end time, the allowed apps,
 * and a callback for showing an error in the dialog.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartFocusDialog(
    kind: FocusKind,
    savedModes: List<FocusMode>,
    apps: List<LaunchableApp>?,
    essentials: Set<String>,
    onDismiss: () -> Unit,
    onStart: (end: Long, allowed: Set<String>, onError: (String) -> Unit) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    var minutes by remember { mutableIntStateOf(60) }
    var customText by remember { mutableStateOf("") }
    var detoxLastDay by remember { mutableStateOf(LocalDate.now(zone)) }
    var allowed by remember { mutableStateOf(emptySet<String>()) }
    // Null = essentials only, a mode id = copied from that mode, CUSTOM_LIST = edited by hand
    var listSource by remember { mutableStateOf<String?>(null) }
    var pickingApps by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val listModes = savedModes.filter { it.schedule is ModeSchedule.Weekly || it.schedule is ModeSchedule.OneOff }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == FocusKind.TIMED) "Timed session" else "Detox day") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (kind == FocusKind.TIMED) {
                    Text("Length", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 120).forEach { preset ->
                            FilterChip(
                                selected = customText.isEmpty() && minutes == preset,
                                onClick = { minutes = preset; customText = "" },
                                label = { Text(FocusFormat.remaining(preset * 60_000L)) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { text ->
                            customText = text.filter(Char::isDigit).take(4)
                            customText.toIntOrNull()?.let { minutes = it }
                        },
                        label = { Text("Custom length (minutes)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                } else {
                    Text("Until the end of ${FocusFormat.date(detoxLastDay)}")
                    TextButton(onClick = { pickingDate = true }) { Text("Change date") }
                }

                Text("Allowed apps", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = listSource == null,
                        onClick = { listSource = null; allowed = emptySet() },
                        label = { Text("Essentials only") }
                    )
                    listModes.forEach { mode ->
                        FilterChip(
                            selected = listSource == mode.id,
                            onClick = { listSource = mode.id; allowed = mode.allowedPackages },
                            label = { Text(mode.name) }
                        )
                    }
                }
                TextButton(onClick = { pickingApps = true }) { Text("Edit apps (${allowed.size})") }

                Text(
                    "You can't end this early. Emergency unlock (2 a day) still works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentRed
                )
                error?.let { Text(it, color = AccentRed, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (kind == FocusKind.TIMED && minutes <= 0) {
                    error = "Enter a length in minutes."
                    return@Button
                }
                val end = if (kind == FocusKind.TIMED) {
                    System.currentTimeMillis() + minutes * 60_000L
                } else {
                    detoxLastDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                }
                onStart(end, allowed) { error = it }
            }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (pickingApps) {
        AppPickerDialog(
            apps = apps,
            essentials = essentials,
            initialSelection = allowed,
            onDismiss = { pickingApps = false },
            onConfirm = {
                allowed = it
                listSource = CUSTOM_LIST
                pickingApps = false
            }
        )
    }
    if (pickingDate) {
        DateDialog(
            initial = detoxLastDay,
            onDismiss = { pickingDate = false },
            onConfirm = {
                detoxLastDay = maxOf(it, LocalDate.now(zone))
                pickingDate = false
            }
        )
    }
}
```

- [ ] **Step 6: Build**

Run: `./gradlew :app:assembleDebug -q`
Expected: exit 0.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/lenga/ui/components
git commit -m "Add focus mode dialogs, cards and app picker" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Screens, dashboard and navigation

**Files:**
- Create: `app/src/main/java/app/lenga/ui/screens/ModesScreen.kt`
- Create: `app/src/main/java/app/lenga/ui/screens/EditModeScreen.kt`
- Modify: `app/src/main/java/app/lenga/ui/screens/StudyDashboardScreen.kt`
- Modify: `app/src/main/java/app/lenga/ui/navigation/StudyNavHost.kt`

- [ ] **Step 1: `ModesScreen.kt`**

```kotlin
package app.lenga.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lenga.focus.ActiveMode
import app.lenga.focus.FocusFormat
import app.lenga.focus.FocusMode
import app.lenga.focus.FocusPolicy
import app.lenga.focus.ModeSchedule
import app.lenga.ui.theme.AccentRed
import app.lenga.ui.theme.TextSecondary
import app.lenga.viewmodel.StudyViewModel
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModesScreen(
    viewModel: StudyViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit
) {
    val load by viewModel.modesLoad.collectAsStateWithLifecycle()
    val status by viewModel.focusStatus.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val now = status?.now ?: System.currentTimeMillis()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Add mode") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (load?.corrupt == true) {
                item { Text("Saved modes couldn't be read and were reset.", color = AccentRed) }
            }
            val modes = load?.modes.orEmpty()
            if (load != null && modes.isEmpty()) {
                item {
                    Text("No modes yet. Add a weekly schedule or a one-off block.", color = TextSecondary)
                }
            }
            items(modes, key = { it.id }) { mode ->
                val active = status?.active?.find { it.mode.id == mode.id }
                val editable = active == null &&
                    (mode.schedule is ModeSchedule.Weekly || mode.schedule is ModeSchedule.OneOff)
                Card(modifier = Modifier.fillMaxWidth().clickable(enabled = editable) { onEdit(mode.id) }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                FocusFormat.schedule(mode.schedule, zone),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(statusLine(mode, active, now, zone), style = MaterialTheme.typography.bodySmall)
                        }
                        if (active != null) Icon(Icons.Default.Lock, contentDescription = "Running")
                    }
                }
            }
        }
    }
}

private fun statusLine(mode: FocusMode, active: ActiveMode?, now: Long, zone: ZoneId): String = when {
    active != null -> "Running until ${FocusFormat.clock(active.end, zone)}"
    else -> FocusPolicy.nextStart(mode.schedule, now, zone)
        ?.let { "Next: ${FocusFormat.dayTime(it, zone)}" }
        ?: "Ended"
}
```

- [ ] **Step 2: `EditModeScreen.kt`**

```kotlin
package app.lenga.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lenga.focus.FocusFormat
import app.lenga.focus.FocusMode
import app.lenga.focus.ModeSchedule
import app.lenga.ui.components.AppPickerDialog
import app.lenga.ui.components.DateDialog
import app.lenga.ui.components.TimeDialog
import app.lenga.ui.components.rememberNotificationPermissionRequest
import app.lenga.ui.theme.AccentRed
import app.lenga.ui.theme.TextSecondary
import app.lenga.viewmodel.StudyViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private enum class EditDialog { FROM, TO, START_DATE, START_TIME, END_DATE, END_TIME, APPS }

/** Creates a weekly or one-off mode, or edits one when [modeId] is set. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditModeScreen(modeId: String?, viewModel: StudyViewModel, onDone: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val existing = remember(modeId) { modeId?.let { viewModel.mode(it) } }
    val apps by viewModel.launchableApps.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadLaunchableApps() }
    val requestNotifications = rememberNotificationPermissionRequest()

    val initialWeekly = existing?.schedule as? ModeSchedule.Weekly
    val initialOneOff = existing?.schedule as? ModeSchedule.OneOff
    val defaultStart = remember { LocalDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0) }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var weekly by remember { mutableStateOf(initialOneOff == null) }
    var days by remember { mutableStateOf(initialWeekly?.days ?: setOf(1, 2, 3, 4, 5)) }
    var fromMinute by remember { mutableIntStateOf(initialWeekly?.fromMinute ?: (8 * 60)) }
    var toMinute by remember { mutableIntStateOf(initialWeekly?.toMinute ?: (16 * 60)) }
    var start by remember { mutableStateOf(initialOneOff?.start?.let { toLocal(it, zone) } ?: defaultStart) }
    var end by remember { mutableStateOf(initialOneOff?.end?.let { toLocal(it, zone) } ?: defaultStart.plusHours(2)) }
    var allowed by remember { mutableStateOf(existing?.allowedPackages ?: emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<EditDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New mode" else "Edit mode") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Weekly") })
                FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("One-off") })
            }
            if (weekly) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.values().forEach { day ->
                        FilterChip(
                            selected = day.value in days,
                            onClick = { days = if (day.value in days) days - day.value else days + day.value },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { dialog = EditDialog.FROM }) { Text("From ${FocusFormat.minuteOfDay(fromMinute)}") }
                    OutlinedButton(onClick = { dialog = EditDialog.TO }) { Text("To ${FocusFormat.minuteOfDay(toMinute)}") }
                }
                if (toMinute <= fromMinute) {
                    Text("Ends the next day", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            } else {
                DateTimeRow("Start", start, onDate = { dialog = EditDialog.START_DATE }, onTime = { dialog = EditDialog.START_TIME })
                DateTimeRow("End", end, onDate = { dialog = EditDialog.END_DATE }, onTime = { dialog = EditDialog.END_TIME })
            }
            OutlinedButton(onClick = { dialog = EditDialog.APPS }) { Text("Allowed apps (${allowed.size})") }
            error?.let { Text(it, color = AccentRed) }
            Button(
                onClick = {
                    val schedule = if (weekly) {
                        ModeSchedule.Weekly(days, fromMinute, toMinute)
                    } else {
                        ModeSchedule.OneOff(
                            start.atZone(zone).toInstant().toEpochMilli(),
                            end.atZone(zone).toInstant().toEpochMilli()
                        )
                    }
                    val mode = FocusMode(existing?.id ?: UUID.randomUUID().toString(), name.trim(), schedule, allowed)
                    viewModel.saveMode(mode) { saveError ->
                        if (saveError == null) {
                            requestNotifications()
                            onDone()
                        } else {
                            error = saveError
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
            if (existing != null) {
                OutlinedButton(
                    onClick = {
                        viewModel.deleteMode(existing.id) { deleteError ->
                            if (deleteError == null) onDone() else error = deleteError
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
                ) { Text("Delete") }
            }
        }
    }

    when (dialog) {
        EditDialog.FROM -> TimeDialog(fromMinute, onDismiss = { dialog = null }) { fromMinute = it; dialog = null }
        EditDialog.TO -> TimeDialog(toMinute, onDismiss = { dialog = null }) { toMinute = it; dialog = null }
        EditDialog.START_DATE -> DateDialog(start.toLocalDate(), onDismiss = { dialog = null }) {
            start = it.atTime(start.toLocalTime()); dialog = null
        }
        EditDialog.START_TIME -> TimeDialog(start.hour * 60 + start.minute, onDismiss = { dialog = null }) {
            start = start.toLocalDate().atTime(it / 60, it % 60); dialog = null
        }
        EditDialog.END_DATE -> DateDialog(end.toLocalDate(), onDismiss = { dialog = null }) {
            end = it.atTime(end.toLocalTime()); dialog = null
        }
        EditDialog.END_TIME -> TimeDialog(end.hour * 60 + end.minute, onDismiss = { dialog = null }) {
            end = end.toLocalDate().atTime(it / 60, it % 60); dialog = null
        }
        EditDialog.APPS -> AppPickerDialog(
            apps = apps,
            essentials = viewModel.essentialPackages,
            initialSelection = allowed,
            onDismiss = { dialog = null },
            onConfirm = { allowed = it; dialog = null }
        )
        null -> Unit
    }
}

@Composable
private fun DateTimeRow(label: String, value: LocalDateTime, onDate: () -> Unit, onTime: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(48.dp))
        OutlinedButton(onClick = onDate) { Text(FocusFormat.date(value.toLocalDate())) }
        OutlinedButton(onClick = onTime) { Text(FocusFormat.minuteOfDay(value.hour * 60 + value.minute)) }
    }
}

private fun toLocal(epochMillis: Long, zone: ZoneId): LocalDateTime =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
```

- [ ] **Step 3: Update the dashboard** (`StudyDashboardScreen.kt`).

3a. Add these imports after `import app.lenga.data.model.StudyModule`:

```kotlin
import app.lenga.focus.FocusKind
```

(`ui.components.*`, `androidx.compose.runtime.*` and `material.icons.filled.*` are already imported.)

3b. Add a parameter. Replace

```kotlin
    onEmergencyClick: () -> Unit
) {
```

with

```kotlin
    onEmergencyClick: () -> Unit,
    onModesClick: () -> Unit
) {
```

3c. After the `LifecycleResumeEffect(Unit) { ... }` block near the top of the function, add:

```kotlin
    val focusStatus by viewModel.focusStatus.collectAsStateWithLifecycle()
    val modesLoad by viewModel.modesLoad.collectAsStateWithLifecycle()
    val launchableApps by viewModel.launchableApps.collectAsStateWithLifecycle()
    var startKind by remember { mutableStateOf<FocusKind?>(null) }
    val requestNotifications = rememberNotificationPermissionRequest()
```

3d. In the top bar `actions = { ... }`, add this before the emergency `IconButton`:

```kotlin
                    IconButton(onClick = onModesClick) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "Modes",
                            tint = Color.White
                        )
                    }
```

3e. In the `LazyColumn`, directly after the `if (!isBlockerRunning) { item { EnableBlockingCard(...) } }` block, add:

```kotlin
            focusStatus?.let { status ->
                item {
                    if (status.isActive) {
                        FocusBanner(status)
                    } else {
                        StartFocusCard(
                            onTimed = { startKind = FocusKind.TIMED },
                            onDetox = { startKind = FocusKind.DETOX }
                        )
                    }
                }
            }
```

3f. At the end of `StudyDashboardScreen`, after the closing `}` of `Scaffold(...) { padding -> ... }` and before the function's closing `}`, add:

```kotlin
    startKind?.let { kind ->
        LaunchedEffect(kind) { viewModel.loadLaunchableApps() }
        StartFocusDialog(
            kind = kind,
            savedModes = modesLoad?.modes.orEmpty(),
            apps = launchableApps,
            essentials = viewModel.essentialPackages,
            onDismiss = { startKind = null },
            onStart = { end, allowed, onError ->
                viewModel.startFocus(kind, end, allowed) { error ->
                    if (error == null) {
                        startKind = null
                        requestNotifications()
                    } else {
                        onError(error)
                    }
                }
            }
        )
    }
```

- [ ] **Step 4: Update navigation** (`StudyNavHost.kt`).

4a. Add routes inside `sealed class Screen`, after `object EmergencyUnlock : Screen("emergency_unlock")`:

```kotlin
    object Modes : Screen("modes")
    object EditMode : Screen("mode_edit/{modeId}") {
        const val NEW = "new"
        fun createRoute(modeId: String?) = "mode_edit/${modeId ?: NEW}"
    }
```

4b. In the dashboard `composable`, add the new argument after `onEmergencyClick = { ... }`:

```kotlin
                onEmergencyClick = {
                    navController.navigate(Screen.EmergencyUnlock.route)
                },
                onModesClick = {
                    navController.navigate(Screen.Modes.route)
                }
```

(Replace the existing `onEmergencyClick = { ... }` block with the two blocks above.)

4c. After a passed quiz during a focus mode, the reward time is saved rather than started, so go back to the dashboard (whose banner shows "Free time paused") instead of the reward countdown. In the Quiz `composable`, replace

```kotlin
                    if (passed) {
                        navController.navigate(Screen.Reward.route) {
                            popUpTo(Screen.StudyDashboard.route)
                        }
                    } else {
```

with

```kotlin
                    if (passed && viewModel.focusStatus.value?.isActive == true) {
                        navController.popBackStack(Screen.StudyDashboard.route, inclusive = false)
                    } else if (passed) {
                        navController.navigate(Screen.Reward.route) {
                            popUpTo(Screen.StudyDashboard.route)
                        }
                    } else {
```

4d. Add the two destinations before the final `}` of the `NavHost { ... }` block:

```kotlin
        composable(Screen.Modes.route) {
            ModesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Screen.EditMode.createRoute(null)) },
                onEdit = { id -> navController.navigate(Screen.EditMode.createRoute(id)) }
            )
        }

        composable(Screen.EditMode.route) { backStackEntry ->
            val modeId = backStackEntry.arguments?.getString("modeId")?.takeIf { it != Screen.EditMode.NEW }
            EditModeScreen(
                modeId = modeId,
                viewModel = viewModel,
                onDone = { navController.popBackStack() }
            )
        }
```

- [ ] **Step 5: Build and run all unit tests**

Run: `./gradlew :app:testDebugUnitTest -q && ./gradlew :app:assembleDebug -q`
Expected: both exit 0.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/lenga/ui
git commit -m "Add modes screens and focus controls to the dashboard" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: On-device verification (Xiaomi test phone)

No code changes. The phone must be connected over adb. `ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`.

- [ ] **Step 1: Install and confirm the service is bound**

```bash
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" shell dumpsys accessibility | grep -E "Enabled services|Bound services|Crashed"
```

Expected: Lenga is listed under Bound services. If it's Crashed or missing, ask the user to switch it off and on in Accessibility settings, and to lock Lenga in Recents.

- [ ] **Step 2: Timed session.** Ask the user to:
  - Open Modes and create a weekly mode "Test list" for today that allows WhatsApp. Save it and allow notifications when asked.
  - On the dashboard, start a timed session with a custom length of 3 minutes, using "Test list".

  Then check, taking screenshots with `"$ADB" exec-out screencap -p > "$TEMP/shot.png"` and reading them:
  - The dashboard banner shows "Focus session", the end time and "3m left".
  - The notification shade shows "Focus mode on".
  - WhatsApp opens. Instagram (or another app that isn't listed) bounces back with "Blocked by Focus session until HH:mm".
  - Settings bounces back with "Settings are locked during focus sessions".
  - Emergency unlock (PIN) opens Instagram and Settings.
  - After 3 minutes, the banner is replaced by the Start focus card, the notification disappears, and Instagram is blocked with the quiz message again.

- [ ] **Step 3: Weekly auto-start.** Ask the user to edit "Test list" (or create a new mode) so it starts 2 minutes from now for today's weekday only, with no apps allowed. Wait 2 minutes and check that Instagram is blocked with "Blocked by <name> until …" and that the Modes screen shows the lock icon and won't open the mode for editing.

- [ ] **Step 4: Free time pauses.** With no mode running, pass a quiz to start free time, then start a 2-minute timed session. Check that the banner shows "Free time paused: …" and that after the session ends, Instagram opens because free time has resumed.

- [ ] **Step 5: Reboot during a mode.** Start a 10-minute timed session, then `"$ADB" reboot`, and ask the user to unlock the phone. Check that Instagram is blocked again and that `dumpsys accessibility` shows the service bound.

- [ ] **Step 6: Report.** Write down which checks passed and failed, with screenshots or logcat lines for any failure. Fix failures using superpowers:systematic-debugging before claiming the feature is done.

---

## Self-review against the spec

- Four mode types → Tasks 1, 2 (windows), 11 (timed/detox dialog), 12 (weekly/one-off editor).
- Per-mode allowed apps → Tasks 1, 3, 8 (picker data), 11 (picker UI).
- An app must be allowed by every active mode; blocking lasts until the last one ends → Task 3 tests.
- No early exit, but the emergency unlock works → Tasks 3, 5 (separate `emergencyUntil`) and 11 (dialog warning).
- No editing a running mode → Tasks 4, 7 (repository), 12 (list disables editing).
- Settings lock → Task 3 (`SETTINGS_PACKAGES`), Task 8 (Settings removed from essentials), Task 9 (toast).
- Pausing free time, including late detection and idempotency → Tasks 5, 9 (service sync), 10 (view model sync, quiz rewards), 12 (quiz navigation).
- No alarms; evaluation on window change → Task 9.
- Ongoing notification and permission → Tasks 8, 9, 11, 12.
- Corrupt JSON → Task 1 (codec), Task 12 (Modes screen message).
- Expired timed and detox cleanup → Task 2 (`isDisposable`), Task 7.
- Daylight saving and midnight crossover → Task 2 tests.
- Manual test plan → Task 13.
- Differences from the spec: the app picker is a full-screen dialog rather than an `app_picker` route; `createdAt` is dropped because nothing uses it. The spec has been updated to match.
