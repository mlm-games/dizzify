package app.dizzify

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.dizzify.helper.PrivateSpaceReceiver
import app.dizzify.ui.LauncherShell
import app.dizzify.ui.components.LauncherWidgetHost
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private var privateSpaceReceiver: PrivateSpaceReceiver? = null
    private val launcherViewModel: LauncherViewModel by viewModel()
    private val launcherWidgetHost: LauncherWidgetHost by inject()

    companion object {
        const val REQUEST_CONFIGURE_WIDGET = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Previously the PrivateSpaceReceiver broadcast (app.dizzify.ACTION_REFRESH_APPS)
        // was a dead letter — nothing registered for it. Wire it up (not exported).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            privateSpaceReceiver = PrivateSpaceReceiver()
            val intentFilter = IntentFilter().apply {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(
                        privateSpaceReceiver,
                        intentFilter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    registerReceiver(privateSpaceReceiver, intentFilter)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to register PrivateSpaceReceiver", e)
            }
        }

        setContent {
            val vm: LauncherViewModel = koinViewModel()
            LauncherShell(viewModel = vm)
        }
    }

    override fun onDestroy() {
        privateSpaceReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to unregister PrivateSpaceReceiver", e)
            }
            privateSpaceReceiver = null
        }
        super.onDestroy()
    }

    /**
     * Results from LauncherWidgetHost.startWidgetConfiguration() arrive here
     * (AppWidgetHost uses the legacy startActivityForResult internally).
     */
    @Deprecated("Required for AppWidgetHost configuration results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            val widgetId = data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            if (resultCode == RESULT_OK) {
                Log.d("MainActivity", "Widget $widgetId configured successfully")
            } else {
                Log.w("MainActivity", "Widget configuration cancelled for $widgetId")
                launcherWidgetHost.deleteWidgetId(widgetId)
                lifecycleScope.launch {
                    runCatching { launcherViewModel.removeWidget(widgetId) }
                }
            }
        }
    }


    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

//    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
//        // Prevent back from exiting launcher (it's a home screen)
//        if (keyCode == KeyEvent.KEYCODE_BACK) {
//            return true
//        }
//        return super.onKeyDown(keyCode, event)
//    }
//
//    override fun onBackPressed() {
//        // Do nothing - launcher shouldn't exit on back
//    }
//
}
