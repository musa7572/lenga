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
