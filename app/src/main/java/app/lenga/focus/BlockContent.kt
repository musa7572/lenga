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
