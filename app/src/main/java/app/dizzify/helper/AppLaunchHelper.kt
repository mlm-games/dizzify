package app.dizzify.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import app.dizzify.R
import app.dizzify.data.AppLaunchMode
import app.dizzify.data.AppModel
import co.touchlab.kermit.Logger

object AppLaunchResolver {

    fun leanbackComponent(pm: PackageManager, packageName: String): ComponentName? {
        return try {
            pm.getLeanbackLaunchIntentForPackage(packageName)?.component
        } catch (_: Exception) {
            null
        }
    }

    fun mobileComponent(pm: PackageManager, packageName: String): ComponentName? {
        return try {
            pm.getLaunchIntentForPackage(packageName)?.component
        } catch (_: Exception) {
            null
        }
    }

    fun leanbackClassNames(pm: PackageManager, packageName: String): Set<String> {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                `package` = packageName
            }
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.name }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun hasLeanbackEntry(pm: PackageManager, packageName: String): Boolean {
        if (leanbackComponent(pm, packageName) != null) return true
        return leanbackClassNames(pm, packageName).isNotEmpty()
    }

    fun isSystemApp(pm: PackageManager, packageName: String): Boolean {
        return try {
            val ai = pm.getApplicationInfo(packageName, 0)
            (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) ||
                (ai.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0)
        } catch (_: Exception) {
            false
        }
    }

    fun openInPlayStore(context: Context, packageName: String) {
        try {
            val market = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("market://details?id=$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(market)
        } catch (_: Exception) {
            try {
                val web = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(web)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to open Play Store for $packageName" }
                context.showToast(context.getString(R.string.unable_to_open_app))
            }
        }
    }
}

sealed interface LaunchResult {
    data object Success : LaunchResult
    data class Failure(val message: String, val cause: Throwable? = null) : LaunchResult
}

fun resolveEffectiveLaunchMode(
    app: AppModel,
    override: AppLaunchMode,
    preferTvGlobally: Boolean,
): AppLaunchMode {
    if (!app.supportsBoth) return AppLaunchMode.AUTO
    return when (override) {
        AppLaunchMode.TV, AppLaunchMode.MOBILE -> override
        AppLaunchMode.AUTO -> if (preferTvGlobally) AppLaunchMode.TV else AppLaunchMode.MOBILE
    }
}

fun launchAppPreferringLeanback(
    context: Context,
    app: AppModel,
    preferTv: Boolean = true,
    forceMode: AppLaunchMode = AppLaunchMode.AUTO,
): LaunchResult {
    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val pm = context.packageManager
    val user: UserHandle = app.resolveUser(context)

    val requested: AppLaunchMode = when (forceMode) {
        AppLaunchMode.TV, AppLaunchMode.MOBILE -> forceMode
        AppLaunchMode.AUTO -> if (preferTv) AppLaunchMode.TV else AppLaunchMode.MOBILE
    }

    val tvComponent: ComponentName? = app.leanbackActivityClassName
        ?.takeIf { it.isNotBlank() }
        ?.let { ComponentName(app.appPackage, it) }
        ?: AppLaunchResolver.leanbackComponent(pm, app.appPackage)

    val mobileComponent: ComponentName? = app.mobileActivityClassName
        ?.takeIf { it.isNotBlank() }
        ?.let { ComponentName(app.appPackage, it) }
        ?: AppLaunchResolver.mobileComponent(pm, app.appPackage)
        ?: app.activityClassName?.takeIf { it.isNotBlank() }
            ?.let { ComponentName(app.appPackage, it) }

    val ordered = when (requested) {
        AppLaunchMode.MOBILE -> listOfNotNull(mobileComponent, tvComponent)
        else -> listOfNotNull(tvComponent, mobileComponent)
    }.distinct()

    for (component in ordered) {
        try {
            launcherApps.startMainActivity(component, user, null, null)
            return LaunchResult.Success
        } catch (_: Exception) {
        }
    }

    if (requested != AppLaunchMode.MOBILE) {
        try {
            val leanback = pm.getLeanbackLaunchIntentForPackage(app.appPackage)
            if (leanback != null) {
                leanback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(leanback)
                return LaunchResult.Success
            }
        } catch (_: Exception) { }
    }

    try {
        val normal = pm.getLaunchIntentForPackage(app.appPackage)
        if (normal != null) {
            normal.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(normal)
            return LaunchResult.Success
        }
    } catch (_: Exception) { }

    val msg = try {
        context.getString(R.string.unable_to_open_app)
    } catch (_: Exception) {
        "Unable to open ${app.appLabel}"
    }
    return LaunchResult.Failure(msg)
}
