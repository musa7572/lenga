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
        allowed.addAll(systemOverlays(context))
        val packageManager = context.packageManager

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapTo(allowed) { it.activityInfo.packageName }

        (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
            ?.defaultDialerPackage
            ?.let { allowed.add(it) }

        Telephony.Sms.getDefaultSmsPackage(context)?.let { allowed.add(it) }

        return allowed
    }

    /**
     * Windows that open on top of the current app without replacing it: system dialogs, the
     * status bar and notification shade, and keyboards.
     */
    fun systemOverlays(context: Context): Set<String> {
        val packages = mutableSetOf("android", "com.android.systemui")
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.enabledInputMethodList
            ?.mapTo(packages) { it.packageName }
        return packages
    }
}
