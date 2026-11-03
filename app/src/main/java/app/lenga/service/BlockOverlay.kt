package app.lenga.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import app.lenga.data.repository.EmergencyUnlockResult
import app.lenga.data.repository.EmergencyUnlocker
import app.lenga.focus.BlockText
import app.lenga.ui.components.BlockScreen
import app.lenga.ui.theme.LengaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Full-screen "Stay focused" window drawn by the accessibility service over a blocked app.
 * Accessibility overlays need no extra permission and aren't affected by OEM limits on
 * opening activities from the background.
 */
class BlockOverlay(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val unlocker: EmergencyUnlocker
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayLifecycleOwner().apply { start() }
    private var view: ComposeView? = null
    private var text by mutableStateOf<BlockText?>(null)
    private var pinSet by mutableStateOf<Boolean?>(null)
    private var unlocksLeft by mutableStateOf<Int?>(null)

    /** Package of the app under the overlay; null when hidden. */
    var coveredPackage: String? = null
        private set

    val isShowing: Boolean get() = view != null

    /** Shows [newText] over [pkg], or updates it if already showing. Returns false if the window couldn't be added. */
    fun show(pkg: String, newText: BlockText): Boolean {
        if (pkg != coveredPackage) refreshEmergencyInfo()
        text = newText
        coveredPackage = pkg
        if (view != null) return true
        val composeView = ComposeView(service).apply {
            owner.attach(this)
            setContent {
                LengaTheme {
                    text?.let {
                        BlockScreen(
                            text = it,
                            pinSet = pinSet,
                            unlocksLeft = unlocksLeft,
                            onClose = ::close,
                            onOpenLenga = ::openLenga,
                            onUnlock = ::unlock
                        )
                    }
                }
            }
        }
        return try {
            windowManager.addView(composeView, layoutParams())
            view = composeView
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't add the blocking overlay", e)
            coveredPackage = null
            false
        }
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        coveredPackage = null
    }

    fun destroy() {
        hide()
        owner.destroy()
    }

    private fun close() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        hide()
    }

    /**
     * Goes HOME, then opens Lenga once the launcher is up (HOME is asynchronous and would
     * otherwise land on top of Lenga). Where the phone refuses the launch, the user stays home.
     */
    private fun openLenga() {
        close()
        scope.launch {
            delay(OPEN_LENGA_DELAY_MS)
            service.packageManager.getLaunchIntentForPackage(service.packageName)?.let {
                service.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun unlock(pin: String, onResult: (EmergencyUnlockResult) -> Unit) {
        scope.launch {
            val result = unlocker.unlock(pin)
            unlocksLeft = unlocker.unlocksLeftToday.first()
            onResult(result)
            if (result == EmergencyUnlockResult.UNLOCKED) hide()
        }
    }

    /** Re-reads PIN state and today's unlocks, so a new day or a new PIN shows up. */
    private fun refreshEmergencyInfo() {
        scope.launch {
            pinSet = unlocker.isPinSet.first()
            unlocksLeft = unlocker.unlocksLeftToday.first()
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.OPAQUE
    )

    private companion object {
        const val TAG = "BlockOverlay"
        const val OPEN_LENGA_DELAY_MS = 300L
    }
}
