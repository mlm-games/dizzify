@file:Suppress("unused")

package app.dizzify.helper

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import app.dizzify.R
import app.dizzify.data.AnimationConstants
import app.dizzify.data.AppModel
import app.dizzify.data.Constants
import app.dizzify.data.AnimationConfig
import app.dizzify.data.AppKey
import app.dizzify.settings.LauncherSettings
import app.dizzify.settings.LauncherState
import app.dizzify.settings.SortOrder
import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

private const val TAG = "LauncherUtils"


fun openSearch(context: Context, query: String = "") {
    try {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No web search handler, trying browser", e)
        runCatching {
            val encoded = android.net.Uri.encode(query.trim())
            val browser = Intent(
                Intent.ACTION_VIEW,
                "${Constants.URL_DUCK_SEARCH}${encoded}".toUri()
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(browser)
        }.onFailure { e2 ->
            Log.w(TAG, "No browser handler either", e2)
        }
    }
}

fun getLauncherVisibleProfiles(
    userManager: UserManager,
    launcherApps: LauncherApps
): List<UserHandle> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        launcherApps.profiles
    } else {
        userManager.userProfiles
    }
}

suspend fun getAppsList(
    context: Context,
    settingsRepo: SettingsRepository<LauncherSettings>,
    stateRepo: SettingsRepository<LauncherState>,
    iconCache: IconCache,
    includeRegularApps: Boolean = true,
    includeHiddenApps: Boolean = false,
    filterTvApps: Boolean = true // When true, only show TV apps
): MutableList<AppModel> = withContext(Dispatchers.IO) {

    val appList: MutableList<AppModel> = mutableListOf()

    try {
        val appContext = context.applicationContext
        val settings = settingsRepo.flow.first()
        val state = stateRepo.flow.first()

        val hiddenApps = state.hiddenApps
        val renamedApps = state.renamedApps
        val recentHistory = state.recentAppHistory

        val includeIcons = settings.showAppIcons
        val selectedIconPack = settings.iconPack
        val showSystemApps = try { settings.showSystemApps } catch (_: Exception) { true }

        val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
        val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val packageManager = appContext.packageManager
        val collator = Collator.getInstance()
        val myUser = android.os.Process.myUserHandle()

        val profiles = getLauncherVisibleProfiles(userManager, launcherApps)

        for (profile in profiles) {
            // Skip locked private-space profiles
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val userType = runCatching { launcherApps.getLauncherUserInfo(profile)?.userType }.getOrNull()
                if (userType == UserManager.USER_TYPE_PROFILE_PRIVATE &&
                    userManager.isQuietModeEnabled(profile)
                ) {
                    continue
                }
            }

            val activitiesByPackage = runCatching { launcherApps.getActivityList(null, profile) }
                .getOrNull().orEmpty()
                .groupBy { it.applicationInfo.packageName }

            for ((pkg, activities) in activitiesByPackage) {
                if (pkg == appContext.packageName) continue

                if (!showSystemApps && AppLaunchResolver.isSystemApp(packageManager, pkg)) continue

                val leanbackComponent = AppLaunchResolver.leanbackComponent(packageManager, pkg)
                val mobileComponent = AppLaunchResolver.mobileComponent(packageManager, pkg)
                val leanbackClasses = AppLaunchResolver.leanbackClassNames(packageManager, pkg)

                val groupClasses = activities.map { it.componentName.className }.toSet()

                val resolvedLeanbackClass: String? = when {
                    leanbackComponent != null &&
                        (groupClasses.contains(leanbackComponent.className) || leanbackClasses.contains(leanbackComponent.className)) ->
                        leanbackComponent.className
                    else -> activities.firstOrNull { leanbackClasses.contains(it.componentName.className) }
                        ?.componentName?.className
                }

                val resolvedMobileClass: String? = when {
                    mobileComponent != null && groupClasses.contains(mobileComponent.className) &&
                        mobileComponent.className != resolvedLeanbackClass ->
                        mobileComponent.className
                    else -> activities.firstOrNull { it.componentName.className != resolvedLeanbackClass }
                        ?.componentName?.className
                        ?: activities.firstOrNull()?.componentName?.className
                }

                val hasLeanback = resolvedLeanbackClass != null
                if (filterTvApps && !hasLeanback) continue

                val displayClass = resolvedLeanbackClass ?: resolvedMobileClass ?: continue
                val displayActivity = activities.firstOrNull { it.componentName.className == displayClass }
                    ?: activities.first()

                val userString = profile.toString()
                val appKey = AppKey.of(pkg, userString)

                val defaultLabel = displayActivity.label.toString() +
                        if (profile != myUser) " (Clone)" else ""

                val shownLabel = renamedApps[appKey] ?: defaultLabel

                val appIcon = if (includeIcons) {
                    // Try to get TV banner first
                    val banner = getTvBanner(appContext, pkg)
                    if (banner != null) {
                        BitmapUtils.drawableToBitmap(banner)?.asImageBitmap()
                    } else {
                        iconCache.getIcon(
                            packageName = pkg,
                            className = displayClass,
                            user = profile,
                            iconPackName = selectedIconPack
                        )
                    }
                } else null

                val hasBanner = hasTvBanner(appContext, pkg)

                val model = AppModel(
                    appLabel = shownLabel,
                    key = collator.getCollationKey(displayActivity.label.toString()),
                    appPackage = pkg,
                    activityClassName = displayClass,
                    isNew = (System.currentTimeMillis() - displayActivity.firstInstallTime) < AnimationConstants.ONE_HOUR_IN_MILLIS,
                    user = profile,
                    appIcon = appIcon,
                    isHidden = hiddenApps.contains(appKey),
                    userString = userString,
                    lastLaunchTime = recentHistory[appKey] ?: 0L,
                    hasBanner = hasBanner,
                    leanbackActivityClassName = resolvedLeanbackClass,
                    mobileActivityClassName = resolvedMobileClass?.takeIf { it != resolvedLeanbackClass },
                )

                val isHidden = hiddenApps.contains(appKey)
                when {
                    isHidden && includeHiddenApps -> appList.add(model.copy(isHidden = true))
                    !isHidden && includeRegularApps -> appList.add(model)
                }
            }
        }

        when (settings.sortOrder) {
            SortOrder.Recent -> {
                appList.sortWith(
                    compareByDescending<AppModel> { it.lastLaunchTime }
                        .thenBy { it.appLabel.lowercase(Locale.ROOT) }
                )
            }
            SortOrder.ZA -> {
                appList.sortByDescending { it.appLabel.lowercase(Locale.ROOT) }
            }
            else -> {
                appList.sortBy { it.appLabel.lowercase(Locale.ROOT) }
            }
        }

    } catch (e: Exception) {
        Log.e(TAG, "getAppsList failed", e)
    }

    return@withContext appList
}

fun getTvBanner(context: Context, packageName: String): Drawable? {
    return try {
        val pm = context.applicationContext.packageManager
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        if (appInfo.banner != 0) {
            pm.getDrawable(packageName, appInfo.banner, appInfo)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

fun hasTvBanner(context: Context, packageName: String): Boolean {
    return try {
        val pm = context.applicationContext.packageManager
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        appInfo.banner != 0
    } catch (_: Exception) {
        false
    }
}

fun isPackageInstalled(context: Context, packageName: String, userString: String): Boolean {
    return try {
        val launcher = context.applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, getUserHandleFromString(context, userString))
        activityInfo.isNotEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "isPackageInstalled failed for $packageName", e)
        false
    }
}

fun getUserHandleFromString(context: Context, userHandleString: String): UserHandle {
    val appContext = context.applicationContext
    val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
    val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    for (userHandle in getLauncherVisibleProfiles(userManager, launcherApps)) {
        if (userHandle.toString() == userHandleString) {
            return userHandle
        }
    }
    return android.os.Process.myUserHandle()
}

fun setPlainWallpaperByTheme(context: Context, appTheme: Int) {
    when (appTheme) {
        AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(context, android.R.color.black)
        AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(context, android.R.color.white)
        else -> {
            if (context.isDarkThemeOn())
                setPlainWallpaper(context, android.R.color.black)
            else setPlainWallpaper(context, android.R.color.white)
        }
    }
}

/**
 * Plain-color wallpaper with guaranteed fallback for TV firmware without
 * WallpaperManager (see [WallpaperHelper]).
 */
fun setPlainWallpaper(context: Context, color: Int) {
    WallpaperHelper.applyPlain(context, context.getColor(color))
}

fun getChangedAppTheme(context: Context, currentAppTheme: Int): Int {
    return when (currentAppTheme) {
        AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
        else -> {
            if (context.isDarkThemeOn())
                AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES
        }
    }
}

fun openAppInfo(context: Context, userHandle: UserHandle, packageName: String) {
    // Leanback-first (TV): prefer TV settings entry, fall back to mobile + generic.
    val launcher = context.applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val component = AppLaunchResolver.leanbackComponent(context.packageManager, packageName)
        ?: AppLaunchResolver.mobileComponent(context.packageManager, packageName)
        ?: runCatching { launcher.getActivityList(packageName, userHandle).firstOrNull()?.componentName }.getOrNull()

    if (component != null) {
        try {
            launcher.startAppDetailsActivity(component, userHandle, null, null)
            return
        } catch (e: Exception) {
            Log.e(TAG, "openAppInfo failed", e)
        }
    }
    context.showToast(context.getString(R.string.unable_to_open_app))
}

fun getScreenDimensions(context: Context): Pair<Int, Int> {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        Pair(bounds.width(), bounds.height())
    } else {
        // Fallback for older versions
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val point = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(point)
        Pair(point.x, point.y)
    }
}


fun openSearch(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No web search handler", e)
        context.showToast(R.string.unable_to_open_app)
    }
}

@SuppressLint("WrongConstant")
fun expandNotificationDrawer(context: Context) {
    // expandNotificationsPanel() is hidden API — invoke it on the real
    // StatusBarManager service instance. No "statusbar" string lookup, and no
    // misleading fallback to notification-listener settings (removed).
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val statusBarManager =
                context.applicationContext.getSystemService(Context.STATUS_BAR_SERVICE)
            val method = statusBarManager.javaClass.getMethod("expandNotificationsPanel")
            method.invoke(statusBarManager)
            return
        }
    } catch (e: Exception) {
        Log.w(TAG, "expandNotificationsPanel failed", e)
    }
    Log.i(TAG, "expandNotificationDrawer not supported pre-R without accessibility service")
}

fun openAlarmApp(context: Context) {
    try {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No alarm app found", e)
        context.showToast(R.string.unable_to_open_app)
    }
}

fun openCalendar(context: Context) {
    try {
        val calendarUri = CalendarContract.CONTENT_URI
            .buildUpon()
            .appendPath("time")
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, calendarUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No calendar app found", e)
        context.showToast(R.string.unable_to_open_app)
    }
}

fun isTablet(context: Context): Boolean {
    return try {
        val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = context.resources.displayMetrics

        val (widthPixels, heightPixels) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }

        val widthInches = widthPixels / metrics.xdpi
        val heightInches = heightPixels / metrics.ydpi
        val diagonalInches = sqrt(widthInches.toDouble().pow(2.0) + heightInches.toDouble().pow(2.0))

        diagonalInches >= 7.0
    } catch (e: Exception) {
        Log.w(TAG, "isTablet check failed", e)
        false
    }
}


fun Context.isDarkThemeOn(): Boolean {
    return resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES
}

fun Context.copyToClipboard(text: String) {
    if (text.isBlank()) return
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(getString(R.string.app_name), text)
    clipboardManager.setPrimaryClip(clipData)
}

fun Context.openUrl(url: String) {
    if (url.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No browser for $url", e)
        showToast(R.string.unable_to_open_app)
    }
}

fun Context.isSystemApp(packageName: String): Boolean {
    if (packageName.isBlank()) return true
    return try {
        val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                || (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0))
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (e: Exception) {
        Log.w(TAG, "isSystemApp failed for $packageName", e)
        false
    }
}

fun Context.uninstall(packageName: String) {
    if (packageName.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_DELETE, "package:$packageName".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No uninstall handler", e)
        showToast(R.string.unable_to_open_app)
    }
}

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true,
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

fun View.animateAlpha(alpha: Float = 1.0f) {
    this.animate().apply {
        interpolator = LinearInterpolator()
        duration = AnimationConfig.SUB_QUICK.toLong()
        alpha(alpha)
        start()
    }
}

fun Context.shareApp() {
    try {
        val message = getString(R.string.are_you_using_your_phone_or_is_your_phone_using_you) +
                "\n" + Constants.URL_DIZZIFY_GITHUB
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val shareIntent = Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No share handler", e)
    }
}

fun Context.starApp() {
    try {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Constants.URL_DIZZIFY_GITHUB.toUri()
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No browser for star link", e)
        showToast(R.string.unable_to_open_app)
    }
}

fun AppModel.resolveUser(context: Context): UserHandle =
    getUserHandleFromString(context, userString)

fun AppModel.withResolvedUser(context: Context): AppModel =
    copy(user = resolveUser(context))
