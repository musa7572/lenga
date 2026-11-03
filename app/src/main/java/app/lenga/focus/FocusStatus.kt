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
