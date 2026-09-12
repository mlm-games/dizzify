package app.dizzify.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dizzify.BuildConfig
import app.dizzify.LauncherViewModel
import app.dizzify.helper.openUrl
import app.dizzify.settings.ImportExportState
import app.dizzify.settings.DefaultScreen
import app.dizzify.settings.SearchType
import app.dizzify.settings.SortOrder
import app.dizzify.settings.TextWeight
import app.dizzify.settings.ThemeMode
import app.dizzify.ui.dialogs.PinLockDialog
import app.dizzify.ui.theme.*
import kotlinx.coroutines.launch

sealed class SettingsCategory(
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    data object Appearance : SettingsCategory(
        "Appearance",
        Icons.Outlined.Palette,
        "Theme, icon packs, layout"
    )
    data object HomeScreen : SettingsCategory(
        "Home Screen",
        Icons.Outlined.Home,
        "Favorites, rows, widgets"
    )
    data object AppDrawer : SettingsCategory(
        "App Drawer",
        Icons.Outlined.Apps,
        "Grid size, sorting, search"
    )
    data object Behavior : SettingsCategory(
        "Behavior",
        Icons.Outlined.TouchApp,
        "Gestures, animations"
    )
    data object About : SettingsCategory(
        "About",
        Icons.Outlined.Info,
        "Version, licenses, feedback"
    )
}

@Composable
fun SettingsScreen(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    val showLockDialog by viewModel.showLockDialog.collectAsState()
    val isSettingPin by viewModel.isSettingPin.collectAsState()
    val locked by viewModel.effectiveLockState.collectAsState()
    var pinError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showLockDialog) { if (!showLockDialog) pinError = null }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetUnlockState() }
    }

    val categories = listOf(
        SettingsCategory.Appearance,
        SettingsCategory.HomeScreen,
        SettingsCategory.AppDrawer,
        SettingsCategory.Behavior,
        SettingsCategory.About
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LauncherColors.DarkBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Categories list
            SettingsCategoriesList(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                modifier = Modifier
                    .width(400.dp)
                    .fillMaxHeight()
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(LauncherColors.DarkSurfaceVariant)
            )

            // Settings content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (locked) {
                    LockedSettingsGate(
                        onUnlock = { viewModel.setShowLockDialog(true, false) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val effectiveCategory = selectedCategory ?: SettingsCategory.Appearance
                    SettingsCategoryContent(
                        category = effectiveCategory,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (showLockDialog) {
            PinLockDialog(
                isSettingPin = isSettingPin,
                validateError = pinError,
                onDismiss = { viewModel.setShowLockDialog(false) },
                onConfirm = { pin ->
                    if (isSettingPin) {
                        viewModel.setPin(pin)
                        // Lock only ever engages with a PIN set — no lockout trap.
                        if (!settings.lockSettings) viewModel.toggleLockSettings(true)
                        viewModel.setShowLockDialog(false)
                    } else {
                        scope.launch {
                            if (!viewModel.validatePin(pin)) {
                                pinError = "Wrong PIN, try again"
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun LockedSettingsGate(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(LauncherSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = LauncherColors.TextSecondary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(LauncherSpacing.lg))
        Text(
            text = "Settings are locked",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Text(
            text = "Enter your PIN to make changes",
            style = MaterialTheme.typography.bodyLarge,
            color = LauncherColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(LauncherSpacing.lg))
        Button(onClick = onUnlock) {
            Text("Unlock Settings")
        }
    }
}

@Composable
private fun SettingsCategoriesList(
    categories: List<SettingsCategory>,
    selectedCategory: SettingsCategory?,
    onCategorySelected: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(LauncherSpacing.lg)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displayMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(LauncherSpacing.xl))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(LauncherSpacing.sm)
        ) {
            items(categories) { category ->
                SettingsCategoryItem(
                    category = category,
                    isSelected = selectedCategory == category,
                    onClick = { onCategorySelected(category) }
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> LauncherColors.AccentBlue.copy(alpha = 0.3f)
            isSelected -> LauncherColors.AccentBlue.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        label = "category_bg"
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        label = "category_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .then(
                if (isFocused) Modifier.border(
                    width = 2.dp,
                    color = LauncherColors.AccentBlue.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .padding(LauncherSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected || isFocused)
                        LauncherColors.AccentBlue.copy(alpha = 0.2f)
                    else
                        LauncherColors.DarkSurfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = if (isSelected || isFocused)
                    LauncherColors.AccentBlue
                else
                    LauncherColors.TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(LauncherSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected || isFocused) Color.White else LauncherColors.TextPrimary
            )
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodyMedium,
                color = LauncherColors.TextSecondary
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = LauncherColors.AccentBlue
            )
        }
    }
}

@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.padding(LauncherSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LauncherSpacing.md)
    ) {
        item {
            Text(
                text = category.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(LauncherSpacing.lg))
        }

        when (category) {
            is SettingsCategory.Appearance -> {
                item {
                    SettingsSection(title = "Theme") {
                        SettingsDropdown(
                            title = "Theme Mode",
                            description = "Choose app theme",
                            currentValue = settings.theme.name,
                            options = ThemeMode.entries.map { it.name },
                            onOptionSelected = { selected ->
                                val mode = ThemeMode.valueOf(selected)
                                viewModel.updateTheme(mode)
                            }
                        )

                        SettingsToggle(
                            title = "Show Banners & Icons",
                            description = "Display app banners and icons in launcher",
                            isChecked = settings.showAppIcons,
                            onCheckedChange = { viewModel.updateShowAppIcons(it) }
                        )

                        SettingsToggle(
                            title = "Show App Names",
                            description = "Show labels under app icons",
                            isChecked = settings.showAppNames,
                            onCheckedChange = { viewModel.updateShowAppNames(it) }
                        )

                        SettingsDropdown(
                            title = "Text Size",
                            description = "Scale text across the launcher",
                            currentValue = when {
                                settings.textSizeScale <= 0.9f -> "Small"
                                settings.textSizeScale >= 1.25f -> "Extra Large"
                                settings.textSizeScale > 1.05f -> "Large"
                                else -> "Normal"
                            },
                            options = listOf("Small", "Normal", "Large", "Extra Large"),
                            onOptionSelected = { selected ->
                                viewModel.updateTextSizeScale(
                                    when (selected) {
                                        "Small" -> 0.85f
                                        "Large" -> 1.15f
                                        "Extra Large" -> 1.3f
                                        else -> 1.0f
                                    }
                                )
                            }
                        )
                    }
                }

                item {
                    FontSettingsSection(viewModel = viewModel)
                }

                item {
                    WallpaperSettingsSection(viewModel = viewModel)
                }
            }

            is SettingsCategory.HomeScreen -> {
                item {
                    SettingsSection(title = "Layout") {
                        SettingsClickable(
                            title = "Edit Favorites",
                            description = "Long-press apps to add to favorites",
                            onClick = { /* Navigate to home and show hint */ }
                        )

                        SettingsDropdown(
                            title = "Home Grid Rows",
                            description = "Vertical density of the home grid",
                            currentValue = settings.homeScreenRows.toString(),
                            options = (4..12).map { it.toString() },
                            onOptionSelected = { selected ->
                                selected.toIntOrNull()?.let { viewModel.updateHomeScreenRows(it) }
                            }
                        )

                        SettingsDropdown(
                            title = "Home Grid Columns",
                            description = "Horizontal density of the home grid",
                            currentValue = settings.homeScreenColumns.toString(),
                            options = (2..8).map { it.toString() },
                            onOptionSelected = { selected ->
                                selected.toIntOrNull()?.let { viewModel.updateHomeScreenColumns(it) }
                            }
                        )
                    }
                }
            }

            is SettingsCategory.AppDrawer -> {
                item {
                    SettingsSection(title = "Sorting") {
                        SettingsDropdown(
                            title = "Sort Order",
                            description = "How apps are sorted",
                            currentValue = when (settings.sortOrder) {
                                SortOrder.AZ -> "A-Z"
                                SortOrder.ZA -> "Z-A"
                                SortOrder.Recent -> "Recent"
                            },
                            options = listOf("A-Z", "Z-A", "Recent"),
                            onOptionSelected = { selected ->
                                val order = when (selected) {
                                    "A-Z" -> SortOrder.AZ
                                    "Z-A" -> SortOrder.ZA
                                    "Recent" -> SortOrder.Recent
                                    else -> SortOrder.AZ
                                }
                                viewModel.updateSortOrder(order)
                            }
                        )

                        SettingsToggle(
                            title = "Show System Apps",
                            description = "Include pre-installed system apps",
                            isChecked = settings.showSystemApps,
                            onCheckedChange = { viewModel.updateShowSystemApps(it) }
                        )
                    }
                }

                item {
                    SettingsSection(title = "Search") {
                        SettingsDropdown(
                            title = "Search Type",
                            description = "How search matches apps",
                            currentValue = when (settings.searchType) {
                                SearchType.Contains -> "Contains"
                                SearchType.Fuzzy -> "Fuzzy"
                                SearchType.StartsWith -> "Starts With"
                                SearchType.Exact -> "Exact"
                            },
                            options = listOf("Contains", "Fuzzy", "Starts With", "Exact"),
                            onOptionSelected = { selected ->
                                val type = when (selected) {
                                    "Contains" -> SearchType.Contains
                                    "Fuzzy" -> SearchType.Fuzzy
                                    "Starts With" -> SearchType.StartsWith
                                    "Exact" -> SearchType.Exact
                                    else -> SearchType.Contains
                                }
                                viewModel.updateSearchType(type)
                            }
                        )

                        SettingsToggle(
                            title = "Search Package Names",
                            description = "Include package names in search",
                            isChecked = settings.searchIncludePackageNames,
                            onCheckedChange = { viewModel.updateSearchIncludePackageNames(it) }
                        )

                        SettingsToggle(
                            title = "Show Hidden in Search",
                            description = "Include hidden apps in search results",
                            isChecked = settings.showHiddenAppsOnSearch,
                            onCheckedChange = { viewModel.updateShowHiddenAppsOnSearch(it) }
                        )

                        SettingsToggle(
                            title = "Auto-Open Single Result",
                            description = "Launch immediately when search has one match",
                            isChecked = settings.autoOpenFilteredApp,
                            onCheckedChange = { viewModel.updateAutoOpenFilteredApp(it) }
                        )

                        SettingsToggle(
                            title = "Web Search Option",
                            description = "Offer web search when nothing matches",
                            isChecked = settings.showWebSearchOption,
                            onCheckedChange = { viewModel.updateShowWebSearchOption(it) }
                        )
                    }
                }
            }

            is SettingsCategory.Behavior -> {
                item {
                    SettingsSection(title = "TV Options") {
                        SettingsToggle(
                            title = "Show Non-TV Apps",
                            description = "Show apps without Leanback support",
                            isChecked = settings.showNonTvApps,
                            onCheckedChange = { viewModel.updateShowNonTvApps(it) }
                        )

                        SettingsToggle(
                            title = "Prefer TV Launch",
                            description = "Open TV UI when app supports it (VLC, Dolphin)",
                            isChecked = settings.preferTvLaunch,
                            onCheckedChange = { viewModel.updatePreferTvLaunch(it) }
                        )

                        SettingsToggle(
                            title = "TV Inputs Row",
                            description = "Show HDMI and other TV inputs on Home",
                            isChecked = settings.showTvInputs,
                            onCheckedChange = { viewModel.updateShowTvInputs(it) }
                        )

                        SettingsToggle(
                            title = "Continue Watching",
                            description = "Show Watch-Next programs from your apps",
                            isChecked = settings.showWatchNext,
                            onCheckedChange = { viewModel.updateShowWatchNext(it) }
                        )

                        SettingsToggle(
                            title = "Return to Home",
                            description = "Go back to Home after opening an app",
                            isChecked = settings.returnToHomeAfterApp,
                            onCheckedChange = { viewModel.updateReturnToHomeAfterApp(it) }
                        )

                        SettingsDropdown(
                            title = "Default Screen",
                            description = "Where the launcher starts and Home returns",
                            currentValue = if (settings.defaultScreen == DefaultScreen.Apps) "Apps" else "Home",
                            options = listOf("Home", "Apps"),
                            onOptionSelected = { selected ->
                                viewModel.updateDefaultScreen(
                                    if (selected == "Apps") DefaultScreen.Apps else DefaultScreen.Home
                                )
                            }
                        )
                    }
                }

                item {
                    SettingsSection(title = "Settings Lock") {
                        SettingsToggle(
                            title = "Lock Settings",
                            description = "Require PIN to change settings",
                            isChecked = settings.lockSettings,
                            onCheckedChange = { locked ->
                                if (locked && settings.settingsLockPin.isEmpty()) {
                                    // No PIN yet — the dialog enables lock after one is set.
                                    viewModel.setShowLockDialog(true, true)
                                } else {
                                    viewModel.toggleLockSettings(locked)
                                }
                            }
                        )

                        SettingsClickable(
                            title = if (settings.settingsLockPin.isEmpty()) "Set PIN" else "Change PIN",
                            description = "6 digits max, stored as salted hash",
                            onClick = { viewModel.setShowLockDialog(true, true) }
                        )
                    }
                }
            }

            is SettingsCategory.About -> {
                item {
                    SettingsSection(title = "App Info") {
                        SettingsInfo(
                            title = "Version",
                            value = BuildConfig.VERSION_NAME
                        )
                        SettingsInfo(
                            title = "Build",
                            value = BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }
                        )
                    }
                }

                item {
                    BackupRestoreSection(viewModel = viewModel)
                }

                item {
                    SettingsSection(title = "Links") {
                        SettingsClickable(
                            title = "Source Code",
                            description = "View on GitHub",
                            onClick = {
                                context.openUrl("https://github.com/user/dizzify")
                            }
                        )
                        SettingsClickable(
                            title = "Report Issue",
                            description = "Submit bug report",
                            onClick = {
                                context.openUrl("https://github.com/user/dizzify/issues")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperSettingsSection(viewModel: LauncherViewModel) {
    val settings by viewModel.settings.collectAsState()

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.setWallpaperImage(uri)
    }

    SettingsSection(title = "Wallpaper") {
        SettingsInfo(
            title = "Home Background",
            value = if (settings.wallpaperPath.isNotBlank()) "Custom image" else "Default",
        )

        SettingsClickable(
            title = "Choose Wallpaper Image",
            description = "Stored locally; used when the TV has no system wallpaper",
            onClick = { wallpaperPicker.launch("image/*") },
        )

        if (settings.wallpaperPath.isNotBlank()) {
            SettingsClickable(
                title = "Reset Wallpaper",
                description = "Back to the default background",
                onClick = { viewModel.clearWallpaper() },
            )
        }
    }
}

@Composable
private fun FontSettingsSection(viewModel: LauncherViewModel) {
    val settings by viewModel.settings.collectAsState()
    val fontInfo by viewModel.customFontInfo.collectAsState()

    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.setCustomFont(uri)
    }

    val weightNames = listOf("Thin", "Light", "Normal", "Medium", "Bold", "Black")
    val weightValues = TextWeight.entries

    SettingsSection(title = "Font") {
        SettingsInfo(
            title = "Current Font",
            value = fontInfo?.let { (name, size) ->
                "$name (${size / 1024} KB)"
            } ?: "System font"
        )

        SettingsToggle(
            title = "Use System Font",
            description = "Off when a custom font file is active",
            isChecked = settings.useSystemFont,
            onCheckedChange = { viewModel.updateUseSystemFont(it) }
        )

        SettingsDropdown(
            title = "Font Weight",
            description = "Bolder text reads better from the couch",
            currentValue = weightNames[settings.fontWeight.ordinal.coerceIn(weightNames.indices)],
            options = weightNames,
            onOptionSelected = { selected ->
                val index = weightNames.indexOf(selected).coerceIn(weightValues.indices)
                viewModel.updateFontWeight(weightValues[index])
            }
        )

        SettingsClickable(
            title = "Choose Font File",
            description = "Pick a .ttf/.otf file (copied locally, 5 MB max)",
            onClick = { fontPicker.launch(arrayOf("font/*", "application/*", "*/*")) }
        )

        if (fontInfo != null) {
            SettingsClickable(
                title = "Reset to System Font",
                description = "Delete the custom font file",
                onClick = { viewModel.clearCustomFont() }
            )
        }
    }
}

@Composable
private fun BackupRestoreSection(viewModel: LauncherViewModel) {
    val backupState by viewModel.importExportState.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportSettings(uri)
        else viewModel.resetImportExportState()
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importSettings(uri)
        else viewModel.resetImportExportState()
    }

    SettingsSection(title = "Backup & Restore") {
        SettingsClickable(
            title = "Export Settings",
            description = "Save setup to a file (layout, hidden, renames)",
            onClick = {
                viewModel.resetImportExportState()
                exportLauncher.launch("dizzify-backup.json")
            }
        )

        SettingsClickable(
            title = "Import Settings",
            description = "Restore setup from a backup file",
            onClick = {
                viewModel.resetImportExportState()
                importLauncher.launch(arrayOf("application/json"))
            }
        )

        when (val s = backupState) {
            ImportExportState.Idle -> Unit
            ImportExportState.Loading -> {
                SettingsInfo(title = "Status", value = "Working…")
            }
            ImportExportState.ExportSuccess -> {
                SettingsInfo(title = "Status", value = "Backup saved")
            }
            is ImportExportState.ImportSuccess -> {
                val summary = buildString {
                    append("Applied ${s.appliedCount}, skipped ${s.skippedCount}")
                    if (s.errors.isNotEmpty()) append(", ${s.errors.size} errors")
                }
                SettingsInfo(title = "Status", value = summary)
                s.errors.take(3).forEach { (key, msg) ->
                    SettingsInfo(title = key, value = msg)
                }
            }
            is ImportExportState.Error -> {
                SettingsInfo(title = "Error", value = s.message)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = LauncherColors.AccentBlue,
            modifier = Modifier.padding(bottom = LauncherSpacing.sm)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = LauncherColors.DarkSurface
        ) {
            Column(
                modifier = Modifier.padding(LauncherSpacing.md),
                verticalArrangement = Arrangement.spacedBy(LauncherSpacing.sm),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) LauncherColors.DarkSurfaceVariant else Color.Transparent)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onCheckedChange(!isChecked)
                    true
                } else false
            }
            .focusable()
            .padding(LauncherSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = LauncherColors.TextSecondary
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LauncherColors.AccentBlue,
                checkedTrackColor = LauncherColors.AccentBlue.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun SettingsClickable(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) LauncherColors.DarkSurfaceVariant else Color.Transparent)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .padding(LauncherSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = LauncherColors.TextSecondary
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LauncherColors.TextSecondary
        )
    }
}

@Composable
private fun SettingsDropdown(
    title: String,
    description: String,
    currentValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) LauncherColors.DarkSurfaceVariant else Color.Transparent)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    expanded = true
                    true
                } else false
            }
            .focusable()
            .padding(LauncherSpacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LauncherColors.TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LauncherColors.AccentBlue
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = LauncherColors.TextSecondary
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(LauncherColors.DarkSurface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (option == currentValue) LauncherColors.AccentBlue else Color.White
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    leadingIcon = if (option == currentValue) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = LauncherColors.AccentBlue
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun SettingsInfo(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LauncherSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = LauncherColors.TextSecondary
        )
    }
}
