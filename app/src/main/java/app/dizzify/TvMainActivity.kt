package app.dizzify

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.dizzify.data.WidgetConstants
import app.dizzify.helper.PrivateSpaceReceiver
import app.dizzify.settings.ThemeMode
import app.dizzify.ui.LauncherShell
import app.dizzify.ui.components.LauncherWidgetHost
import app.dizzify.ui.components.snackbar.SnackbarManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private var privateSpaceReceiver: PrivateSpaceReceiver? = null
    private val launcherViewModel: LauncherViewModel by viewModel()
    private val launcherWidgetHost: LauncherWidgetHost by inject()
    private val snackbarManager: SnackbarManager by inject()
    private val permissionManager: app.dizzify.helper.PermissionManager by inject()

    companion object {
        const val REQUEST_CONFIGURE_WIDGET = WidgetConstants.REQUEST_CONFIGURE_WIDGET
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupFullscreen()

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

        // Apply night mode from settings (ported from CCLauncher).
        lifecycleScope.launch {
            launcherViewModel.settings.map { it.theme }.distinctUntilChanged().collect { theme ->
                AppCompatDelegate.setDefaultNightMode(
                    when (theme) {
                        ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                        ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
            }
        }

        // First run: record timestamp, prompt for default launcher (ported from CCLauncher).
        lifecycleScope.launch {
            val settings = launcherViewModel.settings.first()
            if (settings.firstOpen) {
                launcherViewModel.setFirstOpen(false)
                if (!permissionManager.isDefaultLauncher()) {
                    snackbarManager.show(
                        message = "Set Dizzify as your default launcher",
                        actionLabel = "Settings",
                        withDismissAction = true,
                        onAction = {
                            runCatching {
                                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                            }
                        }
                    )
                }
            }
        }

        setContent {
            LauncherShell(viewModel = launcherViewModel)
        }

        lifecycleScope.launch {
            runCatching { launcherViewModel.refreshApps() }
                .onFailure { e -> Log.e("MainActivity", "Initial refresh failed", e) }
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            launcherWidgetHost.startListening()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting widget host listening", e)
        }
        // Refresh on return (e.g. back from Play Store after install/uninstall).
        lifecycleScope.launch {
            runCatching { launcherViewModel.refreshApps() }
                .onFailure { e -> Log.e("MainActivity", "Error refreshing apps", e) }
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            launcherWidgetHost.stopListening()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping widget host listening", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        lifecycleScope.launch {
            val theme = launcherViewModel.settings.first().theme
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
        setupFullscreen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // HOME press while inside Apps/Settings/Hidden returns to the default screen.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            launcherViewModel.emitEvent(app.dizzify.ui.LauncherEvent.NavigateHome)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupFullscreen()
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
            // The VM resolves the widget ID via pending state when data has none.
            launcherViewModel.handleActivityResult(requestCode, resultCode, data)
        }
    }

    private fun setupFullscreen() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "setupFullscreen failed", e)
        }
    }
}
