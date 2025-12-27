package app.dizzify

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

data class LauncherUiState(
    val query: String = "",
    val isLoading: Boolean = true
)

/**
 * Main ViewModel for the Dizzify launcher.
 * 
 * Manages app lists, search functionality, favorites, and user settings.
 * Uses reactive StateFlows for UI state management.
 * 
 * @param app Application context
 * @param settingsRepo Repository for user settings (theme, search preferences, etc.)
 * @param stateRepo Repository for launcher state (hidden apps, favorites, recent history)
 * @param appRepository Repository for app data and operations
 */
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
            Timber.d("Initializing LauncherViewModel - loading apps")
            runCatching {
                appRepository.loadApps()
                appRepository.loadHiddenApps()
            }.onFailure { e ->
                Timber.e(e, "Failed to load apps on init")
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

    /**
     * Updates the search query and triggers app filtering.
     * 
     * @param q The search query string
     */
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

    /**
     * Launches the specified app.
     * 
     * @param app The app to launch
     */
    fun launch(app: AppModel) {
        viewModelScope.launch {
            runCatching { appRepository.launchApp(app) }
                .onSuccess {
                    runCatching { stateRepo.markLaunched(app.getKey()) }
                }
        }
    }

    /**
     * Toggles the hidden state of an app.
     * 
     * @param app The app to show/hide
     */
    fun toggleHidden(app: AppModel) {
        viewModelScope.launch {
            runCatching { appRepository.toggleAppHidden(app) }
        }
    }

    /**
     * Toggles the favorite state of an app.
     * 
     * @param app The app to mark/unmark as favorite
     */
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
                Timber.e(e, "Failed to toggle favorite for ${app.appLabel}")
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("Refreshing apps")
            _ui.update { it.copy(isLoading = true) }
            runCatching {
                appRepository.loadApps()
                appRepository.loadHiddenApps()
            }.onFailure { e ->
                Timber.e(e, "Failed to refresh apps")
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
}