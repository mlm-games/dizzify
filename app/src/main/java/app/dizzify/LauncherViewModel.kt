package app.dizzify

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dizzify.data.AppLaunchMode
import app.dizzify.data.AppModel
import app.dizzify.data.HomeItem
import app.dizzify.data.HomeLayout
import app.dizzify.data.repository.AppRepository
import app.dizzify.helper.SearchAliasUtils
import app.dizzify.settings.LauncherSettings
import app.dizzify.settings.LauncherState
import app.dizzify.settings.SearchType
import app.dizzify.settings.SortOrder
import app.dizzify.settings.ThemeMode
import app.dizzify.settings.markLaunched
import app.dizzify.settings.setAppLaunchMode
import app.dizzify.settings.setCustomName
import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import co.touchlab.kermit.Logger

data class LauncherUiState(
    val query: String = "",
    val isLoading: Boolean = true
)

class LauncherViewModel(
    app: Application,
    private val settingsRepo: SettingsRepository<LauncherSettings>,
    private val stateRepo: SettingsRepository<LauncherState>,
    private val appRepository: AppRepository,
) : AndroidViewModel(app) {

    private val context = app.applicationContext

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

    private val homeLayout: StateFlow<HomeLayout> =
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

        // Rebuild alias index when apps or search settings change
        viewModelScope.launch(Dispatchers.Default) {
            combine(
                appsAll,
                settingsRepo.flow
                    .map { it.searchAliasesMode to it.searchIncludePackageNames }
                    .distinctUntilChanged()
            ) { allApps, (mode, includePkg) ->
                if (mode == SearchAliasUtils.Mode.OFF && !includePkg) {
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
                    else ->
                        queryVariants.any { v -> labelNorm.contains(v) }
                }
                if (direct) return@filter true

                val aliases = idx[app.getKey()].orEmpty()
                when (settings.searchType) {
                    SearchType.StartsWith ->
                        queryVariants.any { v -> aliases.any { it.startsWith(v) } }
                    SearchType.Fuzzy ->
                        queryVariants.any { v -> aliases.any { it.contains(v) } }
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
                }
                .onFailure { e ->
                    Logger.e(e) { "Failed to launch ${app.appLabel}" }
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
        val t = text.lowercase()
        val p = pattern.lowercase()
        var ti = 0
        var pi = 0
        while (ti < t.length && pi < p.length) {
            if (t[ti] == p[pi]) pi++
            ti++
        }
        return pi == p.length
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
            runCatching { stateRepo.setCustomName(app.getKey(), newName) }
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
            runCatching { stateRepo.setAppLaunchMode(app.getKey(), mode.name) }
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
            stateRepo.update { state ->
                val currentLayout = state.homeLayout
                val newItems = currentLayout.items.map { item ->
                    if (item is HomeItem.Widget && item.appWidgetId == appWidgetId) {
                        item.copy(row = row, column = column)
                    } else {
                        item
                    }
                }
                state.copy(homeLayout = currentLayout.copy(items = newItems))
            }
        }
    }

    fun updateWidgetSize(appWidgetId: Int, rowSpan: Int, columnSpan: Int) {
        viewModelScope.launch {
            stateRepo.update { state ->
                val currentLayout = state.homeLayout
                val newItems = currentLayout.items.map { item ->
                    if (item is HomeItem.Widget && item.appWidgetId == appWidgetId) {
                        item.copy(rowSpan = rowSpan, columnSpan = columnSpan)
                    } else {
                        item
                    }
                }
                state.copy(homeLayout = currentLayout.copy(items = newItems))
            }
        }
    }
}