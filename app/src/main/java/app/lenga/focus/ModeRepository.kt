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
