package app.lenga.data.apps

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class LaunchableApp(val packageName: String, val label: String, val icon: ImageBitmap)

/** Apps with a launcher icon, excluding Lenga. Loading icons is slow: call off the main thread. */
object LaunchableApps {
    private const val ICON_SIZE_PX = 96

    fun load(context: Context): List<LaunchableApp> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                LaunchableApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(packageManager).toString(),
                    icon = it.loadIcon(packageManager).toBitmap(ICON_SIZE_PX, ICON_SIZE_PX).asImageBitmap()
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
