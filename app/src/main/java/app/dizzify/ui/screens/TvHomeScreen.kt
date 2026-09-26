package app.dizzify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.dizzify.data.AppLaunchMode
import app.dizzify.data.AppModel
import app.dizzify.helper.WallpaperHelper
import app.dizzify.LauncherViewModel
import app.dizzify.ui.components.*
import app.dizzify.ui.theme.*
import app.dizzify.ui.components.AppOptionContext

@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    onNavigateToApps: () -> Unit,
    onNavigateToWidgetPicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val homeApps by viewModel.homeApps.collectAsState()
    val favoriteApps by viewModel.favoriteApps.collectAsState()
    val recentApps by viewModel.recentApps.collectAsState()
    val allApps by viewModel.apps.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val launcherState by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val tvInputs by viewModel.tvInputs.collectAsState()
    val watchNext by viewModel.watchNext.collectAsState()

    val context = LocalContext.current
    val appOptions = rememberAppOptionsState()

    val scrollState = rememberScrollState()

    val gameApps = remember(allApps) {
        allApps.filter { app ->
            app.appPackage.contains("game", ignoreCase = true) ||
                    app.appLabel.contains("game", ignoreCase = true)
        }.take(20)
    }

    val mediaApps = remember(allApps) {
        allApps.filter { app ->
            listOf("netflix", "youtube", "plex", "spotify", "music", "video", "player", "tv")
                .any { keyword ->
                    app.appPackage.contains(keyword, ignoreCase = true) ||
                            app.appLabel.contains(keyword, ignoreCase = true)
                }
        }.take(12)
    }

    val wallpaperBackground = remember(settings.wallpaperPath) {
        WallpaperHelper.resolveBackground(context, settings.wallpaperPath)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LauncherColors.DarkBackground)
    ) {
        HomeWallpaperLayer(background = wallpaperBackground)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = LauncherSpacing.lg)
        ) {
            WelcomeHeader(
                modifier = Modifier.padding(
                    start = LauncherSpacing.screenPadding,
                    end = LauncherSpacing.screenPadding,
                    bottom = LauncherSpacing.xl
                )
            )

            HomeGridSection(
                viewModel = viewModel,
                onNavigateToWidgetPicker = onNavigateToWidgetPicker
            )

            Spacer(modifier = Modifier.height(LauncherSpacing.sectionGap))

            if (settings.showWatchNext && watchNext.isNotEmpty()) {
                WatchNextRow(
                    items = watchNext,
                    onItemClick = { viewModel.playWatchNext(it) }
                )

                Spacer(modifier = Modifier.height(LauncherSpacing.sectionGap))
            }

            if (homeApps.isNotEmpty()) {
                AppRow(
                    title = "Favorites",
                    apps = homeApps,
                    onAppClick = { app -> viewModel.launch(app) },
                    onAppLongClick = { app ->
                        appOptions.open(app)
                    },
                    cardStyle = CardStyle.STANDARD,
                    accentColor = LauncherColors.AccentOrange
                )

                Spacer(modifier = Modifier.height(LauncherSpacing.sectionGap))
            }

            if (recentApps.isNotEmpty()) {
                AppRow(
                    title = "Recently Used",
                    apps = recentApps,
                    onAppClick = { app -> viewModel.launch(app) },
                    onAppLongClick = { app ->
                        appOptions.open(app)
                    },
                    cardStyle = CardStyle.COMPACT,
                    accentColor = LauncherColors.AccentBlue
                )

                Spacer(modifier = Modifier.height(LauncherSpacing.sectionGap))
            }

            // Media & Entertainment row
            if (mediaApps.isNotEmpty()) {
                AppRow(
                    title = "Media & Entertainment",
                    apps = mediaApps,
                    onAppClick = { app -> viewModel.launch(app) },
                    onAppLongClick = { app ->
                        appOptions.open(app)
                    },
                    cardStyle = CardStyle.BANNER,
                    accentColor = LauncherColors.AccentPurple
                )

                Spacer(modifier = Modifier.height(LauncherSpacing.sectionGap))
            }

            if (gameApps.isNotEmpty()) {
                AppRow(
                    title = "Games",
                    apps = gameApps,
                    onAppClick = { app -> viewModel.launch(app) },
                    onAppLongClick = { app ->
                        appOptions.open(app)
                    },
                    cardStyle = CardStyle.STANDARD,
                    accentColor = LauncherColors.AccentTeal
                )

                Spacer(modifier = Modifier.height(LauncherSpacing.sectionGap))
            }

            if (settings.showTvInputs && tvInputs.isNotEmpty()) {
                TvInputsRow(
                    inputs = tvInputs,
                    onInputClick = { viewModel.switchTvInput(it) }
                )

                Spacer(modifier = Modifier.height(LauncherSpacing.sectionGap))
            }

            if (allApps.isNotEmpty()) {
                AppRow(
                    title = "All Apps",
                    apps = allApps.take(15),
                    onAppClick = { app -> viewModel.launch(app) },
                    onAppLongClick = { app ->
                        appOptions.open(app)
                    },
                    cardStyle = CardStyle.COMPACT,
                    accentColor = LauncherColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(LauncherSpacing.xxxl))
        }

        AppOptionsHost(
            state = appOptions,
            onOpen = { viewModel.launch(it) },
            onToggleHidden = { viewModel.toggleHidden(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            isFavorite = { app -> favoriteApps.contains(app.getKey()) },
            isHidden = { app -> hiddenApps.any { it.getKey() == app.getKey() } },
            contextFor = { app ->
                AppOptionContext.FromHome(
                    isFavorite = favoriteApps.contains(app.getKey()),
                    onToggleFavorite = { viewModel.toggleFavorite(app) }
                )
            },
            onOpenTv = { app -> viewModel.launchInTvMode(app) },
            onOpenMobile = { app -> viewModel.launchInMobileMode(app) },
            launchModeFor = { app -> launcherState.appLaunchModes[app.getKey()] ?: AppLaunchMode.AUTO },
            onLaunchModeChange = { app, mode -> viewModel.setAppLaunchMode(app, mode) },
            onRename = { app, n -> viewModel.renameApp(app, n) },
            onToggleHome = { app -> viewModel.toggleHomeApp(app) },
            isOnHome = { app -> homeApps.any { it.getKey() == app.getKey() } },
        )
    }
}

@Composable
private fun WelcomeHeader(modifier: Modifier = Modifier) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000) // Update every minute
        }
    }

    val greeting = remember(currentTime) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    Column(modifier = modifier) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.displayMedium,
            color = Color.White
        )

        Text(
            text = "Feeling Dizzy?",
            style = MaterialTheme.typography.bodyLarge,
            color = LauncherColors.TextSecondary
        )
    }
}