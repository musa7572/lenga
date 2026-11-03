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
