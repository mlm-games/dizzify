package app.dizzify.helper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import co.touchlab.kermit.Logger
import java.io.File
import java.io.FileOutputStream

/**
 * Wallpaper with a guaranteed fallback.
 *
 * `WallpaperManager` is not implemented on some Android TV devices (see
 * FLauncher README: "changing wallpaper requires…" / custom wallpaper file).
 * Every mutating call here tries the system service first and falls back to an
 * app-private file + in-app background, so callers never have to branch.
 *
 * Storage layout (app-private, no permissions needed):
 * - `filesDir/wallpaper/custom.<ext>` — user-picked image (via [storeCustom])
 * - `filesDir/wallpaper/plain_fallback.png` — last plain color (via [applyPlain)
 */
object WallpaperHelper {
    private const val TAG = "WallpaperHelper"
    private const val DIR = "wallpaper"
    private const val PLAIN_FALLBACK = "plain_fallback.png"
    private const val CUSTOM = "custom_image"
    const val MAX_DIMENSION_PX = 1920

    sealed interface HomeBackground {
        data object Default : HomeBackground

        data class Image(val file: File) : HomeBackground

        data class Color(val argb: Int) : HomeBackground
    }

    fun resolveBackground(context: Context, wallpaperPath: String): HomeBackground {
        if (wallpaperPath.isBlank()) {
            val fallback = fallbackFile(context)
            if (fallback.exists()) return HomeBackground.Image(fallback)
            return HomeBackground.Default
        }
        val file = File(wallpaperPath)
        return if (file.exists()) HomeBackground.Image(file) else HomeBackground.Default
    }

    /**
     * Set a plain-color wallpaper. Tries the system service, and on failure (common
     * on TV firmware without WallpaperManager) persists the color as a fallback
     * file so the in-app background still reflects it. Returns the resulting
     * [HomeBackground].
     */
    fun applyPlain(context: Context, color: Int): HomeBackground {
        val appContext = context.applicationContext
        val systemOk = runCatching {
            val bitmap = createBitmap(100, 200)
            bitmap.eraseColor(color)
            val manager = WallpaperManager.getInstance(appContext)
            manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM)
            runCatching {
                manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_LOCK)
            }
            bitmap.recycle()
            true
        }.onFailure { e -> Logger.e(e) { "System wallpaper set failed, using fallback" } }
            .getOrDefault(false)
        if (systemOk) return HomeBackground.Default
        return runCatching {
            val file = fallbackFile(appContext)
            file.parentFile?.mkdirs()
            val bitmap = createBitmap(100, 200)
            bitmap.eraseColor(color)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            HomeBackground.Image(file)
        }.onFailure { e -> Logger.e(e) { "Wallpaper fallback write failed" } }
            .getOrDefault(HomeBackground.Color(color))
    }

    fun storeCustom(context: Context, uri: Uri): String? {
        val appContext = context.applicationContext
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val dir = File(appContext.filesDir, DIR).apply { mkdirs() }
            // Write to a temp file and swap: deleting the old wallpaper before the new one is
            // committed would leave the launcher pointing at a missing file if this throws.
            val file = File(dir, "$CUSTOM.png")
            val temp = File(dir, "$CUSTOM.png.tmp")
            try {
                FileOutputStream(temp).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                if (file.exists() && !file.delete()) {
                    error("Could not replace the existing wallpaper")
                }
                if (!temp.renameTo(file)) error("Could not finalise the wallpaper file")
            } finally {
                if (temp.exists()) temp.delete()
            }
            file.absolutePath
        }.onFailure { e -> Logger.e(e) { "storeCustom failed: $uri" } }.getOrNull()
    }

    fun clear(context: Context): Boolean {
        val dir = File(context.applicationContext.filesDir, DIR)
        return runCatching {
            dir.listFiles()?.forEach { it.delete() }
            true
        }.onFailure { e -> Logger.e(e) { "Wallpaper clear failed" } }.getOrDefault(false)
    }

    fun decodeFile(file: File, maxDimension: Int = MAX_DIMENSION_PX): ImageBitmap? {
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val opts = BitmapFactory.Options()
                .apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension) }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        }.onFailure { e -> Logger.e(e) { "decodeFile failed: ${file.name}" } }.getOrNull()
    }

    private fun fallbackFile(context: Context): File =
        File(File(context.applicationContext.filesDir, DIR), PLAIN_FALLBACK)

    private fun sampleSize(width: Int, height: Int, max: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / sample > max) sample *= 2
        return sample
    }
}
