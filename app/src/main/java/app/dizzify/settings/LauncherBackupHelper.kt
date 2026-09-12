package app.dizzify.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.mlmgames.settings.core.backup.DeviceInfo
import io.github.mlmgames.settings.core.backup.ExportResult
import io.github.mlmgames.settings.core.backup.ImportError
import io.github.mlmgames.settings.core.backup.ImportOptions
import io.github.mlmgames.settings.core.backup.ImportResult
import io.github.mlmgames.settings.core.backup.SettingsBackupManager
import io.github.mlmgames.settings.core.backup.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "LauncherBackup"
private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024L

@Serializable
private data class CombinedBackup(
    val settings: String = "",
    val state: String = ""
)

sealed class ImportExportState {
    object Idle : ImportExportState()
    object Loading : ImportExportState()
    object ExportSuccess : ImportExportState()
    data class ImportSuccess(
        val appliedCount: Int,
        val skippedCount: Int,
        val errors: List<Pair<String, String>>
    ) : ImportExportState()

    data class Error(val message: String) : ImportExportState()
}

class LauncherBackupHelper(
    context: Context,
    private val settingsStore: DataStore<Preferences>,
    private val stateStore: DataStore<Preferences>
) {
    private val appContext = context.applicationContext

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun deviceInfo(): DeviceInfo {
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
        return DeviceInfo(
            platform = "Android",
            osVersion = "API ${Build.VERSION.SDK_INT}",
            appVersion = packageInfo?.versionName ?: "unknown"
        )
    }

    private val settingsBackup by lazy {
        SettingsBackupManager(
            dataStore = settingsStore,
            schema = LauncherSettingsSchema,
            appId = "app.dizzify",
            schemaVersion = schemaVersionOf<LauncherSettings>(),
            deviceInfoProvider = ::deviceInfo
        )
    }

    private val stateBackup by lazy {
        SettingsBackupManager(
            dataStore = stateStore,
            schema = LauncherStateSchema,
            appId = "app.dizzify",
            schemaVersion = schemaVersionOf<LauncherState>(),
            deviceInfoProvider = ::deviceInfo
        )
    }

    suspend fun exportSettings(): ExportResult {
        val settingsResult = settingsBackup.export()
        if (settingsResult is ExportResult.Error) return settingsResult
        val stateResult = stateBackup.export()
        if (stateResult is ExportResult.Error) return stateResult
        val combined = CombinedBackup(
            settings = (settingsResult as ExportResult.Success).json,
            state = (stateResult as ExportResult.Success).json
        )
        return ExportResult.Success(json.encodeToString(combined))
    }

    suspend fun importSettings(
        jsonString: String,
        options: ImportOptions = ImportOptions()
    ): ImportResult {
        return try {
            val combined = json.decodeFromString<CombinedBackup>(jsonString)
            var applied = 0
            var skipped = 0
            val errors = mutableListOf<Pair<String, String>>()
            if (combined.settings.isNotEmpty()) {
                when (val r = settingsBackup.import(combined.settings, options)) {
                    is ImportResult.Success -> {
                        applied += r.appliedCount
                        skipped += r.skippedCount
                        errors.addAll(r.errors)
                    }
                    is ImportResult.Error -> return r
                }
            }
            if (combined.state.isNotEmpty()) {
                when (val r = stateBackup.import(combined.state, options)) {
                    is ImportResult.Success -> {
                        applied += r.appliedCount
                        skipped += r.skippedCount
                        errors.addAll(r.errors)
                    }
                    is ImportResult.Error -> return r
                }
            }
            ImportResult.Success(applied, skipped, errors)
        } catch (e: Exception) {
            ImportResult.Error(ImportError.PARSE_ERROR, e.message ?: "Failed to parse settings")
        }
    }

    fun validateSettingsBackup(jsonString: String): ValidationResult {
        return try {
            val combined = json.decodeFromString<CombinedBackup>(jsonString)
            val issues = mutableListOf<String>()
            var count = 0
            var version = 0
            var exportedAt = 0L
            var deviceInfo: DeviceInfo? = null
            if (combined.settings.isNotEmpty()) {
                val v = settingsBackup.validate(combined.settings)
                issues.addAll(v.issues.map { "settings: $it" })
                count += v.settingsCount
                version = maxOf(version, v.schemaVersion)
                exportedAt = maxOf(exportedAt, v.exportedAt)
                deviceInfo = v.deviceInfo ?: deviceInfo
                if (!v.isValid) return ValidationResult(false, count, version, exportedAt, issues, deviceInfo)
            }
            if (combined.state.isNotEmpty()) {
                val v = stateBackup.validate(combined.state)
                issues.addAll(v.issues.map { "state: $it" })
                count += v.settingsCount
                version = maxOf(version, v.schemaVersion)
                exportedAt = maxOf(exportedAt, v.exportedAt)
                deviceInfo = v.deviceInfo ?: deviceInfo
                if (!v.isValid) return ValidationResult(false, count, version, exportedAt, issues, deviceInfo)
            }
            ValidationResult(true, count, version, exportedAt, issues, deviceInfo)
        } catch (e: Exception) {
            ValidationResult(false, 0, 0, 0, listOf("Parse error: ${e.message}"), null)
        }
    }

    suspend fun exportSettingsToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            when (val result = exportSettings()) {
                is ExportResult.Success -> {
                    appContext.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(result.json.toByteArray(Charsets.UTF_8))
                    } ?: return@withContext Result.failure(IllegalStateException("Cannot open output"))
                    Result.success(Unit)
                }
                is ExportResult.Error -> Result.failure(IllegalStateException(result.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export settings to URI", e)
            Result.failure(e)
        }
    }

    suspend fun importSettingsFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            var total = 0L
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val reader = input.bufferedReader()
                val buf = CharArray(8 * 1024)
                while (true) {
                    val n = reader.read(buf)
                    if (n <= 0) break
                    total += n * 2L
                    if (total > MAX_IMPORT_BYTES) {
                        return@withContext ImportResult.Error(ImportError.PARSE_ERROR, "Backup file too large")
                    }
                    sb.append(buf, 0, n)
                }
            } ?: return@withContext ImportResult.Error(ImportError.PARSE_ERROR, "Cannot open input")
            importSettings(sb.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import settings from URI", e)
            ImportResult.Error(ImportError.PARSE_ERROR, e.message ?: "Failed to import settings")
        }
    }
}
