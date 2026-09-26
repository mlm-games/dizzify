package app.dizzify.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dizzify.data.AppLaunchMode
import app.dizzify.data.AppModel
import app.dizzify.LauncherViewModel
import app.dizzify.helper.VoiceSearch
import app.dizzify.helper.openSearch
import app.dizzify.settings.SortOrder
import app.dizzify.ui.components.*
import app.dizzify.ui.theme.*
import androidx.compose.foundation.clickable as mainClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val homeApps by viewModel.homeApps.collectAsState()
    val launcherState by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val ui by viewModel.ui.collectAsState()

    val context = LocalContext.current
    val appOptions = rememberAppOptionsState()
    var viewMode by remember { mutableStateOf(AppsViewMode.LIST) }

    val searchFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val focusRestorer = rememberFocusRestorer()

    // Set by the A-Z strip once it has scrolled the target card into view; the card claims
    // focus when it composes, because a requester for a card that is not composed yet is a no-op.
    var pendingFocusKey by remember { mutableStateOf<String?>(null) }

    // isVoiceSearchAvailable() runs a PackageManager query, so keep it off the composition pass.
    var voiceAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        voiceAvailable = withContext(Dispatchers.IO) { viewModel.isVoiceSearchAvailable() }
    }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            VoiceSearch.parseResult(result.data)?.let { viewModel.setQuery(it) }
        }
    }

    var consumedQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(apps, ui.query, settings.autoOpenFilteredApp) {
        if (ui.query.isBlank()) {
            consumedQuery = null
            return@LaunchedEffect
        }
        if (settings.autoOpenFilteredApp &&
            ui.query.isNotBlank() && apps.size == 1 && consumedQuery != ui.query
        ) {
            consumedQuery = ui.query
            viewModel.launch(apps.first())
        }
    }

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
                onSearch = { if (ui.query.isNotBlank()) apps.firstOrNull()?.let { viewModel.launch(it) } },
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                appCount = apps.size,
                searchFocusRequester = searchFocusRequester,
                onVoiceSearch = if (voiceAvailable) {
                    { runCatching { voiceLauncher.launch(VoiceSearch.intent()) } }
                } else null,
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
                    showWebSearch = settings.showWebSearchOption,
                    onWebSearch = { openSearch(context, ui.query) },
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
                        val focusRequester = focusRestorer.getFocusRequester(app.getKey())

                        LaunchedEffect(pendingFocusKey) {
                            if (pendingFocusKey == app.getKey()) {
                                runCatching { focusRequester.requestFocus() }
                                    .onSuccess { pendingFocusKey = null }
                            }
                        }

                        StaggeredAnimatedVisibility(
                            visible = true,
                            index = index % 12 // Limit stagger to visible items
                        ) {
                            AppCard(
                                app = app,
                                onClick = { viewModel.launch(app) },
                                onLongClick = {
                                    appOptions.open(app)
                                },
                                style = if (viewMode == AppsViewMode.GRID)
                                    CardStyle.STANDARD
                                else
                                    CardStyle.BANNER,
                                focusRequester = focusRequester
                            )
                        }
                    }
                }
            }
        }

        // Alphabet quick jump indicator. The grid only groups apps by initial while it is in
        // A-Z order, so the strip is meaningless for Z-A/Recent (or a reversed search result).
        if (settings.sortOrder == SortOrder.AZ && !settings.reverseSearchResults) {
            AlphabetJumpIndicator(
                apps = apps,
                gridState = gridState,
                onJumped = { app -> pendingFocusKey = app.getKey() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = LauncherSpacing.md)
            )
        }

        // App options sheet
        AppOptionsHost(
            state = appOptions,
            onOpen = { viewModel.launch(it) },
            onToggleHidden = { viewModel.toggleHidden(it) },
            isHidden = { app -> hiddenApps.any { it.getKey() == app.getKey() } },
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
private fun AppsHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    viewMode: AppsViewMode,
    onViewModeChange: (AppsViewMode) -> Unit,
    appCount: Int,
    searchFocusRequester: FocusRequester,
    onVoiceSearch: (() -> Unit)? = null,
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
            onSearch = onSearch,
            focusRequester = searchFocusRequester,
            onVoiceSearch = onVoiceSearch,
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
    showWebSearch: Boolean,
    onWebSearch: () -> Unit,
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

        if (showWebSearch) {
            Spacer(modifier = Modifier.height(LauncherSpacing.lg))
            Button(onClick = onWebSearch) {
                Text("Search the web")
            }
        }
    }
}

@Composable
private fun AlphabetJumpIndicator(
    apps: List<AppModel>,
    gridState: LazyGridState,
    onJumped: (AppModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val alphabet = remember(apps) {
        apps.map { it.appLabel.firstOrNull()?.uppercaseChar() ?: '#' }
            .distinct()
            .sorted()
    }
    if (alphabet.isEmpty()) return

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LauncherColors.DarkSurface.copy(alpha = 0.8f))
            .padding(vertical = LauncherSpacing.sm, horizontal = LauncherSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        alphabet.forEach { letter ->
            var isFocused by remember(letter) { mutableStateOf(false) }

            val jumpToLetter = {
                val index = apps.indexOfFirst {
                    it.appLabel.firstOrNull()?.uppercaseChar() == letter
                }
                if (index >= 0) {
                    coroutineScope.launch {
                        gridState.animateScrollToItem(index)
                        onJumped(apps[index])
                    }
                }
                Unit
            }

            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isFocused) LauncherColors.AccentBlue else LauncherColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isFocused) LauncherColors.AccentBlue.copy(alpha = 0.2f) else Color.Transparent
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickableNoRipple { jumpToLetter() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
