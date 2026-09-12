package app.dizzify

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dizzify.data.AppLaunchMode
import app.dizzify.data.AppModel
import app.dizzify.data.AppShortcut
import app.dizzify.data.Constants
import app.dizzify.data.HomeItem
import app.dizzify.data.HomeLayout
import app.dizzify.data.WidgetConstants
import app.dizzify.data.repository.AppRepository
import app.dizzify.helper.PermissionManager
import app.dizzify.helper.SearchAliasUtils
import app.dizzify.helper.getScreenDimensions
import app.dizzify.settings.ImportExportState
import app.dizzify.settings.LauncherBackupHelper
import app.dizzify.settings.LauncherSettings
import app.dizzify.settings.LauncherState
import app.dizzify.settings.LabelAlignment
import app.dizzify.settings.DefaultScreen
import app.dizzify.settings.ItemSpacing
import app.dizzify.settings.SearchAliasesMode
import app.dizzify.settings.SearchBarPosition
import app.dizzify.settings.SearchType
import app.dizzify.settings.SortOrder
import app.dizzify.settings.TextWeight
import app.dizzify.settings.ThemeMode
import app.dizzify.settings.clearCustomFont
import app.dizzify.settings.markLaunched
import app.dizzify.settings.setAppLaunchMode
import app.dizzify.settings.setCustomFont
import app.dizzify.settings.setCustomName
import app.dizzify.settings.setSettingsLock
import app.dizzify.settings.setSettingsLockPin
import app.dizzify.settings.validateSettingsPin
import app.dizzify.ui.LauncherEvent
import app.dizzify.ui.components.LauncherWidgetHost
import app.dizzify.ui.components.snackbar.SnackbarManager
import io.github.mlmgames.settings.core.SettingsRepository
import io.github.mlmgames.settings.core.backup.ImportResult as BackupImportResult
import io.github.mlmgames.settings.core.backup.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import co.touchlab.kermit.Logger
import java.io.File
import java.util.Locale
import kotlin.math.ceil

data class LauncherUiState(
    val query: String = "",
    val isLoading: Boolean = true
)

private const val MAX_VALIDATE_BYTES = 2 * 1024 * 1024L

class LauncherViewModel(
    app: Application,
    private val settingsRepo: SettingsRepository<LauncherSettings>,
    private val stateRepo: SettingsRepository<LauncherState>,
    private val appRepository: AppRepository,
    private val snackbarManager: SnackbarManager,
    private val widgetHost: LauncherWidgetHost,
    private val backupHelper: LauncherBackupHelper,
) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val appWidgetManager = AppWidgetManager.getInstance(app.applicationContext)

    private val _events = MutableSharedFlow<LauncherEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<LauncherEvent> = _events.asSharedFlow()

    fun emitEvent(event: LauncherEvent) {
        _events.tryEmit(event)
    }

    private data class PendingWidgetInfo(
        val appWidgetId: Int,
        val providerInfo: AppWidgetProviderInfo
    )

    private var pendingWidgetInfo: PendingWidgetInfo? = null

    private val launcherAppsService =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private var reloadJob: Job? = null

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) =
            requestReload("packageRemoved:$packageName")

        override fun onPackageAdded(packageName: String?, user: UserHandle?) =
            requestReload("packageAdded:$packageName")

        override fun onPackageChanged(packageName: String?, user: UserHandle?) =
            requestReload("packageChanged:$packageName")

        override fun onPackagesAvailable(
            packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean
        ) = requestReload("packagesAvailable")

        override fun onPackagesUnavailable(
            packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean
        ) = requestReload("packagesUnavailable")

        override fun onShortcutsChanged(
            packageName: String, shortcuts: List<ShortcutInfo>, user: UserHandle
        ) = requestReload("shortcutsChanged:$packageName")
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_REFRESH_APPS) {
                requestReload("broadcast", forceEmit = true)
            }
        }
    }

    private fun requestReload(reason: String, forceEmit: Boolean = false) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250)
            runCatching {
                appRepository.loadApps(forceEmit = forceEmit)
                appRepository.loadHiddenApps()
            }.onFailure { e -> Logger.e(e) { "Reload failed ($reason)" } }
        }
    }

    override fun onCleared() {
        runCatching { launcherAppsService.unregisterCallback(launcherAppsCallback) }
        runCatching { context.unregisterReceiver(refreshReceiver) }
        super.onCleared()
    }

    private val _ui = MutableStateFlow(LauncherUiState())
    val ui: StateFlow<LauncherUiState> = _ui.asStateFlow()

    val settings: StateFlow<LauncherSettings> =
        settingsRepo.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings())

    val state: StateFlow<LauncherState> =
        stateRepo.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherState())

    val apps: StateFlow<List<AppModel>> =
        appRepository.appList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appsAll: StateFlow<List<AppModel>> =
        appRepository.appListAll.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hiddenApps: StateFlow<List<AppModel>> =
        appRepository.hiddenApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteApps: StateFlow<Set<String>> =
        state.map { it.favoriteApps }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val homeLayout: StateFlow<HomeLayout> =
        state.map { it.homeLayout }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeLayout())

    private val aliasIndex = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            Logger.d { "Initializing LauncherViewModel - loading apps" }
            runCatching {
                appRepository.loadApps()
                appRepository.loadHiddenApps()
            }.onFailure { e ->
                Logger.e(e) { "Failed to load apps on init" }
            }
            _ui.update { it.copy(isLoading = false) }
        }

        // Live updates: installs, updates, removals, shortcut changes.
        runCatching {
            launcherAppsService.registerCallback(
                launcherAppsCallback, Handler(Looper.getMainLooper())
            )
        }.onFailure { e -> Logger.e(e) { "Failed to register LauncherApps callback" } }

        // Explicit refresh hook (shortcut pins, profile changes) — not exported.
        runCatching {
            val filter = IntentFilter(Constants.ACTION_REFRESH_APPS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(refreshReceiver, filter)
            }
        }.onFailure { e -> Logger.e(e) { "Failed to register refresh receiver" } }

        // Sync home-grid dimensions from settings (ported from CCLauncher).
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.flow
                .map { it.homeScreenRows to it.homeScreenColumns }
                .distinctUntilChanged()
                .collect { (rows, columns) ->
                    val current = state.value.homeLayout
                    if (current.rows != rows || current.columns != columns) {
                        runCatching {
                            stateRepo.update { s ->
                                s.copy(homeLayout = s.homeLayout.copy(rows = rows, columns = columns))
                            }
                        }.onFailure { e -> Logger.e(e) { "Failed to sync grid size" } }
                    }
                }
        }

        // Rebuild alias index when apps or search settings change
        viewModelScope.launch(Dispatchers.Default) {
            combine(
                appsAll,
                settingsRepo.flow
                    .map { it.searchAliasesMode to it.searchIncludePackageNames }
                    .distinctUntilChanged()
            ) { allApps, (mode, includePkg) ->
                if (mode == SearchAliasesMode.Off && !includePkg) {
                    emptyMap()
                } else {
                    buildMap(allApps.size) {
                        for (app in allApps) {
                            put(
                                app.getKey(),
                                SearchAliasUtils.buildAppAliases(
                                    label = app.appLabel,
                                    packageName = app.appPackage,
                                    mode = mode,
                                    includePkg = includePkg
                                )
                            )
                        }
                    }
                }
            }.collect { idx ->
                aliasIndex.value = idx
            }
        }
    }

    fun setQuery(q: String) {
        _ui.update { it.copy(query = q) }
    }

    val appsFiltered: StateFlow<List<AppModel>> =
        combine(
            appsAll,
            apps,
            ui.map { it.query }.distinctUntilChanged(),
            settingsRepo.flow,
            aliasIndex
        ) { allApps, visibleApps, query, settings, idx ->
            if (query.isBlank()) return@combine visibleApps

            val listToSearch = if (settings.showHiddenAppsOnSearch) allApps else visibleApps

            val mode = settings.searchAliasesMode
            val queryVariants = SearchAliasUtils.buildQueryVariants(query, mode)

            listToSearch.filter { app ->
                val labelNorm = SearchAliasUtils.normalize(app.appLabel)

                val direct = when (settings.searchType) {
                    SearchType.StartsWith ->
                        queryVariants.any { v -> labelNorm.startsWith(v) }
                    SearchType.Fuzzy ->
                        fuzzyMatch(labelNorm, query)
                    SearchType.Exact ->
                        queryVariants.any { v -> labelNorm == v }
                    else ->
                        queryVariants.any { v -> labelNorm.contains(v) }
                }
                if (direct) return@filter true

                val aliases = idx[app.getKey()].orEmpty()
                when (settings.searchType) {
                    SearchType.StartsWith ->
                        queryVariants.any { v -> aliases.any { it.startsWith(v) } }
                    SearchType.Exact ->
                        queryVariants.any { v -> aliases.any { it == v } }
                    else ->
                        queryVariants.any { v -> aliases.any { it.contains(v) } }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val homeApps: StateFlow<List<AppModel>> =
        combine(homeLayout, appsAll) { layout, allApps ->
            val byKey = allApps.associateBy { it.getKey() }
            layout.items.mapNotNull { item ->
                when (item) {
                    is HomeItem.App -> byKey[item.id] ?: item.appModel
                    else -> null
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentApps: StateFlow<List<AppModel>> =
        appsAll.map { list ->
            list.asSequence()
                .filter { it.lastLaunchTime > 0L }
                .sortedByDescending { it.lastLaunchTime }
                .take(12)
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun launch(app: AppModel, forceMode: AppLaunchMode = AppLaunchMode.AUTO) {
        viewModelScope.launch {
            runCatching { appRepository.launchApp(app, forceMode) }
                .onSuccess {
                    runCatching { stateRepo.markLaunched(app.getKey()) }
                    if (settings.value.returnToHomeAfterApp) emitEvent(LauncherEvent.NavigateHome)
                }
                .onFailure { e ->
                    Logger.e(e) { "Failed to launch ${app.appLabel}" }
                    snackbarManager.show("Couldn't open ${app.appLabel}")
                }
        }
    }

    fun launchInTvMode(app: AppModel) = launch(app, AppLaunchMode.TV)

    fun launchInMobileMode(app: AppModel) = launch(app, AppLaunchMode.MOBILE)

    fun toggleHidden(app: AppModel) {
        viewModelScope.launch {
            runCatching { appRepository.toggleAppHidden(app) }
        }
    }

    fun toggleFavorite(app: AppModel) {
        viewModelScope.launch {
            runCatching {
                stateRepo.update { state ->
                    val appKey = app.getKey()
                    if (appKey in state.favoriteApps) {
                        state.copy(favoriteApps = state.favoriteApps - appKey)
                    } else {
                        state.copy(favoriteApps = state.favoriteApps + appKey)
                    }
                }
            }.onFailure { e ->
                Logger.e(e) { "Failed to toggle favorite for ${app.appLabel}" }
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch(Dispatchers.IO) {
            Logger.d { "Refreshing apps" }
            _ui.update { it.copy(isLoading = true) }
            runCatching {
                appRepository.loadApps()
                appRepository.loadHiddenApps()
            }.onFailure { e ->
                Logger.e(e) { "Failed to refresh apps" }
            }
            _ui.update { it.copy(isLoading = false) }
        }
    }

    private fun fuzzyMatch(text: String, pattern: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        val p = pattern.lowercase(Locale.ROOT)
        var ti = 0
        var pi = 0
        while (ti < t.length && pi < p.length) {
            if (t[ti] == p[pi]) pi++
            ti++
        }
        return pi == p.length
    }

    // ---- Pinned shortcuts (ported from CCLauncher backend) ----

    fun getAppShortcuts(app: AppModel, onResult: (List<AppShortcut>) -> Unit) {
        viewModelScope.launch {
            val shortcuts = runCatching { appRepository.getAppShortcuts(app) }.getOrDefault(emptyList())
            onResult(shortcuts)
        }
    }

    fun launchShortcut(app: AppModel, shortcutId: String) {
        viewModelScope.launch {
            runCatching {
                appRepository.launchApp(
                    app.copy(
                        isSystemShortcut = true,
                        systemShortcutId = shortcutId,
                        systemShortcutPackage = app.appPackage
                    )
                )
            }.onSuccess {
                runCatching { stateRepo.markLaunched(app.getKey()) }
                if (settings.value.returnToHomeAfterApp) emitEvent(LauncherEvent.NavigateHome)
            }.onFailure { e ->
                Logger.e(e) { "Failed to open shortcut $shortcutId" }
                snackbarManager.show("Couldn't open shortcut")
            }
        }
    }

    fun pinShortcut(app: AppModel, shortcutId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val launcherApps = context.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE)
                    as android.content.pm.LauncherApps
                if (!launcherApps.hasShortcutHostPermission()) {
                    Logger.w { "Cannot pin shortcut: not default launcher" }
                    return@launch
                }
                val query = android.content.pm.LauncherApps.ShortcutQuery()
                    .setPackage(app.appPackage)
                    .setQueryFlags(android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                val pinnedIds = launcherApps.getShortcuts(query, app.user)
                    .orEmpty().mapNotNull { it.id }.toMutableSet()
                pinnedIds.add(shortcutId)
                launcherApps.pinShortcuts(app.appPackage, pinnedIds.toList(), app.user)
            }.onSuccess {
                runCatching {
                    appRepository.loadApps(forceEmit = true)
                }
            }.onFailure { e ->
                Logger.e(e) { "Failed to pin shortcut $shortcutId" }
            }
        }
    }

    fun deletePinnedShortcut(app: AppModel) {
        if (!app.isSystemShortcut) return
        viewModelScope.launch {
            runCatching {
                appRepository.deletePinnedShortcut(
                    packageName = app.systemShortcutPackage!!,
                    shortcutId = app.systemShortcutId!!,
                    user = app.user
                )
            }.onSuccess {
                runCatching { appRepository.loadApps(forceEmit = true) }
            }.onFailure { e ->
                Logger.e(e) { "Failed to delete shortcut" }
            }
        }
    }

    fun updateShowPinnedShortcuts(show: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(showPinnedShortcuts = show) }
        }
    }

    fun updateShowAppNames(show: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(showAppNames = show) } }
    }

    fun updateAutoOpenFilteredApp(auto: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(autoOpenFilteredApp = auto) } }
    }

    fun updateReturnToHomeAfterApp(returnHome: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(returnToHomeAfterApp = returnHome) } }
    }

    fun updateDefaultScreen(screen: DefaultScreen) {
        viewModelScope.launch { settingsRepo.update { it.copy(defaultScreen = screen) } }
    }

    fun updateShowWebSearchOption(show: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(showWebSearchOption = show) } }
    }

    fun updateTextSizeScale(scale: Float) {
        viewModelScope.launch { settingsRepo.update { it.copy(textSizeScale = scale) } }
    }

    fun updateAnimationSpeed(speed: Float) {
        viewModelScope.launch { settingsRepo.update { it.copy(animationSpeed = speed) } }
    }

    fun updateFontWeight(weight: TextWeight) {
        viewModelScope.launch { settingsRepo.update { it.copy(fontWeight = weight) } }
    }

    fun updateUseSystemFont(use: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(useSystemFont = use) } }
    }

    fun updateItemSpacing(spacing: ItemSpacing) {
        viewModelScope.launch { settingsRepo.update { it.copy(itemSpacing = spacing) } }
    }

    fun updateSearchResultsUseHomeFont(use: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(searchResultsUseHomeFont = use) } }
    }

    fun updateSearchResultsFontSize(scale: Float) {
        viewModelScope.launch { settingsRepo.update { it.copy(searchResultsFontSize = scale) } }
    }

    fun updateIconCornerRadius(radius: Int) {
        viewModelScope.launch { settingsRepo.update { it.copy(iconCornerRadius = radius) } }
    }

    fun updateTextColor(color: Int) {
        viewModelScope.launch { settingsRepo.update { it.copy(textColor = color, useCustomTextColor = true) } }
    }

    fun updateUseCustomTextColor(use: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(useCustomTextColor = use) } }
    }

    fun updateShowHomeScreenIcons(show: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(showHomeScreenIcons = show) } }
    }

    fun updateAppLabelAlignment(alignment: LabelAlignment) {
        viewModelScope.launch { settingsRepo.update { it.copy(appLabelAlignment = alignment) } }
    }

    fun updateSearchResultsAlignment(alignment: LabelAlignment) {
        viewModelScope.launch { settingsRepo.update { it.copy(searchResultsAlignment = alignment) } }
    }

    fun updateScaleHomeApps(scale: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(scaleHomeApps = scale) } }
    }

    fun updateHomeScreenRows(rows: Int) {
        viewModelScope.launch {
            val clamped = rows.coerceIn(4, 12)
            settingsRepo.update { it.copy(homeScreenRows = clamped) }
            clampLayoutToGrid()
        }
    }

    fun updateHomeScreenColumns(columns: Int) {
        viewModelScope.launch {
            val clamped = columns.coerceIn(2, 8)
            settingsRepo.update { it.copy(homeScreenColumns = clamped) }
            clampLayoutToGrid()
        }
    }

    private suspend fun clampLayoutToGrid() {
        val s = settingsRepo.flow.first()
        stateRepo.update { state ->
            val layout = state.homeLayout
            val fixed = layout.items.map { item ->
                val rowSpan = item.rowSpan.coerceAtMost(layout.rows.coerceAtLeast(1))
                val colSpan = item.columnSpan.coerceAtMost(layout.columns.coerceAtLeast(1))
                when (item) {
                    is HomeItem.App -> item.copy(
                        row = item.row.coerceIn(0, (s.homeScreenRows - rowSpan).coerceAtLeast(0)),
                        column = item.column.coerceIn(0, (s.homeScreenColumns - colSpan).coerceAtLeast(0)),
                        rowSpan = rowSpan,
                        columnSpan = colSpan
                    )
                    is HomeItem.Widget -> item.copy(
                        row = item.row.coerceIn(0, (s.homeScreenRows - rowSpan).coerceAtLeast(0)),
                        column = item.column.coerceIn(0, (s.homeScreenColumns - colSpan).coerceAtLeast(0)),
                        rowSpan = rowSpan,
                        columnSpan = colSpan
                    )
                }
            }
            state.copy(homeLayout = layout.copy(items = fixed))
        }
    }

    suspend fun willGridChangeAffectItems(rows: Int, columns: Int): Boolean {
        val layout = state.value.homeLayout
        return layout.items.any { item ->
            item.row + item.rowSpan > rows || item.column + item.columnSpan > columns
        }
    }

    fun updateSearchBarPosition(position: SearchBarPosition) {
        viewModelScope.launch { settingsRepo.update { it.copy(searchBarPosition = position) } }
    }

    fun updateReverseSearchResults(reverse: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(reverseSearchResults = reverse) } }
    }

    fun setFirstOpen(value: Boolean) {
        viewModelScope.launch {
            settingsRepo.update {
                it.copy(
                    firstOpen = value,
                    firstOpenTime = if (!value && it.firstOpenTime == 0L) {
                        System.currentTimeMillis()
                    } else {
                        it.firstOpenTime
                    }
                )
            }
        }
    }

    fun setFirstSettingsOpen(value: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(firstSettingsOpen = value) } }
    }

    fun setFirstHide(value: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(firstHide = value) } }
    }

    fun bumpHintCounter() {
        viewModelScope.launch { settingsRepo.update { it.copy(showHintCounter = it.showHintCounter + 1) } }
    }

    fun setAccessibilityConsent(consented: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(accessibilityConsent = consented) } }
    }
    // Repo holds the hash; the VM owns dialog visibility + temporary unlock.

    private val _showLockDialog = MutableStateFlow(false)
    val showLockDialog: StateFlow<Boolean> = _showLockDialog.asStateFlow()

    private val _isSettingPin = MutableStateFlow(false)
    val isSettingPin: StateFlow<Boolean> = _isSettingPin.asStateFlow()

    private val _isTemporarilyUnlocked = MutableStateFlow(false)

    val effectiveLockState: StateFlow<Boolean> =
        combine(
            settings.map { it.lockSettings }.distinctUntilChanged(),
            _isTemporarilyUnlocked
        ) { locked, tempUnlocked -> locked && !tempUnlocked }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setShowLockDialog(show: Boolean, isSettingPin: Boolean = false) {
        _showLockDialog.value = show
        _isSettingPin.value = isSettingPin
    }

    suspend fun validatePin(pin: String): Boolean = withContext(Dispatchers.Default) {
        val ok = settingsRepo.validateSettingsPin(pin)
        if (ok) {
            _isTemporarilyUnlocked.value = true
            _showLockDialog.value = false
        }
        ok
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            settingsRepo.setSettingsLockPin(pin)
        }
    }

    fun toggleLockSettings(locked: Boolean) {
        viewModelScope.launch {
            settingsRepo.setSettingsLock(locked)
            if (!locked) {
                settingsRepo.setSettingsLockPin("")
            }
        }
    }

    fun resetUnlockState() {
        _isTemporarilyUnlocked.value = false
    }

    val customFontInfo: StateFlow<Pair<String, Long>?> =
        settings.map { s ->
            val path = s.customFontPath
            if (path.isEmpty()) null
            else runCatching {
                File(path).takeIf { it.exists() }?.let { it.name to it.length() }
            }.getOrNull()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCustomFont(uri: Uri) {
        viewModelScope.launch {
            settingsRepo.setCustomFont(context, uri)
        }
    }

    fun clearCustomFont() {
        viewModelScope.launch {
            settingsRepo.clearCustomFont(context)
        }
    }

    private val _importExportState = MutableStateFlow<ImportExportState>(ImportExportState.Idle)
    val importExportState: StateFlow<ImportExportState> = _importExportState.asStateFlow()

    fun exportSettings(uri: Uri) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            _importExportState.value = backupHelper.exportSettingsToUri(uri).fold(
                onSuccess = { ImportExportState.ExportSuccess },
                onFailure = { ImportExportState.Error(it.message ?: "Failed to export settings") }
            )
        }
    }

    fun importSettings(uri: Uri) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            _importExportState.value = when (val result = backupHelper.importSettingsFromUri(uri)) {
                is BackupImportResult.Success -> ImportExportState.ImportSuccess(
                    appliedCount = result.appliedCount,
                    skippedCount = result.skippedCount,
                    errors = result.errors
                )
                is BackupImportResult.Error -> ImportExportState.Error(result.message)
            }
        }
    }

    fun validateBackup(uri: Uri): ValidationResult? {
        return try {
            var total = 0L
            val sb = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { input ->
                val reader = input.bufferedReader()
                val buf = CharArray(8 * 1024)
                while (true) {
                    val n = reader.read(buf)
                    if (n <= 0) break
                    total += n * 2L
                    if (total > MAX_VALIDATE_BYTES) return null
                    sb.append(buf, 0, n)
                }
            } ?: return null
            backupHelper.validateSettingsBackup(sb.toString())
        } catch (e: Exception) {
            Logger.w(e) { "validateBackup failed" }
            null
        }
    }

    fun resetImportExportState() {
        _importExportState.value = ImportExportState.Idle
    }

    fun lockScreen() {
        viewModelScope.launch {
            val pm = PermissionManager(context)
            if (!pm.hasAccessibilityPermission()) {
                Logger.w { "Lock requested but accessibility service not enabled" }
                return@launch
            }
            Logger.i { "Lock requested; accessibility enabled, awaiting bound service action" }
        }
    }

    fun updateTheme(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(theme = mode) }
        }
    }

    fun updateShowAppIcons(show: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(showAppIcons = show) }
        }
    }

    fun updateSortOrder(order: SortOrder) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(sortOrder = order) }
        }
    }

    fun updateSearchType(type: SearchType) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(searchType = type) }
        }
    }

    fun updateSearchAliasesMode(mode: SearchAliasesMode) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(searchAliasesMode = mode) }
        }
    }

    fun updateSearchIncludePackageNames(include: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(searchIncludePackageNames = include) }
        }
    }

    fun updateShowHiddenAppsOnSearch(show: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(showHiddenAppsOnSearch = show) }
        }
    }

    fun updateShowNonTvApps(show: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(showNonTvApps = show) }
        }
    }

    fun updatePreferTvLaunch(prefer: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(preferTvLaunch = prefer) }
        }
    }

    fun updateShowSystemApps(show: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(showSystemApps = show) }
        }
    }

    fun renameApp(app: AppModel, newName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = newName?.trim()
            val defaultLabel = runCatching { appRepository.getDefaultAppLabel(app) }.getOrNull()
            val effectiveName =
                if (trimmed.isNullOrBlank() || (defaultLabel != null && trimmed == defaultLabel)) {
                    null
                } else {
                    trimmed
                }
            runCatching { stateRepo.setCustomName(app.getKey(), effectiveName) }
                .onSuccess {
                    runCatching {
                        appRepository.loadApps()
                        appRepository.loadHiddenApps()
                    }
                }
                .onFailure { e ->
                    Logger.e(e) { "Failed to rename ${app.appLabel}" }
                }
        }
    }

    fun setAppLaunchMode(app: AppModel, mode: AppLaunchMode) {
        viewModelScope.launch {
            runCatching { stateRepo.setAppLaunchMode(app.getKey(), mode) }
                .onFailure { e ->
                    Logger.e(e) { "Failed to set launch mode for ${app.appLabel}" }
                }
        }
    }

    fun clearFromRecent(app: AppModel) {
        viewModelScope.launch {
            stateRepo.update { state ->
                val history = state.recentAppHistory.toMutableMap()
                history.remove(app.getKey())
                state.copy(recentAppHistory = history)
            }
        }
    }

    fun addWidget(
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
        row: Int = 0,
        column: Int = 0,
        rowSpan: Int = 2,
        columnSpan: Int = 2
    ) {
        viewModelScope.launch {
            val widget = HomeItem.Widget(
                appWidgetId = appWidgetId,
                providerInfo = providerInfo,
                packageName = providerInfo.provider.packageName,
                providerClassName = providerInfo.provider.className,
                row = row,
                column = column,
                rowSpan = rowSpan,
                columnSpan = columnSpan
            )

            stateRepo.update { state ->
                val currentLayout = state.homeLayout
                val newItems = currentLayout.items + widget
                state.copy(homeLayout = currentLayout.copy(items = newItems))
            }
        }
    }

    fun removeWidget(appWidgetId: Int) {
        viewModelScope.launch {
            stateRepo.update { state ->
                val currentLayout = state.homeLayout
                val newItems = currentLayout.items.filterNot { item ->
                    item is HomeItem.Widget && item.appWidgetId == appWidgetId
                }
                state.copy(homeLayout = currentLayout.copy(items = newItems))
            }
            // Release the host ID too, otherwise IDs leak (ported from CCLauncher).
            runCatching { widgetHost.deleteWidgetId(appWidgetId) }
        }
    }

    fun toggleHomeApp(app: AppModel) {
        viewModelScope.launch {
            stateRepo.update { state ->
                val currentLayout = state.homeLayout
                val appKey = app.getKey()

                val existingApp = currentLayout.items.filterIsInstance<HomeItem.App>()
                    .find { it.id == appKey }

                val newItems = if (existingApp != null) {
                    currentLayout.items.filter { it.id != appKey }
                } else {
                    val nextColumn = currentLayout.items
                        .filterIsInstance<HomeItem.App>()
                        .maxOfOrNull { it.column + it.columnSpan } ?: 0

                    currentLayout.items + HomeItem.App(
                        appModel = app,
                        row = 0,
                        column = nextColumn
                    )
                }

                state.copy(homeLayout = currentLayout.copy(items = newItems))
            }
        }
    }

    fun updateWidgetPosition(appWidgetId: Int, row: Int, column: Int) {
        viewModelScope.launch {
            val currentLayout = state.value.homeLayout
            val target = currentLayout.items.filterIsInstance<HomeItem.Widget>()
                .find { it.appWidgetId == appWidgetId } ?: return@launch
            if (!validateAndReport(
                    currentLayout, target.id, row, column,
                    target.rowSpan, target.columnSpan, "move widget"
                )) return@launch
            stateRepo.update { state ->
                val newItems = state.homeLayout.items.map { item ->
                    if (item is HomeItem.Widget && item.appWidgetId == appWidgetId) {
                        item.copy(row = row, column = column)
                    } else {
                        item
                    }
                }
                state.copy(homeLayout = state.homeLayout.copy(items = newItems))
            }
        }
    }

    fun updateWidgetSize(appWidgetId: Int, rowSpan: Int, columnSpan: Int) {
        viewModelScope.launch {
            val currentLayout = state.value.homeLayout
            val target = currentLayout.items.filterIsInstance<HomeItem.Widget>()
                .find { it.appWidgetId == appWidgetId } ?: return@launch
            if (!validateAndReport(
                    currentLayout, target.id, target.row, target.column,
                    rowSpan, columnSpan, "resize widget"
                )) return@launch
            stateRepo.update { state ->
                val newItems = state.homeLayout.items.map { item ->
                    if (item is HomeItem.Widget && item.appWidgetId == appWidgetId) {
                        item.copy(rowSpan = rowSpan, columnSpan = columnSpan)
                    } else {
                        item
                    }
                }
                state.copy(homeLayout = state.homeLayout.copy(items = newItems))
            }
            updateWidgetOptionsPixels(appWidgetId, rowSpan, columnSpan, currentLayout.rows, currentLayout.columns)
        }
    }
    // adapted: dizzify HomeLayout is single-page, so all placement is page 0) ----

    fun startWidgetConfiguration(providerInfo: AppWidgetProviderInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val componentName = providerInfo.provider
                    ?: run {
                        Logger.e { "Widget provider component name missing" }
                        snackbarManager.show("Internal error: widget component missing.")
                        return@launch
                    }
                val appWidgetId = widgetHost.allocateWidgetId()
                val bindSuccess = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, componentName)
                if (bindSuccess) {
                    if (providerInfo.configure != null) {
                        pendingWidgetInfo = PendingWidgetInfo(appWidgetId, providerInfo)
                        emitEvent(LauncherEvent.ConfigureWidget(appWidgetId))
                    } else {
                        addWidgetToLayout(appWidgetId, providerInfo)
                    }
                } else {
                    pendingWidgetInfo = PendingWidgetInfo(appWidgetId, providerInfo)
                    val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, componentName)
                    }
                    emitEvent(LauncherEvent.LaunchWidgetBindIntent(bindIntent))
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error in startWidgetConfiguration" }
                snackbarManager.show("Failed to add widget: ${e.message}")
            }
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != WidgetConstants.REQUEST_CONFIGURE_WIDGET) return
        val widgetId = pendingWidgetInfo?.appWidgetId
            ?: data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            pendingWidgetInfo = null
            return
        }
        if (resultCode == android.app.Activity.RESULT_OK) {
            pendingWidgetInfo?.let { info ->
                if (info.appWidgetId == widgetId) addWidgetToLayout(info.appWidgetId, info.providerInfo)
            }
        } else {
            Logger.w { "Widget configuration cancelled for ID $widgetId" }
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { widgetHost.deleteWidgetId(widgetId) }
            }
            snackbarManager.show("Widget configuration cancelled.")
        }
        pendingWidgetInfo = null
    }

    fun requestWidgetReconfigure(widgetItem: HomeItem.Widget) {
        val info = widgetHost.getWidgetInfo(widgetItem.appWidgetId)
        if (info?.configure == null) {
            snackbarManager.show("This widget has no settings screen.")
            return
        }
        pendingWidgetInfo = PendingWidgetInfo(widgetItem.appWidgetId, info)
        emitEvent(LauncherEvent.ConfigureWidget(widgetItem.appWidgetId))
    }

    private fun addWidgetToLayout(appWidgetId: Int, providerInfo: AppWidgetProviderInfo) {
        viewModelScope.launch {
            try {
                val density = context.resources.displayMetrics.density
                val (screenWidthPx, screenHeightPx) = getScreenDimensions(context)
                val currentLayout = state.value.homeLayout
                val cellWidthDp = (screenWidthPx / density) / currentLayout.columns
                val cellHeightDp = (screenHeightPx / density) / currentLayout.rows

                val widthCells = 1.coerceAtLeast(ceil(providerInfo.minWidth.toDouble() / cellWidthDp).toInt())
                val heightCells = 1.coerceAtLeast(ceil(providerInfo.minHeight.toDouble() / cellHeightDp).toInt())

                val nextPos = findNextAvailableGridPosition(currentLayout, widthCells, heightCells)
                if (nextPos == null) {
                    snackbarManager.show("No space available for widget on the home grid.")
                    runCatching { widgetHost.deleteWidgetId(appWidgetId) }
                    return@launch
                }

                val widgetItem = HomeItem.Widget(
                    appWidgetId = appWidgetId,
                    providerInfo = providerInfo,
                    packageName = providerInfo.provider.packageName,
                    providerClassName = providerInfo.provider.className,
                    row = nextPos.first,
                    column = nextPos.second,
                    rowSpan = heightCells,
                    columnSpan = widthCells
                )
                stateRepo.update { s ->
                    val items = s.homeLayout.items + widgetItem
                    s.copy(homeLayout = s.homeLayout.copy(items = items))
                }

                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, providerInfo.minWidth)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, providerInfo.minWidth)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, providerInfo.minHeight)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, providerInfo.minHeight)
                }
                runCatching { appWidgetManager.updateAppWidgetOptions(appWidgetId, options) }
            } catch (e: Exception) {
                Logger.e(e) { "Error adding widget to layout" }
                snackbarManager.show("Failed to add widget: ${e.message}")
                runCatching { widgetHost.deleteWidgetId(appWidgetId) }
            }
        }
    }

    fun moveApp(appItem: HomeItem.App, newRow: Int, newColumn: Int) {
        viewModelScope.launch {
            val currentLayout = state.value.homeLayout
            if (!validateAndReport(
                    currentLayout, appItem.id, newRow, newColumn,
                    appItem.rowSpan, appItem.columnSpan, "move app"
                )) return@launch
            stateRepo.update { s ->
                val updated = s.homeLayout.items.map { item ->
                    if (item.id == appItem.id && item is HomeItem.App) {
                        item.copy(row = newRow, column = newColumn)
                    } else item
                }
                s.copy(homeLayout = s.homeLayout.copy(items = updated))
            }
        }
    }

    fun moveWidget(widgetItem: HomeItem.Widget, newRow: Int, newColumn: Int) {
        viewModelScope.launch {
            val currentLayout = state.value.homeLayout
            if (!validateAndReport(
                    currentLayout, widgetItem.id, newRow, newColumn,
                    widgetItem.rowSpan, widgetItem.columnSpan, "move widget"
                )) return@launch
            stateRepo.update { s ->
                val updated = s.homeLayout.items.map { item ->
                    if (item.id == widgetItem.id && item is HomeItem.Widget) {
                        item.copy(row = newRow, column = newColumn)
                    } else item
                }
                s.copy(homeLayout = s.homeLayout.copy(items = updated))
            }
        }
    }

    fun resizeApp(appItem: HomeItem.App, newRowSpan: Int, newColumnSpan: Int) {
        viewModelScope.launch {
            val currentLayout = state.value.homeLayout
            if (!validateAndReport(
                    currentLayout, appItem.id, appItem.row, appItem.column,
                    newRowSpan, newColumnSpan, "resize app"
                )) return@launch
            stateRepo.update { s ->
                val updated = s.homeLayout.items.map { item ->
                    if (item.id == appItem.id && item is HomeItem.App) {
                        item.copy(rowSpan = newRowSpan, columnSpan = newColumnSpan)
                    } else item
                }
                s.copy(homeLayout = s.homeLayout.copy(items = updated))
            }
        }
    }

    fun resizeWidget(widgetItem: HomeItem.Widget, newRowSpan: Int, newColumnSpan: Int) {
        viewModelScope.launch {
            val currentLayout = state.value.homeLayout
            if (!validateAndReport(
                    currentLayout, widgetItem.id, widgetItem.row, widgetItem.column,
                    newRowSpan, newColumnSpan, "resize widget"
                )) return@launch
            stateRepo.update { s ->
                val updated = s.homeLayout.items.map { item ->
                    if (item.id == widgetItem.id && item is HomeItem.Widget) {
                        item.copy(rowSpan = newRowSpan, columnSpan = newColumnSpan)
                    } else item
                }
                s.copy(homeLayout = s.homeLayout.copy(items = updated))
            }
            updateWidgetOptionsPixels(
                widgetItem.appWidgetId, newRowSpan, newColumnSpan,
                currentLayout.rows, currentLayout.columns
            )
        }
    }

    private fun updateWidgetOptionsPixels(
        appWidgetId: Int, rowSpan: Int, columnSpan: Int, rows: Int, columns: Int
    ) {
        try {
            val density = context.resources.displayMetrics.density
            val (screenWidthPx, screenHeightPx) = getScreenDimensions(context)
            val cellWidthDp = (screenWidthPx / density) / columns
            val cellHeightDp = (screenHeightPx / density) / rows
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, (columnSpan * cellWidthDp).toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, (columnSpan * cellWidthDp).toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, (rowSpan * cellHeightDp).toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, (rowSpan * cellHeightDp).toInt())
            }
            appWidgetManager.updateAppWidgetOptions(appWidgetId, options)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to update widget options" }
        }
    }

    private fun findNextAvailableGridPosition(
        layout: HomeLayout, widthSpan: Int, heightSpan: Int
    ): Pair<Int, Int>? {
        val occupied = Array(layout.rows) { BooleanArray(layout.columns) }
        layout.items.forEach { item ->
            for (r in item.row until (item.row + item.rowSpan).coerceAtMost(layout.rows)) {
                for (c in item.column until (item.column + item.columnSpan).coerceAtMost(layout.columns)) {
                    if (r >= 0 && c >= 0) occupied[r][c] = true
                }
            }
        }
        for (r in 0..layout.rows - heightSpan) {
            for (c in 0..layout.columns - widthSpan) {
                if (isSpaceFree(occupied, r, c, widthSpan, heightSpan, layout.rows, layout.columns)) {
                    return Pair(r, c)
                }
            }
        }
        return null
    }

    private fun isSpaceFree(
        occupied: Array<BooleanArray>, startRow: Int, startCol: Int,
        spanW: Int, spanH: Int, maxRows: Int, maxCols: Int
    ): Boolean {
        for (r in startRow until startRow + spanH) {
            for (c in startCol until startCol + spanW) {
                if (r >= maxRows || c >= maxCols || occupied[r][c]) return false
            }
        }
        return true
    }

    private sealed class PlacementResult {
        object Valid : PlacementResult()
        data class Invalid(val reason: String) : PlacementResult()
    }

    private fun validatePlacement(
        layout: HomeLayout, itemId: String, row: Int, column: Int, rowSpan: Int, columnSpan: Int
    ): PlacementResult {
        if (row < 0 || column < 0) return PlacementResult.Invalid("Invalid position")
        if (row + rowSpan > layout.rows) return PlacementResult.Invalid("Would go out of bounds vertically")
        if (column + columnSpan > layout.columns) return PlacementResult.Invalid("Would go out of bounds horizontally")
        val hasOverlap = layout.items.any { item ->
            if (item.id == itemId) return@any false
            val itemEndRow = item.row + item.rowSpan
            val itemEndCol = item.column + item.columnSpan
            !(row >= itemEndRow || row + rowSpan <= item.row ||
                    column >= itemEndCol || column + columnSpan <= item.column)
        }
        return if (hasOverlap) PlacementResult.Invalid("Would overlap with other items")
        else PlacementResult.Valid
    }

    private fun validateAndReport(
        layout: HomeLayout, itemId: String, row: Int, column: Int,
        rowSpan: Int, columnSpan: Int, actionName: String
    ): Boolean {
        return when (val result = validatePlacement(layout, itemId, row, column, rowSpan, columnSpan)) {
            is PlacementResult.Valid -> true
            is PlacementResult.Invalid -> {
                snackbarManager.show("Cannot $actionName: ${result.reason}")
                false
            }
        }
    }
}
