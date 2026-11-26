package com.example.model

data class WrappedKeyBlob(
    val salt: String,
    val iv: String,
    val ciphertext: String,
    val iterations: Int
)

data class AccountHeader(
    val identifier: String,
    val identifierType: String, // "email" or "phone"
    val createdAt: String,
    val passwordWrap: WrappedKeyBlob,
    val recoveryWrap: WrappedKeyBlob,
    val totpEnabled: Boolean = true,
    val onlineVerified: Boolean = false
)

data class EncryptedVaultBlob(
    val version: Int,
    val updatedAt: String,
    val iv: String,
    val ciphertext: String
)

/**
 * Envelope persisted locally and uploaded to Google Drive appDataFolder as "dailylog-vault.json".
 * Contains zero plaintext health records, passwords, DEKs, or recovery keys.
 */
data class VaultFileEnvelope(
    val schemaVersion: Int,
    val header: AccountHeader,
    val vault: EncryptedVaultBlob
)

data class BackupCodeHash(
    val salt: String,
    val hash: String,
    val used: Boolean = false
)

data class QuitPlan(
    val quitEpochMs: Long = 0L,
    val weeklySpend: Double = 0.0,
    val gramsPerDay: Double = 0.0,
    val currencyCode: String = "USD",
    val reason: String = ""
)

data class CravingLog(
    val id: String,
    val timestampMs: Long,
    val intensity: Int, // 1..10
    val trigger: String,
    val durationSeconds: Int,
    val resisted: Boolean
)

data class CheckInLog(
    val id: String,
    val dateKey: String, // yyyy-MM-dd
    val timestampMs: Long,
    val mood: Int, // 1..5
    val sleepHours: Double, // 0.0..14.0
    val cravingLevel: Int, // 0..10
    val usedToday: Boolean,
    val note: String
)

data class SlipLog(
    val id: String,
    val timestampMs: Long,
    val trigger: String,
    val note: String,
    val previousStreakStartMs: Long
)

data class ReminderPrefs(
    val enabled: Boolean = false,
    val dailyHour: Int = 20,
    val dailyMinute: Int = 30,
    val weeklySummary: Boolean = true,
    val peakHourHeadsUp: Boolean = true,
    val milestoneAlerts: Boolean = true
)

data class UiPrefs(
    val themeMode: String = "dark", // "dark" or "light"
    val reduceMotion: Boolean = false,
    val autoBackupDrive: Boolean = false
)

/**
 * Full decrypted user payload. Lives ONLY in memory while unlocked and is serialized +
 * encrypted with AES-256-GCM before touching storage or Google Drive.
 */
data class VaultPayload(
    val onboardingComplete: Boolean = false,
    val quitPlan: QuitPlan = QuitPlan(),
    val currentStreakStartMs: Long = 0L,
    val longestStreakMs: Long = 0L,
    val cravings: List<CravingLog> = emptyList(),
    val checkIns: List<CheckInLog> = emptyList(),
    val slips: List<SlipLog> = emptyList(),
    val celebratedMilestoneIds: List<String> = emptyList(),
    val totpSecret: String = "",
    val totpEnabled: Boolean = true,
    val backupCodes: List<BackupCodeHash> = emptyList(),
    val reminders: ReminderPrefs = ReminderPrefs(),
    val uiPrefs: UiPrefs = UiPrefs()
)

data class LockoutState(
    val failedAttempts: Int = 0,
    val lockedUntilMs: Long = 0L
)

data class DriveSyncMetadata(
    val connectedEmail: String? = null,
    val accessToken: String? = null,
    val fileId: String? = null,
    val lastBackupIso: String? = null
)

object CravingTriggers {
    val ALL = listOf(
        "Stress",
        "Boredom",
        "Social",
        "Evening routine",
        "Can't sleep",
        "Anger",
        "Loneliness",
        "Saw or smelled it",
        "Other"
    )
}

object Currencies {
    data class CurrencyOption(val code: String, val symbol: String, val label: String)

    val ALL = listOf(
        CurrencyOption("USD", "$", "USD ($)"),
        CurrencyOption("EUR", "€", "EUR (€)"),
        CurrencyOption("GBP", "£", "GBP (£)"),
        CurrencyOption("INR", "₹", "INR (₹)"),
        CurrencyOption("CAD", "C$", "CAD (C$)"),
        CurrencyOption("AUD", "A$", "AUD (A$)"),
        CurrencyOption("JPY", "¥", "JPY (¥)"),
        CurrencyOption("BRL", "R$", "BRL (R$)")
    )

    fun symbolFor(code: String): String =
        ALL.firstOrNull { it.code == code }?.symbol ?: "$"
}
