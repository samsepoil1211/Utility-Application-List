package com.example.config

/**
 * Central configuration constants for the application.
 *
 * Naming separation:
 * - [APP_NAME_PUBLIC] ("Daily Log") is used everywhere outside the Today dashboard header:
 *   launcher label, onboarding, sign-in/sign-up, settings, notifications, errors, and backup file.
 * - [DASHBOARD_TITLE] ("QuitWeed") is rendered ONLY as the header on the Today screen.
 */
object AppConfig {
    const val APP_NAME_PUBLIC = "Daily Log"
    const val DASHBOARD_TITLE = "QuitWeed"

    const val DRIVE_BACKUP_FILENAME = "dailylog-vault.json"
    const val DRIVE_SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

    /**
     * Optional Google OAuth 2.0 Client ID for Google Drive appDataFolder backup.
     * Leave empty to operate 100% offline. To enable cloud backup, set your Google Cloud
     * OAuth Client ID authorized for the scope https://www.googleapis.com/auth/drive.appdata ONLY.
     * Google Drive only ever receives the end-to-end encrypted vault ciphertext.
     */
    const val DRIVE_OAUTH_CLIENT_ID = ""

    /**
     * Optional Firebase Authentication configuration for online email/phone identifier verification.
     * Leave empty to run purely offline; the local encrypted account works without verification.
     * Firebase never receives the Data Encryption Key (DEK), Recovery Key, or any health data.
     */
    data class FirebaseAuthConfig(
        val apiKey: String = "",
        val projectId: String = "",
        val appId: String = ""
    ) {
        val isConfigured: Boolean
            get() = apiKey.isNotBlank() && projectId.isNotBlank()
    }

    val FIREBASE_AUTH_CONFIG = FirebaseAuthConfig()

    const val VAULT_SCHEMA_VERSION = 1
    const val PBKDF2_ITERATIONS = 310_000
    const val AUTO_LOCK_BACKGROUND_MS = 60_000L
    const val FULL_2FA_MAX_AGE_MS = 24L * 60L * 60L * 1000L
}
