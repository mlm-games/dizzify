package app.dizzify.platform.tv

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Build
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger

data class TvInputEntry(
    val inputId: String,
    val label: String,
    val type: Int,
    val typeName: String,
    val isPassthrough: Boolean,
    val switchIntentUri: String,
    val packageName: String?,
)

object TvInputs {
    private const val TAG = "TvInputs"

    fun isAvailable(context: Context): Boolean =
        ContextCompat.getSystemService(context, TvInputManager::class.java) != null

    fun list(context: Context, includeNonPassthrough: Boolean = true): List<TvInputEntry> {
        val manager =
            ContextCompat.getSystemService(context, TvInputManager::class.java) ?: return emptyList()
        return runCatching {
            manager.tvInputList
                .map { it.toEntry(context) }
                .filter { includeNonPassthrough || it.isPassthrough }
                .sortedWith(compareBy({ !it.isPassthrough }, { it.label.lowercase() }))
        }.onFailure { e -> Logger.e(e) { "TvInputs.list failed" } }.getOrDefault(emptyList())
    }

    fun switchIntent(entry: TvInputEntry): Intent =
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(entry.switchIntentUri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun switch(context: Context, entry: TvInputEntry): Boolean = runCatching {
        context.startActivity(switchIntent(entry))
        true
    }.onFailure { e -> Logger.e(e) { "TvInputs.switch failed: ${entry.inputId}" } }
        .getOrDefault(false)

    private fun TvInputInfo.toEntry(context: Context): TvInputEntry {
        val label = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() }
            } else null
        }.getOrNull() ?: runCatching { loadLabel(context)?.toString() }.getOrNull()
            ?: inputIdFallback()

        val switchUri = Intent(
            Intent.ACTION_VIEW,
            TvContract.buildChannelUriForPassthroughInput(id),
        ).toUri(0)

        return TvInputEntry(
            inputId = id,
            label = label,
            type = type,
            typeName = typeName(type),
            isPassthrough = isPassthroughInput,
            switchIntentUri = switchUri,
            packageName = runCatching { serviceInfo?.packageName }.getOrNull(),
        )
    }

    private fun TvInputInfo.inputIdFallback(): String =
        runCatching { id.substringAfterLast('/').replace('_', ' ') }.getOrDefault("TV Input")

    fun typeName(type: Int): String = when (type) {
        TvInputInfo.TYPE_TUNER -> "Tuner"
        TvInputInfo.TYPE_HDMI -> "HDMI"
        TvInputInfo.TYPE_COMPOSITE -> "Composite"
        TvInputInfo.TYPE_SVIDEO -> "S-Video"
        TvInputInfo.TYPE_SCART -> "SCART"
        TvInputInfo.TYPE_COMPONENT -> "Component"
        TvInputInfo.TYPE_VGA -> "VGA"
        TvInputInfo.TYPE_DVI -> "DVI"
        TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
        TvInputInfo.TYPE_OTHER -> "Other"
        else -> "Input"
    }
}
