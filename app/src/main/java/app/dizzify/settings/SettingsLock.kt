package app.dizzify.settings

import io.github.mlmgames.settings.core.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Settings-lock PIN helpers. The PIN is never stored in plaintext —
 * only a salted SHA-256 hash in "saltHex:hashHex" format.
 */
object SettingsLock {
    fun hashPin(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = sha256(salt + pin.toByteArray(Charsets.UTF_8))
        return "${salt.toHex()}:${hash.toHex()}"
    }

    fun verifyPin(pin: String, stored: String): Boolean {
        if (pin.isEmpty() || stored.isEmpty()) return false
        // Back-compat: very old installs stored plaintext; callers migrate on match.
        if (!stored.contains(":")) {
            return MessageDigest.isEqual(
                stored.toByteArray(Charsets.UTF_8),
                pin.toByteArray(Charsets.UTF_8)
            )
        }
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = parts[0].hexToBytes() ?: return false
        val expected = parts[1].hexToBytes() ?: return false
        val actual = sha256(salt + pin.toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(expected, actual)
    }

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? = runCatching {
        require(length % 2 == 0)
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }.getOrNull()
}

suspend fun SettingsRepository<LauncherSettings>.setSettingsLock(locked: Boolean) {
    update { it.copy(lockSettings = locked) }
}

suspend fun SettingsRepository<LauncherSettings>.setSettingsLockPin(pin: String) {
    if (pin.isEmpty()) {
        update { it.copy(settingsLockPin = "") }
        return
    }
    update { it.copy(settingsLockPin = SettingsLock.hashPin(pin)) }
}

suspend fun SettingsRepository<LauncherSettings>.validateSettingsPin(pin: String): Boolean =
    withContext(Dispatchers.Default) {
        if (pin.isEmpty()) return@withContext false
        val stored = flow.first().settingsLockPin
        if (stored.isEmpty()) return@withContext false
        val ok = SettingsLock.verifyPin(pin, stored)
        // Migrate legacy plaintext PINs to hashed form on successful match.
        if (ok && !stored.contains(":")) {
            setSettingsLockPin(pin)
        }
        ok
    }
