package app.dizzify.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object SettingsShortcuts {

    fun openWifi(context: Context) {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                add(Intent(Settings.Panel.ACTION_WIFI))
            }
            add(Intent(Settings.ACTION_WIFI_SETTINGS))
            add(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            add(Intent(Settings.ACTION_SETTINGS))
        }

        context.startFirstResolvable(intents)
    }

    fun openBluetooth(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )

        context.startFirstResolvable(intents)
    }

    fun openDeviceSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_SETTINGS),
        )
        context.startFirstResolvable(intents)
    }

    private fun Context.startFirstResolvable(intents: List<Intent>) {
        val pm = packageManager
        for (i in intents) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(pm) != null) {
                startActivity(i)
                return
            }
        }
        // TODO: show a snackbar message if couldn't handle.
    }
}