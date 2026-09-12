package app.dizzify.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import app.dizzify.data.AppLaunchMode
import app.dizzify.data.AppModel
import app.dizzify.data.AppKey
import app.dizzify.data.AppShortcut
import app.dizzify.helper.AppLaunchResolver
import app.dizzify.helper.BitmapUtils
import app.dizzify.helper.IconCache
import app.dizzify.helper.LaunchResult
import app.dizzify.helper.PrivateSpaceHelper
import app.dizzify.helper.getAppsList
import app.dizzify.helper.getLauncherVisibleProfiles
import app.dizzify.helper.launchAppPreferringLeanback
import app.dizzify.settings.LauncherSettings
import app.dizzify.settings.LauncherState
import app.dizzify.settings.toggleHidden
import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val settingsRepo: SettingsRepository<LauncherSettings>,
    private val stateRepo: SettingsRepository<LauncherState>,
    private val iconCache: IconCache,
    private val privateSpaceHelper: PrivateSpaceHelper,
    coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AppRepository"
    }

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val packageManager = appContext.packageManager

    private val loadMutex = Mutex()

    private val _appListAll = MutableStateFlow<List<AppModel>>(emptyList())
    val appListAll: StateFlow<List<AppModel>> = _appListAll.asStateFlow()

    private val _appList = MutableStateFlow<List<AppModel>>(emptyList())
    val appList: StateFlow<List<AppModel>> = _appList.asStateFlow()

    private val _hiddenApps = MutableStateFlow<List<AppModel>>(emptyList())
    val hiddenApps: StateFlow<List<AppModel>> = _hiddenApps.asStateFlow()

    init {
        coroutineScope.launch {
            settingsRepo.flow
                .map {
                    listOf(
                        it.iconPack,
                        it.showAppIcons.toString(),
                        it.showNonTvApps.toString(),
                        try { it.showSystemApps.toString() } catch (_: Exception) { "true" },
                        it.showPinnedShortcuts.toString(),
                    )
                }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    loadApps(forceEmit = true)
                    loadHiddenApps()
                }
        }

        // Reload when hidden apps set changes
        coroutineScope.launch {
            stateRepo.flow
                .map { it.hiddenApps }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    loadApps()
                    loadHiddenApps()
                }
        }
    }

    private fun hasTvLauncherActivity(packageName: String): Boolean {
        return try {
            val tvIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                `package` = packageName
            }
            val tvActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    tvIntent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(tvIntent, 0)
            }
            tvActivities.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun getTvBanner(packageName: String): android.graphics.drawable.Drawable? {
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            if (appInfo.banner != 0) {
                packageManager.getDrawable(packageName, appInfo.banner, appInfo)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Single-scan load: one getAppsList() call, then partition visible/hidden.
     * Mutex is acquired inside IO (never held across dispatchers).
     * Emits only on visible change unless forceEmit (sameAppList guard).
     */
    suspend fun loadApps(forceEmit: Boolean = false) = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            try {
                val settings = settingsRepo.flow.first()
                val showNonTvApps = settings.showNonTvApps

                val combined = getAppsList(
                    context = appContext,
                    settingsRepo = settingsRepo,
                    stateRepo = stateRepo,
                    iconCache = iconCache,
                    includeRegularApps = true,
                    includeHiddenApps = true,
                    filterTvApps = !showNonTvApps
                )

                val visibleMobileApps = combined.filter { !it.isHidden }

                val systemShortcuts = if (settings.showPinnedShortcuts) {
                    loadSystemShortcuts()
                } else {
                    emptyList()
                }

                var finalVisible = visibleMobileApps + systemShortcuts
                var finalAll = combined + systemShortcuts

                if (privateSpaceHelper.isPrivateSpaceSupported() &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
                ) {
                    if (privateSpaceHelper.isPrivateSpaceLocked()) {
                        val privateSpaceUser = privateSpaceHelper.getPrivateSpaceUser()
                        if (privateSpaceUser != null) {
                            finalVisible = finalVisible.filter { it.user != privateSpaceUser }
                            finalAll = finalAll.filter { it.user != privateSpaceUser }
                        }
                    }
                }

                if (forceEmit || !sameAppList(_appList.value, finalVisible)) {
                    _appList.value = finalVisible
                }
                if (forceEmit || !sameAppList(_appListAll.value, finalAll)) {
                    _appListAll.value = finalAll
                }
                val hiddenOnly = combined.filter { it.isHidden }
                if (forceEmit || !sameAppList(_hiddenApps.value, hiddenOnly)) {
                    _hiddenApps.value = hiddenOnly
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadApps failed", e)
            }
        }
    }

    private fun List<AppModel>.uiSignature(): List<String> =
        map { app ->
            "${app.getKey()}|${app.appLabel}|${app.isHidden}|${app.lastLaunchTime}|${app.isSystemShortcut}"
        }

    private fun sameAppList(oldList: List<AppModel>, newList: List<AppModel>): Boolean =
        oldList.uiSignature() == newList.uiSignature()

    suspend fun loadHiddenApps() = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepo.flow.first()

            _hiddenApps.value = getAppsList(
                context = appContext,
                settingsRepo = settingsRepo,
                stateRepo = stateRepo,
                iconCache = iconCache,
                includeRegularApps = false,
                includeHiddenApps = true,
                filterTvApps = !settings.showNonTvApps
            )
        } catch (e: Exception) {
            Log.e(TAG, "loadHiddenApps failed", e)
        }
    }

    suspend fun toggleAppHidden(app: AppModel) = withContext(Dispatchers.IO) {
        val appKey = app.getKey()
        stateRepo.toggleHidden(appKey)
        loadApps(forceEmit = true)
        loadHiddenApps()
    }

    /**
     * Batch shortcut query per user profile (one IPC per profile instead of per shortcut).
     */
    private suspend fun queryPinnedShortcutsByUser(): Map<android.os.UserHandle, List<android.content.pm.ShortcutInfo>> =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return@withContext emptyMap()
            if (!launcherApps.hasShortcutHostPermission()) {
                Log.d(TAG, "No shortcut host permission (not default launcher?)")
                return@withContext emptyMap()
            }
            val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
            val out = mutableMapOf<android.os.UserHandle, List<android.content.pm.ShortcutInfo>>()
            for (user in getLauncherVisibleProfiles(userManager, launcherApps)) {
                try {
                    val query = LauncherApps.ShortcutQuery()
                    query.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                    out[user] = launcherApps.getShortcuts(query, user).orEmpty()
                } catch (_: SecurityException) {
                    // not the default launcher
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying shortcuts for user $user", e)
                }
            }
            out
        }

    private suspend fun loadSystemShortcuts(): List<AppModel> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return emptyList()
        val list = mutableListOf<AppModel>()
        try {
            val state = stateRepo.flow.first()
            for ((user, shortcuts) in queryPinnedShortcutsByUser()) {
                val userString = user.toString()
                for (shortcut in shortcuts) {
                    val label = shortcut.shortLabel?.toString() ?: shortcut.id
                    val shownLabel = state.renamedApps[
                        AppKey.shortcutKey(shortcut.`package`, shortcut.id, userString)
                    ] ?: label
                    val iconDrawable = runCatching {
                        launcherApps.getShortcutIconDrawable(
                            shortcut,
                            appContext.resources.displayMetrics.densityDpi
                        )
                    }.getOrNull()
                    val iconBitmap = BitmapUtils.drawableToBitmap(iconDrawable)?.asImageBitmap()
                    list.add(
                        AppModel(
                            appLabel = shownLabel,
                            appPackage = shortcut.`package`,
                            activityClassName = null,
                            user = user,
                            appIcon = iconBitmap,
                            userString = userString,
                            isSystemShortcut = true,
                            systemShortcutId = shortcut.id,
                            systemShortcutPackage = shortcut.`package`,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadSystemShortcuts failed", e)
        }
        return list
    }

    suspend fun getAppShortcuts(app: AppModel): List<app.dizzify.data.AppShortcut> =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return@withContext emptyList()
            if (app.isSystemShortcut) return@withContext emptyList()
            if (!launcherApps.hasShortcutHostPermission()) return@withContext emptyList()
            try {
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(app.appPackage)
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                    )
                launcherApps.getShortcuts(query, app.user).orEmpty().map { shortcut ->
                    val label = shortcut.shortLabel?.toString()
                        ?: shortcut.longLabel?.toString()
                        ?: shortcut.id
                    val iconDrawable = runCatching {
                        launcherApps.getShortcutIconDrawable(
                            shortcut,
                            appContext.resources.displayMetrics.densityDpi
                        )
                    }.getOrNull()
                    AppShortcut(
                        id = shortcut.id,
                        packageName = shortcut.`package`,
                        label = label,
                        user = app.user,
                        icon = BitmapUtils.drawableToBitmap(iconDrawable)?.asImageBitmap()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "getAppShortcuts failed for ${app.appPackage}", e)
                emptyList()
            }
        }

    /** Binder call — runs on IO. */
    suspend fun getDefaultAppLabel(app: AppModel): String? = withContext(Dispatchers.IO) {
        if (app.isSystemShortcut) return@withContext null
        try {
            runCatching { launcherApps.getActivityList(app.appPackage, app.user) }
                .getOrNull().orEmpty()
                .firstOrNull { it.componentName.className == app.activityClassName }
                ?.label?.toString()
                ?: runCatching { launcherApps.getActivityList(app.appPackage, app.user) }
                    .getOrNull().orEmpty().firstOrNull()?.label?.toString()
        } catch (e: Exception) {
            Log.w(TAG, "getDefaultAppLabel failed for ${app.appPackage}", e)
            null
        }
    }

    /** Binder call — runs on IO. */
    suspend fun deletePinnedShortcut(
        packageName: String,
        shortcutId: String,
        user: android.os.UserHandle
    ) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return@withContext
        if (!launcherApps.hasShortcutHostPermission()) {
            Log.w(TAG, "No shortcut host permission")
            return@withContext
        }
        try {
            val query = LauncherApps.ShortcutQuery()
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                .setPackage(packageName)
            val pinned = launcherApps.getShortcuts(query, user).orEmpty()
            val remainingIds = pinned.mapNotNull { it.id }.filter { it != shortcutId }
            launcherApps.pinShortcuts(packageName, remainingIds, user)
            Log.d(TAG, "Deleted pinned shortcut: $shortcutId from $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "deletePinnedShortcut failed", e)
            throw e
        }
    }

    suspend fun launchApp(
        appModel: AppModel,
        forceMode: AppLaunchMode = AppLaunchMode.AUTO,
    ) = withContext(Dispatchers.IO) {
        // Pinned shortcuts launch directly (no leanback/mobile resolution).
        if (appModel.isSystemShortcut) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
                throw AppLaunchException("Shortcuts require Android 7.1 or higher")
            }
            if (!launcherApps.hasShortcutHostPermission()) {
                throw AppLaunchException("Set Dizzify as the default launcher to open shortcuts")
            }
            val shortcutId = appModel.systemShortcutId
            val shortcutPackage = appModel.systemShortcutPackage
            if (shortcutId.isNullOrBlank() || shortcutPackage.isNullOrBlank()) {
                throw AppLaunchException("Shortcut not available")
            }
            try {
                launcherApps.startShortcut(shortcutPackage, shortcutId, null, null, appModel.user)
                return@withContext
            } catch (e: SecurityException) {
                throw AppLaunchException("Security error launching ${appModel.appLabel}", e)
            } catch (e: Exception) {
                throw AppLaunchException("Failed to launch ${appModel.appLabel}", e)
            }
        }

        val settings = try { settingsRepo.flow.first() } catch (_: Exception) { null }
        val preferTv = try { settings?.preferTvLaunch } catch (_: Exception) { true } ?: true

        val storedOverride = try {
            stateRepo.flow.first().appLaunchModes[appModel.getKey()] ?: AppLaunchMode.AUTO
        } catch (_: Exception) {
            AppLaunchMode.AUTO
        }
        val effectiveForce = if (forceMode != AppLaunchMode.AUTO) forceMode else storedOverride

        when (val result = launchAppPreferringLeanback(appContext, appModel, preferTv, effectiveForce)) {
            is LaunchResult.Success -> Unit
            is LaunchResult.Failure ->
                throw AppLaunchException(result.message, result.cause)
        }
    }

    class AppLaunchException(message: String, cause: Throwable? = null) : Exception(message, cause)
}
