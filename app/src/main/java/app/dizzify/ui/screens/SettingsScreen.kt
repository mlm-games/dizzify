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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dizzify.BuildConfig
import app.dizzify.LauncherViewModel
import app.dizzify.R
import app.dizzify.data.Constants
import app.dizzify.helper.openUrl
import app.dizzify.settings.ImportExportState
import app.dizzify.settings.DefaultScreen
import app.dizzify.settings.LauncherSettingsSchema
import app.dizzify.settings.NonTvApps
import app.dizzify.settings.SearchType
import app.dizzify.settings.SortOrder
import app.dizzify.settings.TextWeight
import app.dizzify.settings.ThemeMode
import app.dizzify.ui.dialogs.PinLockDialog
import app.dizzify.ui.theme.*
import app.dizzify.ui.components.tvPointerClick
import io.github.mlmgames.settings.core.locale.AppLanguage
import io.github.mlmgames.settings.core.resources.StringResourceProvider
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

sealed class SettingsCategory(
    val titleRes: Int,
    val icon: ImageVector,
    val descriptionRes: Int
) {
    data object Appearance : SettingsCategory(
        R.string.appearance,
        Icons.Outlined.Palette,
        R.string.appearance_desc
    )
    data object HomeScreen : SettingsCategory(
        R.string.home_screen,
        Icons.Outlined.Home,
        R.string.home_screen_desc
    )
    data object AppDrawer : SettingsCategory(
        R.string.app_drawer,
        Icons.Outlined.Apps,
        R.string.app_drawer_desc
    )
    data object Behavior : SettingsCategory(
        R.string.behavior,
        Icons.Outlined.TouchApp,
        R.string.behavior_desc
    )
    data object About : SettingsCategory(
        R.string.about,
        Icons.Outlined.Info,
        R.string.about_desc
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
    var pinErrorNonce by remember { mutableIntStateOf(0) }
    val wrongPinError = stringResource(R.string.wrong_pin_try_again)
    LaunchedEffect(showLockDialog) { if (!showLockDialog) pinError = null }

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
                validateNonce = pinErrorNonce,
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
                                pinError = wrongPinError
                                pinErrorNonce += 1
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
            text = stringResource(R.string.settings_are_locked),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Text(
            text = stringResource(R.string.enter_pin_to_make_changes),
            style = MaterialTheme.typography.bodyLarge,
            color = LauncherColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(LauncherSpacing.lg))
        Button(onClick = onUnlock) {
            Text(stringResource(R.string.unlock_settings))
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
            text = stringResource(R.string.section_settings),
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
            .tvPointerClick(onClick)
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
                text = stringResource(category.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected || isFocused) Color.White else LauncherColors.TextPrimary
            )
            Text(
                text = stringResource(category.descriptionRes),
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
                text = stringResource(category.titleRes),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(LauncherSpacing.lg))
        }

        when (category) {
            is SettingsCategory.Appearance -> {
                item {
                    val systemLanguageLabel = stringResource(R.string.system_default)
                    val languages = remember {
                        val allowed = settingMeta("language")?.languages.orEmpty()
                        buildList {
                            add(AppLanguage.System)
                            AppLanguage.entries.forEach { language ->
                                val tag = language.languageTag
                                if (tag != null && (allowed.isEmpty() || tag in allowed)) add(language)
                            }
                        }
                    }
                    val languageLabels = languages.map { language ->
                        if (language == AppLanguage.System) systemLanguageLabel else language.endonym
                    }
                    val currentLanguage = languages.indexOf(settings.language)
                    SettingsSection(title = stringResource(R.string.language)) {
                        SettingsDropdown(
                            title = stringResource(R.string.language),
                            description = stringResource(R.string.language_desc),
                            currentValue = languageLabels.getOrElse(currentLanguage) {
                                settings.language.endonym.ifEmpty { systemLanguageLabel }
                            },
                            options = languageLabels,
                            onOptionSelected = { index ->
                                languages.getOrNull(index)?.let { viewModel.updateLanguage(it) }
                            }
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(R.string.theme)) {
                        SettingsDropdown(
                            title = stringResource(R.string.theme_mode),
                            description = stringResource(R.string.choose_app_theme),
                            currentValue = when (settings.theme) {
                                ThemeMode.System -> stringResource(R.string.system_default)
                                ThemeMode.Light -> stringResource(R.string.light)
                                ThemeMode.Dark -> stringResource(R.string.dark)
                            },
                            options = listOf(
                                stringResource(R.string.system_default),
                                stringResource(R.string.light),
                                stringResource(R.string.dark)
                            ),
                            onOptionSelected = { index ->
                                viewModel.updateTheme(ThemeMode.entries.getOrElse(index) { ThemeMode.System })
                            }
                        )

                        SettingsToggle(
                            title = stringResource(R.string.show_banners_icons),
                            description = stringResource(R.string.display_app_banners_icons),
                            isChecked = settings.showAppIcons,
                            onCheckedChange = { viewModel.updateShowAppIcons(it) }
                        )

                        SettingsToggle(
                            title = stringResource(R.string.show_app_names),
                            description = stringResource(R.string.show_labels_under_app_icons),
                            isChecked = settings.showAppNames,
                            onCheckedChange = { viewModel.updateShowAppNames(it) }
                        )

                        SettingsDropdown(
                            title = stringResource(R.string.text_size),
                            description = stringResource(R.string.scale_text_across_launcher),
                            currentValue = when {
                                settings.textSizeScale <= 0.9f -> stringResource(R.string.text_size_small)
                                settings.textSizeScale >= 1.25f -> stringResource(R.string.text_size_extra_large)
                                settings.textSizeScale > 1.05f -> stringResource(R.string.text_size_large)
                                else -> stringResource(R.string.text_size_normal)
                            },
                            options = listOf(
                                stringResource(R.string.text_size_small),
                                stringResource(R.string.text_size_normal),
                                stringResource(R.string.text_size_large),
                                stringResource(R.string.text_size_extra_large)
                            ),
                            onOptionSelected = { index ->
                                viewModel.updateTextSizeScale(
                                    when (index) {
                                        0 -> 0.85f
                                        2 -> 1.15f
                                        3 -> 1.3f
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
                    SettingsSection(title = stringResource(R.string.layout)) {
                        SettingsClickable(
                            title = stringResource(R.string.edit_favorites),
                            description = stringResource(R.string.edit_favorites_desc),
                            onClick = { /* Navigate to home and show hint */ }
                        )

                        SettingsDropdown(
                            title = stringResource(R.string.home_grid_rows),
                            description = stringResource(R.string.home_grid_rows_desc),
                            currentValue = settings.homeScreenRows.toString(),
                            options = (4..12).map { it.toString() },
                            onOptionSelected = { index ->
                                (4..12).elementAtOrNull(index)?.let { viewModel.updateHomeScreenRows(it) }
                            }
                        )

                        SettingsDropdown(
                            title = stringResource(R.string.home_grid_columns),
                            description = stringResource(R.string.home_grid_columns_desc),
                            currentValue = settings.homeScreenColumns.toString(),
                            options = (2..8).map { it.toString() },
                            onOptionSelected = { index ->
                                (2..8).elementAtOrNull(index)?.let { viewModel.updateHomeScreenColumns(it) }
                            }
                        )
                    }
                }
            }

            is SettingsCategory.AppDrawer -> {
                item {
                    SettingsSection(title = stringResource(R.string.sorting)) {
                        SettingsDropdown(
                            title = stringResource(R.string.sort_order),
                            description = stringResource(R.string.sort_order_desc),
                            currentValue = when (settings.sortOrder) {
                                SortOrder.AZ -> stringResource(R.string.sort_option_a_z)
                                SortOrder.ZA -> stringResource(R.string.sort_option_z_a)
                                SortOrder.Recent -> stringResource(R.string.recent_label)
                            },
                            options = listOf(
                                stringResource(R.string.sort_option_a_z),
                                stringResource(R.string.sort_option_z_a),
                                stringResource(R.string.recent_label)
                            ),
                            onOptionSelected = { index ->
                                viewModel.updateSortOrder(SortOrder.entries.getOrElse(index) { SortOrder.AZ })
                            }
                        )

                        SettingsToggle(
                            title = stringResource(R.string.show_system_apps),
                            description = stringResource(R.string.show_system_apps_desc),
                            isChecked = settings.showSystemApps,
                            onCheckedChange = { viewModel.updateShowSystemApps(it) }
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(R.string.search)) {
                        SettingsDropdown(
                            title = stringResource(R.string.search_type),
                            description = stringResource(R.string.search_type_desc),
                            currentValue = when (settings.searchType) {
                                SearchType.Contains -> stringResource(R.string.search_type_contains)
                                SearchType.Fuzzy -> stringResource(R.string.search_type_fuzzy)
                                SearchType.StartsWith -> stringResource(R.string.search_type_starts_with)
                                SearchType.Exact -> stringResource(R.string.search_type_exact)
                            },
                            options = listOf(
                                stringResource(R.string.search_type_contains),
                                stringResource(R.string.search_type_fuzzy),
                                stringResource(R.string.search_type_starts_with),
                                stringResource(R.string.search_type_exact)
                            ),
                            onOptionSelected = { index ->
                                viewModel.updateSearchType(
                                    SearchType.entries.getOrElse(index) { SearchType.Contains }
                                )
                            }
                        )

                        SettingsToggle(
                            title = stringResource(R.string.search_package_names),
                            description = stringResource(R.string.search_package_names_desc),
                            isChecked = settings.searchIncludePackageNames,
                            onCheckedChange = { viewModel.updateSearchIncludePackageNames(it) }
                        )

                        SettingsToggle(
                            title = stringResource(R.string.show_hidden_in_search),
                            description = stringResource(R.string.show_hidden_in_search_desc),
                            isChecked = settings.showHiddenAppsOnSearch,
                            onCheckedChange = { viewModel.updateShowHiddenAppsOnSearch(it) }
                        )

                        SettingsToggle(
                            title = stringResource(R.string.auto_open_single_result),
                            description = stringResource(R.string.auto_open_single_result_desc),
                            isChecked = settings.autoOpenFilteredApp,
                            onCheckedChange = { viewModel.updateAutoOpenFilteredApp(it) }
                        )

                        SettingsToggle(
                            title = stringResource(R.string.web_search_option),
                            description = stringResource(R.string.web_search_option_desc),
                            isChecked = settings.showWebSearchOption,
                            onCheckedChange = { viewModel.updateShowWebSearchOption(it) }
                        )
                    }
                }
            }

            is SettingsCategory.Behavior -> {
                item {
                    SettingsSection(title = stringResource(R.string.tv_options)) {
                        SettingsDropdown(
                            title = stringResource(R.string.non_tv_apps),
                            description = stringResource(R.string.non_tv_apps_desc),
                            currentValue = when (settings.showNonTvApps) {
                                NonTvApps.All -> stringResource(R.string.all_apps)
                                NonTvApps.TvOnly -> stringResource(R.string.tv_only)
                                NonTvApps.Auto -> stringResource(R.string.auto)
                            },
                            options = listOf(
                                stringResource(R.string.all_apps),
                                stringResource(R.string.auto),
                                stringResource(R.string.tv_only)
                            ),
                            onOptionSelected = { index ->
                                viewModel.updateShowNonTvApps(
                                    when (index) {
                                        0 -> NonTvApps.All
                                        1 -> NonTvApps.Auto
                                        else -> NonTvApps.TvOnly
                                    }
                                )
                            }
                        )

                        SettingsToggle(
                            title = stringResource(R.string.prefer_tv_launch),
                            description = stringResource(R.string.prefer_tv_launch_desc),
                            isChecked = settings.preferTvLaunch,
                            onCheckedChange = { viewModel.updatePreferTvLaunch(it) }
                        )

                        SettingsToggle(
                            title = stringResource(R.string.tv_inputs_row),
                            description = stringResource(R.string.tv_inputs_row_desc),
                            isChecked = settings.showTvInputs,
                            onCheckedChange = { viewModel.updateShowTvInputs(it) }
                        )

                        SettingsToggle(
                            title = stringResource(R.string.continue_watching),
                            description = stringResource(R.string.continue_watching_desc),
                            isChecked = settings.showWatchNext,
                            onCheckedChange = { viewModel.updateShowWatchNext(it) }
                        )

                        SettingsToggle(
                            title = stringResource(R.string.return_to_home),
                            description = stringResource(R.string.return_to_home_desc),
                            isChecked = settings.returnToHomeAfterApp,
                            onCheckedChange = { viewModel.updateReturnToHomeAfterApp(it) }
                        )

                        SettingsDropdown(
                            title = stringResource(R.string.default_screen),
                            description = stringResource(R.string.default_screen_desc),
                            currentValue = if (settings.defaultScreen == DefaultScreen.Apps) {
                                stringResource(R.string.section_apps)
                            } else {
                                stringResource(R.string.sidebar_home)
                            },
                            options = listOf(
                                stringResource(R.string.sidebar_home),
                                stringResource(R.string.section_apps)
                            ),
                            onOptionSelected = { index ->
                                viewModel.updateDefaultScreen(
                                    DefaultScreen.entries.getOrElse(index) { DefaultScreen.Home }
                                )
                            }
                        )
                    }
                }

                item {
                    SettingsSection(title = stringResource(R.string.settings_lock)) {
                        SettingsToggle(
                            title = stringResource(R.string.lock_settings),
                            description = stringResource(R.string.lock_settings_desc),
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
                            title = if (settings.settingsLockPin.isEmpty()) {
                                stringResource(R.string.set_pin)
                            } else {
                                stringResource(R.string.change_pin)
                            },
                            description = stringResource(R.string.change_pin_desc),
                            onClick = { viewModel.setShowLockDialog(true, true) }
                        )
                    }
                }
            }

            is SettingsCategory.About -> {
                item {
                    SettingsSection(title = stringResource(R.string.app_info)) {
                        SettingsInfo(
                            title = stringResource(R.string.version),
                            value = BuildConfig.VERSION_NAME
                        )
                        SettingsInfo(
                            title = stringResource(R.string.build),
                            value = BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }
                        )
                    }
                }

                item {
                    BackupRestoreSection(viewModel = viewModel)
                }

                item {
                    SettingsSection(title = stringResource(R.string.links)) {
                        SettingsClickable(
                            title = stringResource(R.string.source_code),
                            description = stringResource(R.string.source_code_desc),
                            onClick = {
                                context.openUrl(Constants.URL_DIZZIFY_GITHUB)
                            }
                        )
                        SettingsClickable(
                            title = stringResource(R.string.report_issue),
                            description = stringResource(R.string.report_issue_desc),
                            onClick = {
                                context.openUrl("${Constants.URL_DIZZIFY_GITHUB}/issues")
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun settingMeta(key: String) =
    LauncherSettingsSchema.fields.firstOrNull { it.meta?.key == key }?.meta

@Composable
private fun WallpaperSettingsSection(viewModel: LauncherViewModel) {
    val settings by viewModel.settings.collectAsState()

    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.setWallpaperImage(uri)
    }

    SettingsSection(title = stringResource(R.string.wallpaper)) {
        SettingsInfo(
            title = stringResource(R.string.home_background),
            value = if (settings.wallpaperPath.isNotBlank()) {
                stringResource(R.string.custom_image)
            } else {
                stringResource(R.string.wallpaper_default)
            },
        )

        SettingsClickable(
            title = stringResource(R.string.choose_wallpaper_image),
            description = stringResource(R.string.choose_wallpaper_image_desc),
            onClick = { wallpaperPicker.launch("image/*") },
        )

        if (settings.wallpaperPath.isNotBlank()) {
            SettingsClickable(
                title = stringResource(R.string.reset_wallpaper),
                description = stringResource(R.string.reset_wallpaper_desc),
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

    val weightNames = listOf(
        stringResource(R.string.font_weight_thin),
        stringResource(R.string.font_weight_light),
        stringResource(R.string.font_weight_normal),
        stringResource(R.string.font_weight_medium),
        stringResource(R.string.font_weight_bold),
        stringResource(R.string.font_weight_black)
    )
    val weightValues = TextWeight.entries

    SettingsSection(title = stringResource(R.string.font)) {
        SettingsInfo(
            title = stringResource(R.string.current_font),
            value = fontInfo?.let { (name, size) ->
                "$name (${size / 1024} KB)"
            } ?: stringResource(R.string.system_font)
        )

        SettingsToggle(
            title = stringResource(R.string.use_system_font),
            description = stringResource(R.string.use_system_font_desc),
            isChecked = settings.useSystemFont,
            onCheckedChange = { viewModel.updateUseSystemFont(it) }
        )

        SettingsDropdown(
            title = stringResource(R.string.font_weight),
            description = stringResource(R.string.font_weight_desc),
            currentValue = weightNames[settings.fontWeight.ordinal.coerceIn(weightNames.indices)],
            options = weightNames,
            onOptionSelected = { index ->
                viewModel.updateFontWeight(weightValues.getOrElse(index) { TextWeight.Normal })
            }
        )

        SettingsClickable(
            title = stringResource(R.string.choose_font_file),
            description = stringResource(R.string.choose_font_file_desc),
            onClick = { fontPicker.launch(arrayOf("font/*", "application/*", "*/*")) }
        )

        if (fontInfo != null) {
            SettingsClickable(
                title = stringResource(R.string.reset_to_system_font),
                description = stringResource(R.string.reset_to_system_font_desc),
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

    val stringProvider: StringResourceProvider = koinInject()
    val statusLabel = stringResource(R.string.status)

    SettingsSection(title = stringResource(R.string.backup_restore)) {
        SettingsClickable(
            title = stringResource(R.string.export_settings),
            description = stringResource(R.string.export_settings_desc),
            onClick = {
                viewModel.resetImportExportState()
                exportLauncher.launch("dizzify-backup.json")
            }
        )

        SettingsClickable(
            title = stringResource(R.string.import_settings),
            description = stringResource(R.string.import_settings_desc),
            onClick = {
                viewModel.resetImportExportState()
                importLauncher.launch(arrayOf("application/json"))
            }
        )

        when (val s = backupState) {
            ImportExportState.Idle -> Unit
            ImportExportState.Loading -> {
                SettingsInfo(title = statusLabel, value = stringResource(R.string.status_working))
            }
            ImportExportState.ExportSuccess -> {
                SettingsInfo(title = statusLabel, value = stringResource(R.string.status_backup_saved))
            }
            is ImportExportState.ImportSuccess -> {
                val importSummary = stringResource(
                    R.string.status_applied_skipped,
                    s.appliedCount,
                    s.skippedCount
                )
                val importSummaryErrors = stringResource(R.string.status_errors, s.errors.size)
                val summary = buildString {
                    append(importSummary)
                    if (s.errors.isNotEmpty()) append(importSummaryErrors)
                }
                SettingsInfo(title = statusLabel, value = summary)
                s.errors.take(3).forEach { (key, msg) ->
                    val label = settingMeta(key)?.resolvedTitle(stringProvider) ?: key
                    SettingsInfo(title = label, value = msg)
                }
            }
            is ImportExportState.Error -> {
                SettingsInfo(title = stringResource(R.string.error), value = s.message)
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
            .tvPointerClick(onClick = { onCheckedChange(!isChecked) })
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
            .tvPointerClick(onClick)
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
    onOptionSelected: (Int) -> Unit
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
            .tvPointerClick(onClick = { expanded = true })
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

            Box {
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

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(LauncherColors.DarkSurface)
                ) {
                    options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    color = if (option == currentValue) LauncherColors.AccentBlue else Color.White
                                )
                            },
                            onClick = {
                                onOptionSelected(index)
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
