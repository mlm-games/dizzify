package app.dizzify.data

import android.os.UserHandle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.text.CollationKey

@Serializable
@Immutable
data class AppModel(
    val appLabel: String,
    @Transient
    val key: CollationKey? = null,
    val appPackage: String,
    val activityClassName: String?,
    val isNew: Boolean = false,
    @Transient
    val user: UserHandle = android.os.Process.myUserHandle(),
    @Transient
    val appIcon: ImageBitmap? = null,
    val isHidden: Boolean = false,
    val userString: String = user.toString(),
    @Transient
    val lastLaunchTime: Long = 0,
    val hasBanner: Boolean = false,
    val leanbackActivityClassName: String? = null,
    val mobileActivityClassName: String? = null,
    val isSystemShortcut: Boolean = false,
    val systemShortcutId: String? = null,
    val systemShortcutPackage: String? = null,
) : Comparable<AppModel> {
    val supportsLeanback: Boolean get() = !leanbackActivityClassName.isNullOrBlank()
    val supportsMobile: Boolean get() = !mobileActivityClassName.isNullOrBlank()
    val supportsBoth: Boolean get() = supportsLeanback && supportsMobile
    override fun compareTo(other: AppModel): Int = when {
        key != null && other.key != null -> key.compareTo(other.key)
        else -> appLabel.compareTo(other.appLabel, ignoreCase = true)
    }

    fun getKey(): String = if (isSystemShortcut) {
        AppKey.shortcutKey(systemShortcutPackage, systemShortcutId, userString)
    } else {
        AppKey.of(appPackage, userString)
    }
}


object AppKey {
    fun of(packageName: String, userString: String): String =
        "${packageName.trim()}/${userString.trim()}"

    fun shortcutKey(packageName: String?, shortcutId: String?, userString: String): String =
        "shortcut:${packageName.orEmpty().trim()}/${shortcutId.orEmpty().trim()}/${userString.trim()}"
}

/** How a specific app should be launched when it supports both TV and mobile entries. */
enum class AppLaunchMode {
    AUTO, TV, MOBILE;

    companion object {
        fun of(raw: String?): AppLaunchMode =
            runCatching { valueOf(raw ?: "AUTO") }.getOrDefault(AUTO)
    }
}