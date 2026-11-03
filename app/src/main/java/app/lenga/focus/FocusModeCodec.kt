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
