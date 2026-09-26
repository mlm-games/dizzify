package app.dizzify.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import app.dizzify.LauncherViewModel
import app.dizzify.settings.DefaultScreen
import app.dizzify.ui.components.SidebarDestination
import app.dizzify.ui.components.LauncherSidebar
import app.dizzify.ui.screens.AppsScreen
import app.dizzify.ui.screens.GamesScreen
import app.dizzify.ui.screens.HiddenAppsScreen
import app.dizzify.ui.screens.HomeScreen
import app.dizzify.ui.screens.SettingsScreen
import app.dizzify.ui.theme.LauncherTheme
import androidx.navigation3.runtime.NavKey
import app.dizzify.ui.components.LauncherWidgetHost
import app.dizzify.ui.components.SidebarDestinations
import app.dizzify.ui.components.snackbar.LauncherSnackbarHost
import app.dizzify.ui.components.snackbar.SnackbarManager
import app.dizzify.ui.screens.WidgetPickerScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import app.dizzify.MainActivity


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun LauncherShell(
    viewModel: LauncherViewModel
) {
    val settings by viewModel.settings.collectAsState()
    LauncherTheme(
        theme = settings.theme,
        textSizeScale = settings.textSizeScale,
        fontWeight = settings.fontWeight,
        customFontPath = settings.customFontPath,
        useSystemFont = settings.useSystemFont
    ) {
        val backStack = rememberLauncherBackStack(
            if (settings.defaultScreen == DefaultScreen.Apps) LauncherKey.Apps else LauncherKey.Home
        )

        val snackbarHostState = remember { SnackbarHostState() }
        val snackbarManager: SnackbarManager = koinInject()
        val widgetHost: LauncherWidgetHost = koinInject()
        val context = LocalContext.current

        // Bind-permission round-trip for widgets (ported from CCLauncher navigation).
        val bindLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            viewModel.handleActivityResult(
                MainActivity.REQUEST_CONFIGURE_WIDGET,
                result.resultCode,
                result.data
            )
        }

        // Keyed on the view model only: keying on a settings value tears the collector down
        // and re-subscribes, and a replay=0 SharedFlow drops anything emitted in that window.
        val currentDefaultScreen by rememberUpdatedState(settings.defaultScreen)
        val currentActivity by rememberUpdatedState(context)

        // NavDisplay throws on an empty back stack, and both back handlers below can fire
        // against a stale frame, so every pop goes through here.
        val pop = { if (backStack.size > 1) backStack.removeLast() }
        val resetTo = { key: LauncherKey ->
            backStack.clear()
            backStack.add(key)
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is LauncherEvent.LaunchWidgetBindIntent -> {
                        runCatching { bindLauncher.launch(event.intent) }
                            .onFailure { e ->
                                Log.e("LauncherShell", "Failed to launch widget bind", e)
                                snackbarManager.show("Failed to request widget permission.")
                            }
                    }
                    is LauncherEvent.ConfigureWidget -> {
                        val activity = currentActivity as? Activity
                        if (activity == null) {
                            snackbarManager.show("Widget settings are unavailable right now.")
                        } else if (!widgetHost.startWidgetConfiguration(
                                activity,
                                event.widgetId,
                                MainActivity.REQUEST_CONFIGURE_WIDGET
                            )
                        ) {
                            // startWidgetConfiguration swallows its own failures and returns
                            // false, so nothing else would report them.
                            snackbarManager.show("Couldn't open widget settings.")
                        }
                    }
                    LauncherEvent.NavigateHome -> {
                        val home: LauncherKey =
                            if (currentDefaultScreen == DefaultScreen.Apps) LauncherKey.Apps else LauncherKey.Home
                        resetTo(home)
                    }
                }
            }
        }

        val current = backStack.last()

        val currentDestination = remember(current) {
            when (current) {
                LauncherKey.Home -> SidebarDestination.Home
                LauncherKey.Apps -> SidebarDestination.Apps
                LauncherKey.Games -> SidebarDestination.Games
                LauncherKey.Hidden -> SidebarDestination.Hidden
                LauncherKey.Settings -> SidebarDestination.Settings
                LauncherKey.WidgetPicker -> SidebarDestination.Home
            }
        }

        Scaffold(
            snackbarHost = {
                LauncherSnackbarHost(
                    hostState = snackbarHostState,
                    manager = snackbarManager
                )
            }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                LauncherSidebar(
                    currentDestination = currentDestination,
                    onDestinationSelected = { dest ->
                        val key = when (dest) {
                            SidebarDestination.Home -> LauncherKey.Home
                            SidebarDestination.Apps -> LauncherKey.Apps
                            SidebarDestination.Games -> LauncherKey.Games
                            SidebarDestination.Hidden -> LauncherKey.Hidden
                            SidebarDestination.Settings -> LauncherKey.Settings
                        }

                        resetTo(key)
                    },
                    modifier = Modifier.fillMaxHeight()
                )

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val viewModelStoreOwner =
                        androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!

                    NavDisplay(
                        backStack = backStack,
                        onBack = { pop() },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(
                                viewModelStoreOwner = viewModelStoreOwner,
                                removeViewModelStoreOnPop = { true }
                            )
                        ),
                        entryProvider = entryProvider {
                            entry<LauncherKey.WidgetPicker> {
                                WidgetPickerScreen(
                                    onWidgetSelected = { providerInfo ->
                                        viewModel.startWidgetConfiguration(providerInfo)
                                        pop()
                                    },
                                    onDismiss = { pop() }
                                )
                            }

                            entry<LauncherKey.Home> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToApps = { resetTo(LauncherKey.Apps) },
                                    onNavigateToWidgetPicker = {
                                        backStack.add(LauncherKey.WidgetPicker)
                                    }
                                )
                            }

                            entry<LauncherKey.Apps> {
                                AppsScreen(viewModel = viewModel)
                            }

                            entry<LauncherKey.Games> {
                                GamesScreen(viewModel = viewModel)
                            }

                            entry<LauncherKey.Hidden> {
                                HiddenAppsScreen(viewModel = viewModel)
                            }

                            entry<LauncherKey.Settings> {
                                SettingsScreen(viewModel = viewModel)
                            }
                        }
                    )
                }
            }
        }

        BackHandler(enabled = backStack.size > 1) { pop() }
    }
}

@Serializable
sealed interface LauncherKey : NavKey {

    @Serializable
    data object Home : LauncherKey

    @Serializable
    data object Apps : LauncherKey

    @Serializable
    data object Games : LauncherKey

    @Serializable
    data object Hidden : LauncherKey

    @Serializable
    data object WidgetPicker : LauncherKey

    @Serializable
    data object Settings : LauncherKey
}

/**
 * Back stack that survives configuration changes and process death. A plain `remember` loses
 * navigation on every Activity recreation (locale, theme, low-memory restore).
 */
@Composable
fun rememberLauncherBackStack(initial: LauncherKey = LauncherKey.Home): NavBackStack<LauncherKey> =
    @Suppress("UNCHECKED_CAST")
    rememberNavBackStack(initial) as NavBackStack<LauncherKey>
