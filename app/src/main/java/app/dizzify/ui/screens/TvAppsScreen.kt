package app.dizzify.ui.screens

import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dizzify.data.AppModel
import app.dizzify.LauncherViewModel
import app.dizzify.ui.components.*
import app.dizzify.ui.theme.*
import androidx.compose.foundation.clickable as mainClickable
import kotlinx.coroutines.launch

enum class AppsViewMode {
    GRID,
    LIST
}

@Composable
fun AppsScreen(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val apps by viewModel.appsFiltered.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val ui by viewModel.ui.collectAsState()

    val context = LocalContext.current
    var selectedApp by remember { mutableStateOf<AppModel?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(AppsViewMode.LIST) }

    val searchFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LauncherColors.DarkBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Search and view toggle
            AppsHeader(
                query = ui.query,
                onQueryChange = viewModel::setQuery,
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                appCount = apps.size,
                searchFocusRequester = searchFocusRequester,
                modifier = Modifier.padding(
                    start = LauncherSpacing.screenPadding,
                    end = LauncherSpacing.screenPadding,
                    top = LauncherSpacing.lg,
                    bottom = LauncherSpacing.lg
                )
            )

            // Apps grid
            if (apps.isEmpty() && ui.query.isNotEmpty()) {
                EmptySearchResult(
                    query = ui.query,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(LauncherSpacing.screenPadding)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (viewMode == AppsViewMode.GRID) 6 else 3),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = LauncherSpacing.screenPadding),
                    contentPadding = PaddingValues(bottom = LauncherSpacing.xxxl),
                    horizontalArrangement = Arrangement.spacedBy(LauncherSpacing.cardGap),
                    verticalArrangement = Arrangement.spacedBy(LauncherSpacing.cardGap)
                ) {
                    itemsIndexed(
                        items = apps,
                        key = { _, app -> app.getKey() }
                    ) { index, app ->
                        StaggeredAnimatedVisibility(
                            visible = true,
                            index = index % 12 // Limit stagger to visible items
                        ) {
                            AppCard(
                                app = app,
                                onClick = { viewModel.launch(app) },
                                onLongClick = {
                                    selectedApp = app
                                    showOptions = true
                                },
                                style = if (viewMode == AppsViewMode.GRID)
                                    CardStyle.STANDARD
                                else
                                    CardStyle.BANNER
                            )
                        }
                    }
                }
            }
        }

        // Alphabet quick jump indicator
        AlphabetJumpIndicator(
            apps = apps,
            gridState = gridState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = LauncherSpacing.md)
        )

        // App options sheet
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
                isHidden = hiddenApps.any { it.getKey() == app.getKey() }
            )
        }
    }
}

@Composable
private fun AppsHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    viewMode: AppsViewMode,
    onViewModeChange: (AppsViewMode) -> Unit,
    appCount: Int,
    searchFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "All Apps",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
                Text(
                    text = "$appCount apps installed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LauncherColors.TextSecondary
                )
            }

            // View mode toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(LauncherColors.DarkSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ViewModeButton(
                    icon = Icons.AutoMirrored.Filled.ViewList,
                    isSelected = viewMode == AppsViewMode.LIST,
                    onClick = { onViewModeChange(AppsViewMode.LIST) }
                )
                ViewModeButton(
                    icon = Icons.Default.GridView,
                    isSelected = viewMode == AppsViewMode.GRID,
                    onClick = { onViewModeChange(AppsViewMode.GRID) }
                )
            }
        }

        Spacer(modifier = Modifier.height(LauncherSpacing.lg))

        LauncherSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { },
            focusRequester = searchFocusRequester,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ViewModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) LauncherColors.AccentBlue.copy(alpha = 0.2f)
                else Color.Transparent
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) LauncherColors.AccentBlue else LauncherColors.TextSecondary
        )
    }
}

@Composable
private fun EmptySearchResult(
    query: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔍",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(LauncherSpacing.lg))

        Text(
            text = "No apps found",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Text(
            text = "No results for \"$query\"",
            style = MaterialTheme.typography.bodyLarge,
            color = LauncherColors.TextSecondary
        )
    }
}

@Composable
private fun AlphabetJumpIndicator(
    apps: List<AppModel>,
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val alphabet = remember(apps) {
        apps.map { it.appLabel.firstOrNull()?.uppercaseChar() ?: '#' }
            .distinct()
            .sorted()
    }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LauncherColors.DarkSurface.copy(alpha = 0.8f))
            .padding(vertical = LauncherSpacing.sm, horizontal = LauncherSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        alphabet.forEach { letter ->
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = LauncherColors.TextSecondary,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .clickableNoRipple {
                        val index = apps.indexOfFirst {
                            it.appLabel.firstOrNull()?.uppercaseChar() == letter
                        }
                        if (index >= 0) {
                            coroutineScope.launch {
                                gridState.animateScrollToItem(index)
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    )
}

private fun Modifier.clickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    onClick: () -> Unit
): Modifier = mainClickable(
    interactionSource = interactionSource,
    indication = indication,
    onClick = onClick
).let { this.then(it) }