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
