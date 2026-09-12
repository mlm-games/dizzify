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

suspend fun SettingsRepository<LauncherSettings>.setCustomFont(context: Context, uri: Uri) =
    withContext(Dispatchers.IO) {
        try {
            val type = context.contentResolver.getType(uri)
            if (type != null && !type.startsWith("font/") &&
                !type.startsWith("application/") && !type.startsWith("text/")
            ) {
                Log.w(TAG, "Rejected font with unexpected mime type: $type")
                return@withContext
            }
            val fontFile = File(context.filesDir, Constants.CUSTOM_FONT_FILENAME)
            var total = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(fontFile).use { output ->
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_FONT_BYTES) throw IllegalArgumentException("Font file too large")
                        output.write(buf, 0, n)
                    }
                }
            } ?: return@withContext
            update { it.copy(customFontPath = fontFile.absolutePath, useSystemFont = false) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy font file", e)
        }
    }

suspend fun SettingsRepository<LauncherSettings>.clearCustomFont(context: Context) {
    val currentPath = try { flow.first().customFontPath } catch (_: Exception) { "" }
    if (currentPath.isNotEmpty()) {
        try {
            File(currentPath).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting old font file", e)
        }
    }
    update { it.copy(customFontPath = "", useSystemFont = true) }
}
