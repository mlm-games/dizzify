package app.dizzify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dizzify.data.AppModel
import app.dizzify.LauncherViewModel
import app.dizzify.ui.components.*
import app.dizzify.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    onNavigateToApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val homeApps by viewModel.homeApps.collectAsState()
    val recentApps by viewModel.recentApps.collectAsState()
    val allApps by viewModel.apps.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val ui by viewModel.ui.collectAsState()

    val context = LocalContext.current
    var selectedApp by remember { mutableStateOf<AppModel?>(null) }
    var showOptions by remember { mutableStateOf(false) }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LauncherColors.DarkBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            LauncherColors.AccentBlue.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

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

            if (homeApps.isNotEmpty()) {
                AppRow(
                    title = "Favorites",
                    apps = homeApps,
                    onAppClick = { app -> viewModel.launch(app) },
                    onAppLongClick = { app ->
                        selectedApp = app
                        showOptions = true
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
                        selectedApp = app
                        showOptions = true
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
                        selectedApp = app
                        showOptions = true
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
                        selectedApp = app
                        showOptions = true
                    },
                    cardStyle = CardStyle.STANDARD,
                    accentColor = LauncherColors.AccentTeal
                )

                Spacer(modifier = Modifier.height(LauncherSpacing.sectionGap))
            }

            if (allApps.isNotEmpty()) {
                AppRow(
                    title = "All Apps",
                    apps = allApps.take(15),
                    onAppClick = { app -> viewModel.launch(app) },
                    onAppLongClick = { app ->
                        selectedApp = app
                        showOptions = true
                    },
                    cardStyle = CardStyle.COMPACT,
                    accentColor = LauncherColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(LauncherSpacing.xxxl))
        }

        selectedApp?.let { app ->
            AppOptionsSheet(
                app = app,
                isVisible = showOptions,
                onDismiss = {
                    showOptions = false
                    selectedApp = null
                },
                onOpen = { viewModel.launch(app) },
                onToggleHidden = { viewModel.toggleHidden(app) },
                onToggleFavorite = { viewModel.toggleFavorite(app) },
                isFavorite = homeApps.any { it.getKey() == app.getKey() },
                isHidden = hiddenApps.any { it.getKey() == app.getKey() }
            )
        }
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