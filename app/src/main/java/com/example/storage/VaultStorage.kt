package com.example.storage

import android.content.Context
import com.example.model.DriveSyncMetadata
import com.example.model.LockoutState
import com.example.model.VaultFileEnvelope
import com.example.model.VaultPayload
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Local atomic storage for the encrypted vault envelope, brute-force lockout state,
 * 2FA session age, and Drive backup metadata.
 *
 * Security properties:
 * - Only ciphertext + wrapped DEKs + public account header ever touch disk.
 * - Writes use temp-file + atomic rename to avoid corruption on crash/power loss.
 * - Uses internal app-private directory ([Context.getFilesDir]).
 */
class VaultStorage(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val vaultFile = File(appContext.filesDir, VAULT_FILENAME)

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val envelopeAdapter = moshi.adapter(VaultFileEnvelope::class.java)
    private val payloadAdapter = moshi.adapter(VaultPayload::class.java)

    fun hasVault(): Boolean = vaultFile.exists() && vaultFile.length() > 0L

    fun readEnvelope(): VaultFileEnvelope? {
        if (!vaultFile.exists()) return null
        return try {
            val raw = vaultFile.readText(StandardCharsets.UTF_8)
            envelopeAdapter.fromJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun writeEnvelope(envelope: VaultFileEnvelope) {
        val json = envelopeAdapter.toJson(envelope)
        val tmp = File(appContext.filesDir, "$VAULT_FILENAME.tmp")
        tmp.writeText(json, StandardCharsets.UTF_8)
        if (!tmp.renameTo(vaultFile)) {
            vaultFile.writeText(json, StandardCharsets.UTF_8)
            tmp.delete()
        }
    }

    fun serializeEnvelope(envelope: VaultFileEnvelope): String = envelopeAdapter.toJson(envelope)

    fun deserializeEnvelope(json: String): VaultFileEnvelope? = try {
        envelopeAdapter.fromJson(json)
    } catch (_: Exception) {
        null
    }

    fun serializePayload(payload: VaultPayload): String = payloadAdapter.toJson(payload)

    fun deserializePayload(json: String): VaultPayload? = try {
        payloadAdapter.fromJson(json)
    } catch (_: Exception) {
        null
    }

    fun readLockoutState(): LockoutState = LockoutState(
        failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
        lockedUntilMs = prefs.getLong(KEY_LOCKED_UNTIL_MS, 0L)
    )

    fun writeLockoutState(state: LockoutState) {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, state.failedAttempts)
            .putLong(KEY_LOCKED_UNTIL_MS, state.lockedUntilMs)
            .apply()
    }

    fun resetLockout() {
        writeLockoutState(LockoutState(0, 0L))
    }

    fun readLastFull2faMs(): Long = prefs.getLong(KEY_LAST_FULL_2FA_MS, 0L)

    fun writeLastFull2faMs(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_FULL_2FA_MS, timestampMs).apply()
    }

    fun readDriveMetadata(): DriveSyncMetadata = DriveSyncMetadata(
        connectedEmail = prefs.getString(KEY_DRIVE_EMAIL, null),
        accessToken = null, // Access tokens stay in memory only, never persisted in plain prefs
        fileId = prefs.getString(KEY_DRIVE_FILE_ID, null),
        lastBackupIso = prefs.getString(KEY_DRIVE_LAST_BACKUP, null)
    )

    fun writeDriveMetadata(email: String?, fileId: String?, lastBackupIso: String?) {
        prefs.edit()
            .putString(KEY_DRIVE_EMAIL, email)
            .putString(KEY_DRIVE_FILE_ID, fileId)
            .putString(KEY_DRIVE_LAST_BACKUP, lastBackupIso)
            .apply()
    }

    fun readCachedThemeDark(): Boolean = prefs.getBoolean(KEY_THEME_DARK, true)

    fun writeCachedThemeDark(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_THEME_DARK, isDark).apply()
    }

    fun readCachedReduceMotion(): Boolean = prefs.getBoolean(KEY_REDUCE_MOTION, false)

    fun writeCachedReduceMotion(reduce: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, reduce).apply()
    }

    fun eraseAllLocalData() {
        if (vaultFile.exists()) {
            // Overwrite with zeros before unlinking
            try {
                val len = vaultFile.length().coerceAtMost(2_000_000L).toInt()
                if (len > 0) vaultFile.writeBytes(ByteArray(len))
            } catch (_: Exception) {
            }
            vaultFile.delete()
        }
        val tmp = File(appContext.filesDir, "$VAULT_FILENAME.tmp")
        if (tmp.exists()) tmp.delete()
        prefs.edit().clear().commit()
    }

    companion object {
        private const val PREFS_NAME = "dailylog_meta_prefs"
        private const val VAULT_FILENAME = "dailylog-vault.json"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKED_UNTIL_MS = "locked_until_ms"
        private const val KEY_LAST_FULL_2FA_MS = "last_full_2fa_ms"
        private const val KEY_DRIVE_EMAIL = "drive_email"
        private const val KEY_DRIVE_FILE_ID = "drive_file_id"
        private const val KEY_DRIVE_LAST_BACKUP = "drive_last_backup"
        private const val KEY_THEME_DARK = "ui_theme_dark"
        private const val KEY_REDUCE_MOTION = "ui_reduce_motion"
    }
}
