package app.dizzify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.dizzify.data.AppLaunchMode
import app.dizzify.data.AppModel
import app.dizzify.LauncherViewModel
import app.dizzify.ui.components.*
import app.dizzify.ui.theme.*
import app.dizzify.ui.components.AppOptionContext

@Composable
fun HiddenAppsScreen(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val launcherState by viewModel.state.collectAsState()

    val appOptions = rememberAppOptionsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LauncherColors.DarkBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Column(
                modifier = Modifier.padding(
                    start = LauncherSpacing.screenPadding,
                    end = LauncherSpacing.screenPadding,
                    top = LauncherSpacing.lg,
                    bottom = LauncherSpacing.lg
                )
            ) {
                Text(
                    text = "Hidden Apps",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
                Text(
                    text = "${hiddenApps.size} apps hidden",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LauncherColors.TextSecondary
                )
            }

            if (hiddenApps.isEmpty()) {
                // Empty state
                EmptyHiddenState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(LauncherSpacing.screenPadding)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = LauncherSpacing.screenPadding),
                    contentPadding = PaddingValues(bottom = LauncherSpacing.xxxl),
                    horizontalArrangement = Arrangement.spacedBy(LauncherSpacing.cardGap),
                    verticalArrangement = Arrangement.spacedBy(LauncherSpacing.cardGap)
                ) {
                    itemsIndexed(
                        items = hiddenApps,
                        key = { _, app -> app.getKey() }
                    ) { index, app ->
                        StaggeredAnimatedVisibility(
                            visible = true,
                            index = index
                        ) {
                            AppCard(
                                app = app,
                                onClick = { viewModel.launch(app) },
                                onLongClick = {
                                    appOptions.open(app)
                                },
                                style = CardStyle.STANDARD
                            )
                        }
                    }
                }
            }
        }

        // App options sheet
        AppOptionsHost(
            state = appOptions,
            onOpen = { viewModel.launch(it) },
            onToggleHidden = { viewModel.toggleHidden(it) },
            isHidden = { true },
            contextFor = { AppOptionContext.FromHidden() },
            onOpenTv = { app -> viewModel.launchInTvMode(app) },
            onOpenMobile = { app -> viewModel.launchInMobileMode(app) },
            launchModeFor = { app -> launcherState.appLaunchModes[app.getKey()] ?: AppLaunchMode.AUTO },
            onLaunchModeChange = { app, mode -> viewModel.setAppLaunchMode(app, mode) },
            onRename = { app, n -> viewModel.renameApp(app, n) },
        )
    }
}

@Composable
private fun EmptyHiddenState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = LauncherColors.TextTertiary
        )

        Spacer(modifier = Modifier.height(LauncherSpacing.lg))

        Text(
            text = "No Hidden Apps",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(LauncherSpacing.sm))

        Text(
            text = "Long-press any app and select 'Hide' to add it here",
            style = MaterialTheme.typography.bodyLarge,
            color = LauncherColors.TextSecondary
        )
    }
}