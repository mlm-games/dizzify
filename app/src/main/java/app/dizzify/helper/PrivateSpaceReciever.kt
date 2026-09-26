package app.dizzify.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import app.dizzify.data.Constants

/**
 * Refreshes the app list when a secondary profile (Private Space, or a work profile) becomes
 * available or goes away, so apps hidden behind quiet mode appear and disappear correctly.
 */
class PrivateSpaceReceiver : BroadcastReceiver() {
    private val tag = "PrivateSpaceReceiver"

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_PROFILE_AVAILABLE &&
            action != Intent.ACTION_PROFILE_UNAVAILABLE
        ) return

        // Work profiles raise the same broadcasts; refreshing for those is correct, but the
        // profile the broadcast refers to must exist for the reload to mean anything.
        val user: UserHandle? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_USER)
        }
        Log.d(tag, "Profile ${action.substringAfterLast('_')} for user=$user")

        // Scoped to our own package so no other app can trigger reload loops.
        context.sendBroadcast(
            Intent(Constants.ACTION_REFRESH_APPS).setPackage(context.packageName)
        )
    }
}
