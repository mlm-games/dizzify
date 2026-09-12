package app.dizzify.platform.tv

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import android.os.Handler
import co.touchlab.kermit.Logger

/**
 * Reader for the system Watch-Next row ([TvContract.WatchNextPrograms]).
 */
data class WatchNextItem(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val progress: Float?,
    val posterUri: Uri?,
    val appLinkIntentUri: String?,
    val packageName: String?,
) {
    val hasProgress: Boolean get() = progress != null
}

object WatchNext {
    private const val TAG = "WatchNext"

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun contentUri(): Uri? =
        if (isSupported()) TvContract.WatchNextPrograms.CONTENT_URI else null

    /**
     * `COLUMN_APP_LINK_INTENT_URI` is hidden from the public SDK on some
     * platform versions — use the literal column name instead.
     */
    private const val COL_APP_LINK_INTENT_URI = "app_link_intent_uri"

    private val PROJECTION = if (isSupported()) arrayOf(
        TvContract.WatchNextPrograms._ID,
        TvContract.WatchNextPrograms.COLUMN_TITLE,
        TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
        TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
        TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
        TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
        TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
        COL_APP_LINK_INTENT_URI,
        TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
        TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
    ) else emptyArray()

    fun query(context: Context, limit: Int = 20): List<WatchNextItem> {
        val uri = contentUri() ?: return emptyList()
        if (PROJECTION.isEmpty()) return emptyList()
        return runCatching {
            val items = mutableListOf<WatchNextItem>()
            context.contentResolver.query(
                uri,
                PROJECTION,
                /* selection = */ null,
                /* selectionArgs = */ null,
                "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC",
            )?.use { cursor ->
                val idx = (0 until cursor.columnCount).associateBy { cursor.getColumnName(it) }
                fun s(name: String): String? =
                    idx[name]?.let { runCatching { cursor.getString(it) }.getOrNull() }

                fun l(name: String): Long? =
                    idx[name]?.let { runCatching { cursor.getLong(it) }.getOrNull() }

                fun i(name: String): Int? =
                    idx[name]?.let { runCatching { cursor.getInt(it) }.getOrNull() }

                while (cursor.moveToNext() && items.size < limit) {
                    val title = s(TvContract.WatchNextPrograms.COLUMN_TITLE).orEmpty()
                    if (title.isBlank()) continue
                    val episode = s(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE)
                    val season = i(TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER)
                    val episodeNo =
                        i(TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER)
                    val subtitle = buildSubtitle(episode, season, episodeNo)
                    val position =
                        l(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
                    val duration = l(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS)
                    val progress =
                        if (position != null && duration != null && duration > 0) {
                            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } else null
                    items += WatchNextItem(
                        id = l(TvContract.WatchNextPrograms._ID) ?: continue,
                        title = title,
                        subtitle = subtitle,
                        progress = progress,
                        posterUri = s(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { runCatching { Uri.parse(it) }.getOrNull() },
                        appLinkIntentUri =
                            s(COL_APP_LINK_INTENT_URI)?.takeIf { it.isNotBlank() },
                        packageName = s(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME)
                            ?.takeIf { it.isNotBlank() },
                    )
                }
            }
            items
        }.onFailure { e -> Logger.e(e) { "WatchNext.query failed" } }.getOrDefault(emptyList())
    }

    fun observe(
        contentResolver: ContentResolver,
        handler: Handler,
        onChange: () -> Unit,
    ): ContentObserver? {
        val uri = contentUri() ?: return null
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        return runCatching {
            contentResolver.registerContentObserver(uri, /* notifyForDescendants = */ true, observer)
            observer
        }.onFailure { e -> Logger.e(e) { "WatchNext.observe failed" } }.getOrNull()
    }

    fun unobserve(contentResolver: ContentResolver, observer: ContentObserver?) {
        if (observer == null) return
        runCatching { contentResolver.unregisterContentObserver(observer) }
    }

    fun play(context: Context, item: WatchNextItem): Boolean {
        val intent = item.appLinkIntentUri?.let {
            runCatching { Intent.parseUri(it, 0) }.getOrNull()
        }?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: item.packageName?.let {
                context.packageManager.getLaunchIntentForPackage(it)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
            ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.onFailure { e -> Logger.e(e) { "WatchNext.play failed: ${item.title}" } }
            .getOrDefault(false)
    }

    private fun buildSubtitle(episode: String?, season: Int?, episodeNo: Int?): String? {
        val number = when {
            season != null && episodeNo != null -> "S$season E$episodeNo"
            episodeNo != null -> "E$episodeNo"
            else -> null
        }
        return listOfNotNull(number, episode?.takeIf { it.isNotBlank() })
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
    }
}
