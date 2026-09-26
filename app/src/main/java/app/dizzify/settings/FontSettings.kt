package app.dizzify.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import app.dizzify.data.Constants
import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "FontSettings"
private const val MAX_FONT_BYTES = 5 * 1024 * 1024L

suspend fun SettingsRepository<LauncherSettings>.setCustomFont(context: Context, uri: Uri) {
    withContext(Dispatchers.IO) {
        val fontFile = File(context.filesDir, Constants.CUSTOM_FONT_FILENAME)
        val temp = File(context.filesDir, "${Constants.CUSTOM_FONT_FILENAME}.tmp")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext
            // Stage into a temp file: throwing partway through (size cap, revoked grant, full
            // disk) must not leave a truncated file at the path the launcher is pointing at.
            var total = 0L
            input.use { source ->
                FileOutputStream(temp).use { output ->
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val n = source.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_FONT_BYTES) {
                            throw IllegalArgumentException("Font file larger than $MAX_FONT_BYTES bytes")
                        }
                        output.write(buf, 0, n)
                    }
                }
            }
            if (temp.length() == 0L) {
                Log.w(TAG, "Rejected empty font file")
                return@withContext
            }
            if (fontFile.exists() && !fontFile.delete()) {
                throw IllegalStateException("Could not replace the existing font")
            }
            if (!temp.renameTo(fontFile)) {
                throw IllegalStateException("Could not finalise the font file")
            }
            update {
                it.copy(customFontPath = fontFile.absolutePath, useSystemFont = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install font file", e)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}

suspend fun SettingsRepository<LauncherSettings>.clearCustomFont(context: Context) {
    withContext(Dispatchers.IO) {
        val currentPath = try { flow.first().customFontPath } catch (_: Exception) { "" }
        // customFontPath is persisted data that can arrive from an imported backup, so only
        // delete the file this app actually owns.
        val owned = File(context.filesDir, Constants.CUSTOM_FONT_FILENAME)
        if (currentPath.isNotEmpty() && File(currentPath).absolutePath == owned.absolutePath) {
            try {
                owned.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting old font file", e)
            }
        }
        update { it.copy(customFontPath = "", useSystemFont = true) }
    }
}
