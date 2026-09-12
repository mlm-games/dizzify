package app.dizzify.helper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import co.touchlab.kermit.Logger

/**
 * Voice search via the system's speech recognizer (delegated intent).
 *
 * No RECORD_AUDIO permission needed — recognition runs in the handler app
 * (Google/Katniss). Pattern from browkorf-tv VoiceSearchHelper, trimmed to the
 * launcher use-case: build intent, guard availability, parse result.
 *
 * Wiring: `rememberLauncherForActivityResult(StartActivityForResult())` feeds
 * the result into [parseResult]; set it as the query.
 */
object VoiceSearch {
    private const val TAG = "VoiceSearch"

    fun isAvailable(context: Context): Boolean =
        runCatching {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(
                context.packageManager,
            ) != null
        }.onFailure { e -> Logger.e(e) { "VoiceSearch availability check failed" } }
            .getOrDefault(false)

    fun intent(prompt: String? = null): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH,
            )
            if (!prompt.isNullOrBlank()) putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }

    fun parseResult(data: Intent?): String? = runCatching {
        val matches: ArrayList<String>? =
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        matches?.firstOrNull()?.takeIf { it.isNotBlank() }
    }.onFailure { e -> Logger.e(e) { "VoiceSearch.parseResult failed" } }.getOrNull()

    fun firstMatch(vararg matches: String): String? =
        matches.firstOrNull()?.takeIf { it.isNotBlank() }
}
