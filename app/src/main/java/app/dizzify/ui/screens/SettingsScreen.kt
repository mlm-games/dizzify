package app.dizzify.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
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
import app.dizzify.settings.SearchType
import app.dizzify.settings.SortOrder
import app.dizzify.settings.ThemeMode
import app.dizzify.ui.theme.*

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

    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }

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
                val effectiveCategory = selectedCategory ?: SettingsCategory.Appearance
                SettingsCategoryContent(
                    category = effectiveCategory,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
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
                    }
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
                            },
                            options = listOf("Contains", "Fuzzy", "Starts With"),
                            onOptionSelected = { selected ->
                                val type = when (selected) {
                                    "Contains" -> SearchType.Contains
                                    "Fuzzy" -> SearchType.Fuzzy
                                    "Starts With" -> SearchType.StartsWith
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