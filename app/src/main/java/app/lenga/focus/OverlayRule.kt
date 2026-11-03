package app.lenga.focus

/** What to do with the blocking overlay when a window comes to the foreground. */
enum class OverlayAction { SHOW, HIDE, KEEP }

object OverlayRule {

    /**
     * [pkg] is the app whose window just appeared and [decision] is FocusPolicy's answer for it.
     * [passThrough] are windows that open over the current app without replacing it (status bar,
     * notification shade, system dialogs, keyboards) plus Lenga itself. They never change the
     * overlay: the blocked app is still underneath, and closing them fires no event for it.
     */
    fun onWindow(pkg: String, decision: Decision, passThrough: Set<String>, showing: Boolean): OverlayAction = when {
        pkg in passThrough -> OverlayAction.KEEP
        decision != Decision.Allow -> OverlayAction.SHOW
        showing -> OverlayAction.HIDE
        else -> OverlayAction.KEEP
    }
}
