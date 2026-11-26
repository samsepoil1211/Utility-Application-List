package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthValidator
import com.example.auth.OnlineVerifier
import com.example.config.AppConfig
import com.example.crypto.BiometricKeyManager
import com.example.crypto.VaultCrypto
import javax.crypto.Cipher
import com.example.drive.DriveSyncManager
import com.example.model.AccountHeader
import com.example.model.CheckInLog
import com.example.model.ComputedStats
import com.example.model.CravingLog
import com.example.model.DriveSyncMetadata
import com.example.model.LockoutState
import com.example.model.QuitPlan
import com.example.model.RecoveryContent
import com.example.model.RecoveryMilestone
import com.example.model.ReminderPrefs
import com.example.model.SlipLog
import com.example.model.StatsAnalytics
import com.example.model.UiPrefs
import com.example.model.VaultFileEnvelope
import com.example.model.VaultPayload
import com.example.notifications.ReminderScheduler
import com.example.storage.VaultStorage
import com.example.totp.TotpEngine
import com.example.ui.components.MainTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

sealed class AppRoute {
    data object Welcome : AppRoute()
    data object SignUp : AppRoute()
    data class ShowRecoveryKey(
        val recoveryKey: String,
        val confirmGroupIndices: List<Int> // 3 zero-based group indices from 0..5
    ) : AppRoute()
    data class Setup2Fa(
        val secret: String,
        val otpAuthUri: String,
        val backupCodes: List<String>
    ) : AppRoute()
    data class SignIn(val requireTwoFactorStep: Boolean = false) : AppRoute()
    data object QuickUnlock : AppRoute()
    data class ResetPassword(val step: ResetStep = ResetStep.EnterRecoveryKey) : AppRoute()
    data object Onboarding : AppRoute()
    data object Dashboard : AppRoute()
}

enum class ResetStep {
    EnterRecoveryKey,
    VerifyTwoFactor,
    SetNewPassword
}

data class DailyLogUiState(
    val route: AppRoute = AppRoute.Welcome,
    val selectedTab: MainTab = MainTab.Today,
    val hasLocalVault: Boolean = false,
    val accountHeader: AccountHeader? = null,
    val payload: VaultPayload? = null,
    val stats: ComputedStats? = null,
    val darkTheme: Boolean = true,
    val reduceMotion: Boolean = false,
    val isCryptoBusy: Boolean = false,
    val cryptoBusyLabel: String = "",
    val authError: String? = null,
    val lockoutState: LockoutState = LockoutState(),
    val lockoutRemainingSeconds: Long = 0L,
    val toastMessage: String? = null,
    val toastIsError: Boolean = false,
    val driveMeta: DriveSyncMetadata = DriveSyncMetadata(),
    val isDriveSyncing: Boolean = false,
    val hasPlayedEntranceThisSession: Boolean = false,
    val newlyReachedMilestone: RecoveryMilestone? = null,
    val pulsingMilestoneIndex: Int? = null,
    val newlyGeneratedBackupCodes: List<String>? = null,
    val pendingTotpReconfig: Triple<String, String, List<String>>? = null, // secret, uri, backupCodes
    val biometricEnabled: Boolean = false
)

class DailyLogViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = VaultStorage(application)
    private val driveManager = DriveSyncManager(storage)
    private val onlineVerifier = OnlineVerifier()
    private val reminderScheduler = ReminderScheduler(application)

    private val _uiState = MutableStateFlow(DailyLogUiState())
    val uiState: StateFlow<DailyLogUiState> = _uiState.asStateFlow()

    // Sensitive keys strictly in RAM; zero-filled on lock, sign-out, cancel, or ViewModel clear
    private var unlockedDek: ByteArray? = null
    private var pendingDek: ByteArray? = null
    private var pendingHeader: AccountHeader? = null
    private var pendingPayload: VaultPayload? = null
    private var pendingSignUpPasswordChars: CharArray? = null
    private var pendingSignUpRecoveryKey: String? = null
    private var pendingSignUpIdentifier: String? = null
    private var pendingSignUpIdentifierType: String? = null
    private var driveAccessTokenInMemory: String? = null

    private var backgroundedAtMs: Long = 0L
    private var toastJob: Job? = null
    private var lockoutTickerJob: Job? = null
    private var driveDebounceJob: Job? = null

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        val envelope = storage.readEnvelope()
        val lockout = storage.readLockoutState()
        val driveMeta = storage.readDriveMetadata()
        val darkTheme = storage.readCachedThemeDark()
        val reduceMotion = storage.readCachedReduceMotion()
        val bioEnabled = envelope != null && storage.hasBiometricDekWrap()

        val startRoute = if (envelope != null) AppRoute.SignIn(false) else AppRoute.Welcome

        _uiState.update {
            it.copy(
                route = startRoute,
                hasLocalVault = envelope != null,
                accountHeader = envelope?.header,
                darkTheme = darkTheme,
                reduceMotion = reduceMotion,
                lockoutState = lockout,
                driveMeta = driveMeta,
                biometricEnabled = bioEnabled
            )
        }
        startLockoutTickerIfNeeded(lockout)
    }

    fun onAppBackgrounded() {
        backgroundedAtMs = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        val bgAt = backgroundedAtMs
        backgroundedAtMs = 0L
        if (bgAt == 0L || unlockedDek == null) return

        val elapsed = System.currentTimeMillis() - bgAt
        if (elapsed >= AppConfig.AUTO_LOCK_BACKGROUND_MS) {
            val last2fa = storage.readLastFull2faMs()
            val needsFull = (System.currentTimeMillis() - last2fa) >= AppConfig.FULL_2FA_MAX_AGE_MS
            lockNow(forceFull2fa = needsFull)
        }
    }

    fun lockNow(forceFull2fa: Boolean = false) {
        wipeAllSensitiveRam()
        val header = storage.readEnvelope()?.header
        val last2fa = storage.readLastFull2faMs()
        val expired2fa = (header?.totpEnabled == true) &&
            (System.currentTimeMillis() - last2fa) >= AppConfig.FULL_2FA_MAX_AGE_MS
        val nextRoute = when {
            header == null -> AppRoute.Welcome
            forceFull2fa || expired2fa -> AppRoute.SignIn(false)
            else -> AppRoute.QuickUnlock
        }
        _uiState.update {
            it.copy(
                route = nextRoute,
                payload = null,
                stats = null,
                authError = null,
                newlyReachedMilestone = null,
                newlyGeneratedBackupCodes = null,
                pendingTotpReconfig = null,
                hasPlayedEntranceThisSession = false
            )
        }
    }

    fun navigateToWelcome() {
        wipePendingSignUp()
        _uiState.update { it.copy(route = AppRoute.Welcome, authError = null) }
    }

    fun navigateToSignUp() {
        wipePendingSignUp()
        _uiState.update { it.copy(route = AppRoute.SignUp, authError = null) }
    }

    fun navigateToSignIn() {
        wipePendingSignUp()
        _uiState.update { it.copy(route = AppRoute.SignIn(false), authError = null) }
    }

    fun navigateToResetPassword() {
        wipePendingSignUp()
        _uiState.update { it.copy(route = AppRoute.ResetPassword(ResetStep.EnterRecoveryKey), authError = null) }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun markEntrancePlayed() {
        if (!_uiState.value.hasPlayedEntranceThisSession) {
            _uiState.update { it.copy(hasPlayedEntranceThisSession = true) }
        }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authError = null) }
    }

    fun dismissMilestoneSheet() {
        val milestone = _uiState.value.newlyReachedMilestone ?: return
        val current = _uiState.value.payload ?: return
        if (milestone.id !in current.celebratedMilestoneIds) {
            mutateVault { it.copy(celebratedMilestoneIds = it.celebratedMilestoneIds + milestone.id) }
        }
        _uiState.update { it.copy(newlyReachedMilestone = null, pulsingMilestoneIndex = null) }
    }

    fun dismissBackupCodesDialog() {
        _uiState.update { it.copy(newlyGeneratedBackupCodes = null) }
    }

    fun dismissTotpReconfigDialog() {
        _uiState.update { it.copy(pendingTotpReconfig = null) }
    }

    // -------------------------------------------------------------------------
    // SIGN-UP FLOW: Step 1 (Credentials) -> Step 2 (Recovery Key) -> Step 3 (Mandatory 2FA)
    // -------------------------------------------------------------------------

    fun beginSignUp(
        identifierType: String,
        rawEmail: String,
        dialCode: String,
        nationalPhone: String,
        password: String,
        confirmPassword: String
    ) {
        val (validId, idErr) = AuthValidator.validateIdentifier(identifierType, rawEmail, dialCode, nationalPhone)
        if (validId == null) {
            _uiState.update { it.copy(authError = idErr) }
            return
        }
        val strength = AuthValidator.evaluatePassword(password, validId)
        if (!strength.isValid) {
            _uiState.update { it.copy(authError = strength.error ?: "Choose a stronger password.") }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(authError = "Passwords do not match.") }
            return
        }

        wipePendingSignUp()
        pendingSignUpIdentifier = validId
        pendingSignUpIdentifierType = identifierType
        pendingSignUpPasswordChars = password.toCharArray()

        val recoveryKey = VaultCrypto.generateRecoveryKey()
        pendingSignUpRecoveryKey = recoveryKey

        // Pick 3 random distinct groups (out of 6) for mandatory confirmation
        val confirmIndices = (0..5).shuffled().take(3).sorted()

        _uiState.update {
            it.copy(
                route = AppRoute.ShowRecoveryKey(recoveryKey, confirmIndices),
                authError = null
            )
        }
    }

    fun confirmRecoveryKeyAndFinishSignUp(enteredGroups: Map<Int, String>) {
        val route = _uiState.value.route as? AppRoute.ShowRecoveryKey ?: return
        val expectedGroups = route.recoveryKey.split("-")
        for (idx in route.confirmGroupIndices) {
            val expected = expectedGroups.getOrNull(idx).orEmpty()
            val actual = enteredGroups[idx].orEmpty().trim().uppercase()
            if (!VaultCrypto.constantTimeEquals(expected, actual)) {
                _uiState.update {
                    it.copy(authError = "One or more recovery groups do not match. Check your saved key.")
                }
                return
            }
        }

        val id = pendingSignUpIdentifier ?: return
        val idType = pendingSignUpIdentifierType ?: "email"
        val passChars = pendingSignUpPasswordChars ?: return
        val recoveryKey = pendingSignUpRecoveryKey ?: return
        val initialPayload = VaultPayload(
            onboardingComplete = false,
            totpSecret = "",
            totpEnabled = false,
            backupCodes = emptyList()
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCryptoBusy = true,
                    cryptoBusyLabel = "Deriving encryption keys (PBKDF2-SHA256)...",
                    authError = null
                )
            }
            try {
                val (dek, envelope) = withContext(Dispatchers.Default) {
                    val newDek = VaultCrypto.generateDek()
                    val nowIso = nowIsoUtc()

                    val pwSalt = VaultCrypto.generateSalt()
                    val pwKek = VaultCrypto.deriveKek(passChars, pwSalt, id, AppConfig.PBKDF2_ITERATIONS)
                    val pwWrap = try {
                        VaultCrypto.wrapDek(newDek, pwKek, pwSalt, "pw:$id", AppConfig.PBKDF2_ITERATIONS)
                    } finally {
                        VaultCrypto.wipe(pwKek)
                    }

                    val recSalt = VaultCrypto.generateSalt()
                    val recChars = VaultCrypto.normalizeRecoveryKey(recoveryKey).toCharArray()
                    val recKek = try {
                        VaultCrypto.deriveKek(recChars, recSalt, null, AppConfig.PBKDF2_ITERATIONS)
                    } finally {
                        VaultCrypto.wipe(recChars)
                    }
                    val recWrap = try {
                        VaultCrypto.wrapDek(newDek, recKek, recSalt, "rec:$id", AppConfig.PBKDF2_ITERATIONS)
                    } finally {
                        VaultCrypto.wipe(recKek)
                    }

                    val header = AccountHeader(
                        identifier = id,
                        identifierType = idType,
                        createdAt = nowIso,
                        passwordWrap = pwWrap,
                        recoveryWrap = recWrap,
                        totpEnabled = false,
                        onlineVerified = false
                    )
                    val payloadJson = storage.serializePayload(initialPayload)
                    val encrypted = VaultCrypto.encryptVault(payloadJson, newDek, 1, nowIso)
                    val env = VaultFileEnvelope(AppConfig.VAULT_SCHEMA_VERSION, header, encrypted)
                    BiometricKeyManager.deleteKey()
                    storage.clearBiometricDekWrap()
                    storage.writeEnvelope(env)
                    storage.resetLockout()
                    storage.writeLastFull2faMs(System.currentTimeMillis())
                    newDek to env
                }

                wipePendingSignUp()
                unlockedDek = dek

                _uiState.update {
                    it.copy(
                        isCryptoBusy = false,
                        hasLocalVault = true,
                        biometricEnabled = false,
                        accountHeader = envelope.header,
                        payload = initialPayload,
                        stats = StatsAnalytics.compute(initialPayload),
                        route = AppRoute.Onboarding,
                        authError = null
                    )
                }
                showToast("Encrypted vault created offline.")
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isCryptoBusy = false,
                        authError = "Could not create your encrypted vault. Please try again."
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // SIGN-IN & QUICK UNLOCK (WITH PERSISTENT EXPONENTIAL LOCKOUT)
    // -------------------------------------------------------------------------

    fun signInPasswordStep(identifierInput: String, password: String) {
        if (isLockedOutNow()) {
            _uiState.update { it.copy(authError = "Too many attempts. Wait for the timer before trying again.") }
            return
        }
        val envelope = storage.readEnvelope()
        if (envelope == null) {
            _uiState.update { it.copy(authError = "Credentials could not be verified. Please try again.") }
            return
        }

        val cleanedId = identifierInput.trim().lowercase()
        val storedId = envelope.header.identifier.trim().lowercase()
        val passChars = password.toCharArray()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCryptoBusy = true,
                    cryptoBusyLabel = "Verifying credentials...",
                    authError = null
                )
            }
            val result = withContext(Dispatchers.Default) {
                try {
                    if (!VaultCrypto.constantTimeEquals(cleanedId, storedId)) {
                        // Still run PBKDF2 to keep timing uniform
                        val dummySalt = VaultCrypto.decodeB64(envelope.header.passwordWrap.salt)
                        VaultCrypto.wipe(VaultCrypto.deriveKek(passChars, dummySalt, storedId, envelope.header.passwordWrap.iterations))
                        null
                    } else {
                        val salt = VaultCrypto.decodeB64(envelope.header.passwordWrap.salt)
                        val kek = VaultCrypto.deriveKek(passChars, salt, storedId, envelope.header.passwordWrap.iterations)
                        val dek = try {
                            VaultCrypto.unwrapDek(envelope.header.passwordWrap, kek, "pw:$storedId")
                        } finally {
                            VaultCrypto.wipe(kek)
                        }
                        val json = VaultCrypto.decryptVault(envelope.vault, dek)
                        val payload = storage.deserializePayload(json)
                        if (payload != null) dek to payload else {
                            VaultCrypto.wipe(dek)
                            null
                        }
                    }
                } catch (_: Exception) {
                    null
                } finally {
                    VaultCrypto.wipe(passChars)
                }
            }

            if (result == null) {
                recordAuthFailure()
                _uiState.update {
                    it.copy(
                        isCryptoBusy = false,
                        authError = "Credentials could not be verified. Please try again."
                    )
                }
                return@launch
            }

            val (dek, payload) = result
            if (payload.totpEnabled && payload.totpSecret.isNotBlank()) {
                // Hold DEK in pending memory only until 2FA code verifies
                VaultCrypto.wipe(pendingDek)
                pendingDek = dek
                pendingHeader = envelope.header
                pendingPayload = payload
                _uiState.update {
                    it.copy(
                        isCryptoBusy = false,
                        route = AppRoute.SignIn(requireTwoFactorStep = true),
                        authError = null
                    )
                }
            } else {
                completeUnlock(dek, envelope.header, payload, markFull2fa = true)
            }
        }
    }

    fun signInTwoFactorStep(codeOrBackup: String) {
        if (isLockedOutNow()) {
            _uiState.update { it.copy(authError = "Too many attempts. Wait for the timer before trying again.") }
            return
        }
        val dek = pendingDek
        val header = pendingHeader
        val payload = pendingPayload
        if (dek == null || header == null || payload == null) {
            _uiState.update { it.copy(route = AppRoute.SignIn(false), authError = "Session expired. Enter your password again.") }
            return
        }

        val totpOk = TotpEngine.verifyCode(payload.totpSecret, codeOrBackup)
        if (totpOk) {
            pendingDek = null
            pendingHeader = null
            pendingPayload = null
            completeUnlock(dek, header, payload, markFull2fa = true)
            return
        }

        val backupIdx = TotpEngine.findMatchingBackupCodeIndex(codeOrBackup, payload.backupCodes)
        if (backupIdx >= 0) {
            val updatedCodes = payload.backupCodes.mapIndexed { idx, item ->
                if (idx == backupIdx) item.copy(used = true) else item
            }
            val updatedPayload = payload.copy(backupCodes = updatedCodes)
            pendingDek = null
            pendingHeader = null
            pendingPayload = null
            completeUnlock(dek, header, updatedPayload, markFull2fa = true, persistImmediately = true)
            showToast("Signed in with single-use backup code (${updatedCodes.count { !it.used }} remaining).")
            return
        }

        recordAuthFailure()
        _uiState.update { it.copy(authError = "Credentials could not be verified. Please try again.") }
    }

    fun quickUnlockWithPassword(password: String) {
        val header = storage.readEnvelope()?.header ?: run {
            _uiState.update { it.copy(route = AppRoute.Welcome) }
            return
        }
        if (isLockedOutNow()) {
            _uiState.update { it.copy(authError = "Too many attempts. Wait for the timer before trying again.") }
            return
        }

        // Enforce 24-hour full 2FA policy ONLY when optional 2FA is enabled
        val last2fa = storage.readLastFull2faMs()
        if (header.totpEnabled && (System.currentTimeMillis() - last2fa) >= AppConfig.FULL_2FA_MAX_AGE_MS) {
            _uiState.update {
                it.copy(
                    route = AppRoute.SignIn(false),
                    authError = "24 hours elapsed. Full sign-in with 2FA is required."
                )
            }
            return
        }

        val envelope = storage.readEnvelope() ?: return
        val passChars = password.toCharArray()
        val storedId = header.identifier.trim().lowercase()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCryptoBusy = true,
                    cryptoBusyLabel = "Unlocking encrypted vault...",
                    authError = null
                )
            }
            val result = withContext(Dispatchers.Default) {
                try {
                    val salt = VaultCrypto.decodeB64(header.passwordWrap.salt)
                    val kek = VaultCrypto.deriveKek(passChars, salt, storedId, header.passwordWrap.iterations)
                    val dek = try {
                        VaultCrypto.unwrapDek(header.passwordWrap, kek, "pw:$storedId")
                    } finally {
                        VaultCrypto.wipe(kek)
                    }
                    val json = VaultCrypto.decryptVault(envelope.vault, dek)
                    val payload = storage.deserializePayload(json)
                    if (payload != null) dek to payload else {
                        VaultCrypto.wipe(dek)
                        null
                    }
                } catch (_: Exception) {
                    null
                } finally {
                    VaultCrypto.wipe(passChars)
                }
            }

            if (result == null) {
                recordAuthFailure()
                _uiState.update {
                    it.copy(isCryptoBusy = false, authError = "Credentials could not be verified. Please try again.")
                }
            } else {
                completeUnlock(result.first, header, result.second, markFull2fa = false)
            }
        }
    }

    // -------------------------------------------------------------------------
    // FINGERPRINT / BIOMETRIC UNLOCK (HARDWARE-BACKED ANDROID KEYSTORE)
    // -------------------------------------------------------------------------

    fun prepareBiometricEnableCipher(): Cipher? {
        if (unlockedDek == null) return null
        return try {
            BiometricKeyManager.createEncryptCipher()
        } catch (_: Exception) {
            showToast("Biometric hardware is unavailable on this device.", isError = true)
            null
        }
    }

    fun completeBiometricEnable(authedCipher: Cipher) {
        val dek = unlockedDek ?: return
        try {
            val wrappedBytes = authedCipher.doFinal(dek)
            val ivB64 = VaultCrypto.encodeB64(authedCipher.iv)
            val ctB64 = VaultCrypto.encodeB64(wrappedBytes)
            storage.writeBiometricDekWrap(ivB64, ctB64)
            _uiState.update { it.copy(biometricEnabled = true) }
            showToast("Fingerprint unlock enabled on this device.")
        } catch (_: Exception) {
            BiometricKeyManager.deleteKey()
            storage.clearBiometricDekWrap()
            showToast("Could not enable fingerprint unlock.", isError = true)
        }
    }

    fun disableBiometricUnlock(silent: Boolean = false) {
        BiometricKeyManager.deleteKey()
        storage.clearBiometricDekWrap()
        _uiState.update { it.copy(biometricEnabled = false) }
        if (!silent) {
            showToast("Fingerprint unlock disabled.")
        }
    }

    fun prepareBiometricUnlockCipher(): Cipher? {
        if (isLockedOutNow()) {
            _uiState.update { it.copy(authError = "Too many attempts. Wait for the timer before trying again.") }
            return null
        }
        val wrapped = storage.readBiometricDekWrap() ?: run {
            _uiState.update { it.copy(biometricEnabled = false) }
            return null
        }
        val cipher = BiometricKeyManager.createDecryptCipher(wrapped.first)
        if (cipher == null) {
            disableBiometricUnlock(silent = true)
            _uiState.update {
                it.copy(authError = "Fingerprint key was invalidated after device biometric changes. Unlock with your password and re-enable fingerprint in Settings.")
            }
        }
        return cipher
    }

    fun unlockWithBiometricCipher(authedCipher: Cipher, isQuickUnlock: Boolean) {
        val envelope = storage.readEnvelope() ?: return
        val wrapped = storage.readBiometricDekWrap() ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isCryptoBusy = true, cryptoBusyLabel = "Decrypting vault...", authError = null)
            }
            val result = withContext(Dispatchers.Default) {
                try {
                    val ct = VaultCrypto.decodeB64(wrapped.second)
                    val dek = authedCipher.doFinal(ct)
                    val json = VaultCrypto.decryptVault(envelope.vault, dek)
                    val payload = storage.deserializePayload(json)
                    if (payload != null) dek to payload else {
                        VaultCrypto.wipe(dek)
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }

            if (result == null) {
                disableBiometricUnlock(silent = true)
                _uiState.update {
                    it.copy(
                        isCryptoBusy = false,
                        authError = "Fingerprint unlock failed. Please sign in with your password."
                    )
                }
                return@launch
            }

            val (dek, payload) = result
            if (!isQuickUnlock && payload.totpEnabled && payload.totpSecret.isNotBlank()) {
                VaultCrypto.wipe(pendingDek)
                pendingDek = dek
                pendingHeader = envelope.header
                pendingPayload = payload
                _uiState.update {
                    it.copy(
                        isCryptoBusy = false,
                        route = AppRoute.SignIn(requireTwoFactorStep = true),
                        authError = null
                    )
                }
            } else {
                completeUnlock(dek, envelope.header, payload, markFull2fa = !isQuickUnlock)
            }
        }
    }

    private fun completeUnlock(
        dek: ByteArray,
        header: AccountHeader,
        payload: VaultPayload,
        markFull2fa: Boolean,
        persistImmediately: Boolean = false
    ) {
        VaultCrypto.wipe(unlockedDek)
        unlockedDek = dek
        storage.resetLockout()
        if (markFull2fa) {
            storage.writeLastFull2faMs(System.currentTimeMillis())
        }
        storage.writeCachedThemeDark(payload.uiPrefs.themeMode != "light")
        storage.writeCachedReduceMotion(payload.uiPrefs.reduceMotion)

        val computed = StatsAnalytics.compute(payload)
        val nextRoute = if (payload.onboardingComplete) AppRoute.Dashboard else AppRoute.Onboarding

        _uiState.update {
            it.copy(
                isCryptoBusy = false,
                route = nextRoute,
                accountHeader = header,
                payload = payload,
                stats = computed,
                darkTheme = payload.uiPrefs.themeMode != "light",
                reduceMotion = payload.uiPrefs.reduceMotion,
                lockoutState = LockoutState(),
                lockoutRemainingSeconds = 0L,
                authError = null
            )
        }

        if (persistImmediately) {
            persistCurrentVault(payload, header)
        }
        if (payload.onboardingComplete) {
            checkForNewlyReachedMilestone(payload)
            reminderScheduler.rescheduleAll(payload.reminders, computed.peakCravingHour)
        }
    }

    // -------------------------------------------------------------------------
    // RESET PASSWORD VIA RECOVERY KEY + 2FA
    // -------------------------------------------------------------------------

    fun resetVerifyRecoveryKey(identifierInput: String, recoveryKeyInput: String) {
        if (isLockedOutNow()) {
            _uiState.update { it.copy(authError = "Too many attempts. Wait before trying again.") }
            return
        }
        val envelope = storage.readEnvelope() ?: run {
            _uiState.update { it.copy(authError = "No local account found on this device.") }
            return
        }
        val storedId = envelope.header.identifier.trim().lowercase()
        val cleanedId = identifierInput.trim().lowercase()
        val recChars = VaultCrypto.normalizeRecoveryKey(recoveryKeyInput).toCharArray()

        viewModelScope.launch {
            _uiState.update {
                it.copy(isCryptoBusy = true, cryptoBusyLabel = "Verifying recovery key...", authError = null)
            }
            val result = withContext(Dispatchers.Default) {
                try {
                    if (!VaultCrypto.constantTimeEquals(cleanedId, storedId)) null
                    else {
                        val salt = VaultCrypto.decodeB64(envelope.header.recoveryWrap.salt)
                        val kek = VaultCrypto.deriveKek(recChars, salt, null, envelope.header.recoveryWrap.iterations)
                        val dek = try {
                            VaultCrypto.unwrapDek(envelope.header.recoveryWrap, kek, "rec:$storedId")
                        } finally {
                            VaultCrypto.wipe(kek)
                        }
                        val json = VaultCrypto.decryptVault(envelope.vault, dek)
                        val payload = storage.deserializePayload(json)
                        if (payload != null) dek to payload else {
                            VaultCrypto.wipe(dek)
                            null
                        }
                    }
                } catch (_: Exception) {
                    null
                } finally {
                    VaultCrypto.wipe(recChars)
                }
            }

            if (result == null) {
                recordAuthFailure()
                _uiState.update {
                    it.copy(isCryptoBusy = false, authError = "Credentials could not be verified. Please try again.")
                }
            } else {
                VaultCrypto.wipe(pendingDek)
                pendingDek = result.first
                pendingHeader = envelope.header
                pendingPayload = result.second
                val nextStep = if (result.second.totpEnabled && result.second.totpSecret.isNotBlank()) {
                    ResetStep.VerifyTwoFactor
                } else {
                    ResetStep.SetNewPassword
                }
                _uiState.update {
                    it.copy(isCryptoBusy = false, route = AppRoute.ResetPassword(nextStep), authError = null)
                }
            }
        }
    }

    fun resetVerify2Fa(codeOrBackup: String) {
        val payload = pendingPayload ?: return
        val totpOk = TotpEngine.verifyCode(payload.totpSecret, codeOrBackup)
        val backupIdx = if (!totpOk) TotpEngine.findMatchingBackupCodeIndex(codeOrBackup, payload.backupCodes) else -1
        if (!totpOk && backupIdx < 0) {
            recordAuthFailure()
            _uiState.update { it.copy(authError = "Credentials could not be verified. Please try again.") }
            return
        }
        if (backupIdx >= 0) {
            pendingPayload = payload.copy(
                backupCodes = payload.backupCodes.mapIndexed { i, c -> if (i == backupIdx) c.copy(used = true) else c }
            )
        }
        _uiState.update {
            it.copy(route = AppRoute.ResetPassword(ResetStep.SetNewPassword), authError = null)
        }
    }

    fun resetCommitNewPassword(newPassword: String, confirmPassword: String) {
        val dek = pendingDek ?: return
        val header = pendingHeader ?: return
        val payload = pendingPayload ?: return
        val strength = AuthValidator.evaluatePassword(newPassword, header.identifier)
        if (!strength.isValid) {
            _uiState.update { it.copy(authError = strength.error ?: "Choose a stronger password.") }
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.update { it.copy(authError = "Passwords do not match.") }
            return
        }
        val passChars = newPassword.toCharArray()
        val storedId = header.identifier.trim().lowercase()

        viewModelScope.launch {
            _uiState.update {
                it.copy(isCryptoBusy = true, cryptoBusyLabel = "Re-wrapping data encryption key...", authError = null)
            }
            val updatedHeader = withContext(Dispatchers.Default) {
                try {
                    val newSalt = VaultCrypto.generateSalt()
                    val newKek = VaultCrypto.deriveKek(passChars, newSalt, storedId, AppConfig.PBKDF2_ITERATIONS)
                    val newWrap = try {
                        VaultCrypto.wrapDek(dek, newKek, newSalt, "pw:$storedId", AppConfig.PBKDF2_ITERATIONS)
                    } finally {
                        VaultCrypto.wipe(newKek)
                    }
                    header.copy(passwordWrap = newWrap)
                } finally {
                    VaultCrypto.wipe(passChars)
                }
            }
            pendingDek = null
            pendingHeader = null
            pendingPayload = null
            completeUnlock(dek, updatedHeader, payload, markFull2fa = true, persistImmediately = true)
            showToast("Password updated. Your encryption key was re-wrapped.")
        }
    }

    // -------------------------------------------------------------------------
    // ONBOARDING & CORE RECOVERY ACTIONS
    // -------------------------------------------------------------------------

    fun saveQuitPlan(plan: QuitPlan, fromOnboarding: Boolean) {
        val now = System.currentTimeMillis()
        mutateVault { current ->
            val streakStart = if (fromOnboarding || current.currentStreakStartMs == 0L) {
                plan.quitEpochMs
            } else {
                // Keep streak start aligned when user edits their quit date directly
                plan.quitEpochMs
            }
            val elapsed = (now - streakStart).coerceAtLeast(0L)
            current.copy(
                onboardingComplete = true,
                quitPlan = plan,
                currentStreakStartMs = streakStart,
                longestStreakMs = maxOf(current.longestStreakMs, elapsed)
            )
        }
        if (fromOnboarding) {
            _uiState.update {
                it.copy(
                    route = AppRoute.Dashboard,
                    selectedTab = MainTab.Today,
                    hasPlayedEntranceThisSession = false
                )
            }
        } else {
            showToast("Quit plan updated.")
        }
    }

    fun logCravingSession(intensity: Int, trigger: String, durationSeconds: Int, resisted: Boolean) {
        val log = CravingLog(
            id = UUID.randomUUID().toString(),
            timestampMs = System.currentTimeMillis(),
            intensity = intensity.coerceIn(1, 10),
            trigger = trigger,
            durationSeconds = durationSeconds.coerceAtLeast(1),
            resisted = resisted
        )
        mutateVault { current ->
            current.copy(cravings = current.cravings + log)
        }
        if (resisted) {
            showToast("Logged. You got through it.")
        }
    }

    fun logDailyCheckIn(
        mood: Int,
        sleepHours: Double,
        cravingLevel: Int,
        usedToday: Boolean,
        note: String
    ) {
        val now = System.currentTimeMillis()
        val entry = CheckInLog(
            id = UUID.randomUUID().toString(),
            dateKey = StatsAnalytics.todayDateKey(now),
            timestampMs = now,
            mood = mood.coerceIn(1, 5),
            sleepHours = sleepHours.coerceIn(0.0, 14.0),
            cravingLevel = cravingLevel.coerceIn(0, 10),
            usedToday = usedToday,
            note = note.trim()
        )
        mutateVault { current ->
            // Replace same-day entry if already checked in today, or append
            val filtered = current.checkIns.filterNot { it.dateKey == entry.dateKey }
            current.copy(checkIns = filtered + entry)
        }
        showToast("Daily check-in encrypted and saved.")
    }

    fun logSlip(trigger: String, note: String) {
        val now = System.currentTimeMillis()
        mutateVault { current ->
            val prevStart = current.currentStreakStartMs.takeIf { it > 0L } ?: current.quitPlan.quitEpochMs
            val completedStreak = (now - prevStart).coerceAtLeast(0L)
            val newLongest = maxOf(current.longestStreakMs, completedStreak)
            val slip = SlipLog(
                id = UUID.randomUUID().toString(),
                timestampMs = now,
                trigger = trigger,
                note = note.trim(),
                previousStreakStartMs = prevStart
            )
            current.copy(
                currentStreakStartMs = now,
                longestStreakMs = newLongest,
                slips = current.slips + slip,
                celebratedMilestoneIds = emptyList()
            )
        }
        showToast("Slip logged. Your streak has reset — history and longest streak are preserved.")
    }

    fun checkMilestoneTick() {
        val payload = _uiState.value.payload ?: return
        if (payload.onboardingComplete) {
            checkForNewlyReachedMilestone(payload)
        }
    }

    private fun checkForNewlyReachedMilestone(payload: VaultPayload) {
        val start = payload.currentStreakStartMs
        if (start <= 0L) return
        val elapsedHours = (System.currentTimeMillis() - start) / 3_600_000L
        if (elapsedHours < 24L) return

        val newlyCrossedIdx = RecoveryContent.MILESTONES.indexOfLast {
            elapsedHours >= it.hoursRequired && it.id !in payload.celebratedMilestoneIds
        }
        if (newlyCrossedIdx >= 0) {
            val milestone = RecoveryContent.MILESTONES[newlyCrossedIdx]
            _uiState.update {
                it.copy(
                    newlyReachedMilestone = milestone,
                    pulsingMilestoneIndex = newlyCrossedIdx
                )
            }
            reminderScheduler.notifyMilestoneReached(payload.reminders)
        }
    }

    // -------------------------------------------------------------------------
    // SETTINGS: APPEARANCE, REMINDERS, SECURITY RE-AUTH, DRIVE BACKUP, ERASE
    // -------------------------------------------------------------------------

    fun updateTheme(dark: Boolean) {
        storage.writeCachedThemeDark(dark)
        _uiState.update { it.copy(darkTheme = dark) }
        if (_uiState.value.payload != null) {
            mutateVault { it.copy(uiPrefs = it.uiPrefs.copy(themeMode = if (dark) "dark" else "light")) }
        }
    }

    fun updateReduceMotion(reduce: Boolean) {
        storage.writeCachedReduceMotion(reduce)
        _uiState.update { it.copy(reduceMotion = reduce) }
        if (_uiState.value.payload != null) {
            mutateVault { it.copy(uiPrefs = it.uiPrefs.copy(reduceMotion = reduce)) }
        }
    }

    fun updateReminders(prefs: ReminderPrefs) {
        mutateVault { it.copy(reminders = prefs) }
        val peakHour = _uiState.value.stats?.peakCravingHour
        reminderScheduler.rescheduleAll(prefs, peakHour)
        showToast(if (prefs.enabled) "Quiet reminders scheduled." else "Reminders paused.")
    }

    fun updateAutoBackup(enabled: Boolean) {
        mutateVault { it.copy(uiPrefs = it.uiPrefs.copy(autoBackupDrive = enabled)) }
        if (enabled && driveAccessTokenInMemory != null) {
            triggerDriveBackupNow()
        }
    }

    fun requestOnlineVerification(onlinePassword: String) {
        val header = _uiState.value.accountHeader ?: return
        if (header.identifierType != "email") {
            showToast(
                "Phone SMS verification requires configuring Firebase Phone Auth in AppConfig. Your local account works offline.",
                isError = false
            )
            return
        }
        viewModelScope.launch {
            when (val res = onlineVerifier.requestEmailVerification(header.identifier, onlinePassword)) {
                is OnlineVerifier.VerifyResult.Sent -> showToast(res.message)
                is OnlineVerifier.VerifyResult.NotConfigured -> showToast(res.message)
                is OnlineVerifier.VerifyResult.Failed -> showToast(res.message, isError = true)
            }
        }
    }

    /**
     * Re-authenticates current password + 2FA (if enabled) before sensitive Settings changes.
     */
    private suspend fun verifyReAuth(currentPassword: String, twoFactorCode: String): Boolean {
        val header = _uiState.value.accountHeader ?: return false
        val payload = _uiState.value.payload ?: return false
        val storedId = header.identifier.trim().lowercase()
        val passChars = currentPassword.toCharArray()

        val passwordOk = withContext(Dispatchers.Default) {
            try {
                val salt = VaultCrypto.decodeB64(header.passwordWrap.salt)
                val kek = VaultCrypto.deriveKek(passChars, salt, storedId, header.passwordWrap.iterations)
                val unwrapped = try {
                    VaultCrypto.unwrapDek(header.passwordWrap, kek, "pw:$storedId")
                } finally {
                    VaultCrypto.wipe(kek)
                }
                VaultCrypto.wipe(unwrapped)
                true
            } catch (_: Exception) {
                false
            } finally {
                VaultCrypto.wipe(passChars)
            }
        }
        if (!passwordOk) return false

        if (payload.totpEnabled && payload.totpSecret.isNotBlank()) {
            val totpOk = TotpEngine.verifyCode(payload.totpSecret, twoFactorCode)
            val backupIdx = if (!totpOk) TotpEngine.findMatchingBackupCodeIndex(twoFactorCode, payload.backupCodes) else -1
            return totpOk || backupIdx >= 0
        }
        return true
    }

    fun changePassword(
        currentPassword: String,
        twoFactorCode: String,
        newPassword: String,
        confirmPassword: String,
        onDone: (Boolean, String?) -> Unit
    ) {
        val header = _uiState.value.accountHeader ?: return
        val dek = unlockedDek ?: return
        val payload = _uiState.value.payload ?: return

        val strength = AuthValidator.evaluatePassword(newPassword, header.identifier)
        if (!strength.isValid) {
            onDone(false, strength.error ?: "Choose a stronger password.")
            return
        }
        if (newPassword != confirmPassword) {
            onDone(false, "New passwords do not match.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCryptoBusy = true, cryptoBusyLabel = "Re-wrapping encryption key...") }
            val reAuthOk = verifyReAuth(currentPassword, twoFactorCode)
            if (!reAuthOk) {
                _uiState.update { it.copy(isCryptoBusy = false) }
                onDone(false, "Current password or 2FA code could not be verified.")
                return@launch
            }

            val storedId = header.identifier.trim().lowercase()
            val newPassChars = newPassword.toCharArray()
            val updatedHeader = withContext(Dispatchers.Default) {
                try {
                    val newSalt = VaultCrypto.generateSalt()
                    val newKek = VaultCrypto.deriveKek(newPassChars, newSalt, storedId, AppConfig.PBKDF2_ITERATIONS)
                    val newWrap = try {
                        VaultCrypto.wrapDek(dek, newKek, newSalt, "pw:$storedId", AppConfig.PBKDF2_ITERATIONS)
                    } finally {
                        VaultCrypto.wipe(newKek)
                    }
                    header.copy(passwordWrap = newWrap)
                } finally {
                    VaultCrypto.wipe(newPassChars)
                }
            }

            persistCurrentVault(payload, updatedHeader)
            _uiState.update { it.copy(isCryptoBusy = false, accountHeader = updatedHeader) }
            showToast("Password changed. Only the data key wrapper was updated.")
            onDone(true, null)
        }
    }

    fun regenerateBackupCodes(currentPassword: String, twoFactorCode: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCryptoBusy = true, cryptoBusyLabel = "Verifying identity...") }
            val ok = verifyReAuth(currentPassword, twoFactorCode)
            _uiState.update { it.copy(isCryptoBusy = false) }
            if (!ok) {
                onError("Password or 2FA code could not be verified.")
                return@launch
            }
            val (plain, hashes) = TotpEngine.generateBackupCodes(10)
            mutateVault { it.copy(backupCodes = hashes) }
            _uiState.update { it.copy(newlyGeneratedBackupCodes = plain) }
        }
    }

    fun beginChangeAuthenticator(currentPassword: String, twoFactorCode: String, onError: (String) -> Unit) {
        val header = _uiState.value.accountHeader ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCryptoBusy = true, cryptoBusyLabel = "Verifying identity...") }
            val ok = verifyReAuth(currentPassword, twoFactorCode)
            _uiState.update { it.copy(isCryptoBusy = false) }
            if (!ok) {
                onError("Password or 2FA code could not be verified.")
                return@launch
            }
            val secret = TotpEngine.generateSecret()
            val uri = TotpEngine.buildOtpAuthUri(header.identifier, secret)
            val (plain, _) = TotpEngine.generateBackupCodes(10)
            _uiState.update { it.copy(pendingTotpReconfig = Triple(secret, uri, plain)) }
        }
    }

    fun confirmChangeAuthenticator(newCode: String, onError: (String) -> Unit) {
        val pending = _uiState.value.pendingTotpReconfig ?: return
        val secret = pending.first
        if (!TotpEngine.verifyCode(secret, newCode)) {
            onError("Code did not match the new authenticator secret.")
            return
        }
        val (plain, hashes) = TotpEngine.generateBackupCodes(10)
        val updatedHeader = _uiState.value.accountHeader?.copy(totpEnabled = true)
        mutateVault(overrideHeader = updatedHeader) {
            it.copy(totpSecret = secret, totpEnabled = true, backupCodes = hashes)
        }
        _uiState.update {
            it.copy(
                pendingTotpReconfig = null,
                newlyGeneratedBackupCodes = plain
            )
        }
        showToast("Authenticator updated and backup codes refreshed.")
    }

    fun disableTwoFactor(currentPassword: String, twoFactorCode: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCryptoBusy = true, cryptoBusyLabel = "Verifying identity...") }
            val ok = verifyReAuth(currentPassword, twoFactorCode)
            _uiState.update { it.copy(isCryptoBusy = false) }
            if (!ok) {
                onResult(false, "Password or 2FA code could not be verified.")
                return@launch
            }
            val updatedHeader = _uiState.value.accountHeader?.copy(totpEnabled = false)
            mutateVault(overrideHeader = updatedHeader) {
                it.copy(totpEnabled = false, totpSecret = "", backupCodes = emptyList())
            }
            showToast("2FA disabled. You can re-enable it anytime in Settings.")
            onResult(true, null)
        }
    }

    fun signOut() {
        wipeAllSensitiveRam()
        driveAccessTokenInMemory = null
        val hasVault = storage.hasVault()
        _uiState.update {
            it.copy(
                route = if (hasVault) AppRoute.SignIn(false) else AppRoute.Welcome,
                payload = null,
                stats = null,
                authError = null,
                hasPlayedEntranceThisSession = false
            )
        }
        showToast("Signed out. Encryption key cleared from memory.")
    }

    fun eraseAllLocalData(currentPassword: String, twoFactorCode: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCryptoBusy = true, cryptoBusyLabel = "Verifying identity before erasure...") }
            val ok = verifyReAuth(currentPassword, twoFactorCode)
            if (!ok) {
                _uiState.update { it.copy(isCryptoBusy = false) }
                onResult(false, "Password or 2FA code could not be verified.")
                return@launch
            }
            wipeAllSensitiveRam()
            driveAccessTokenInMemory = null
            BiometricKeyManager.deleteKey()
            reminderScheduler.cancelAll()
            storage.eraseAllLocalData()
            _uiState.update {
                DailyLogUiState(
                    route = AppRoute.Welcome,
                    hasLocalVault = false,
                    biometricEnabled = false,
                    darkTheme = true,
                    reduceMotion = false
                )
            }
            showToast("All local encrypted data erased.")
            onResult(true, null)
        }
    }

    // -------------------------------------------------------------------------
    // GOOGLE DRIVE BACKUP & ENCRYPTED FILE EXPORT / RESTORE
    // -------------------------------------------------------------------------

    fun getEncryptedVaultJsonForExport(): String? {
        val env = storage.readEnvelope() ?: return null
        return storage.serializeEnvelope(env)
    }

    fun importEncryptedVaultJson(rawJson: String) {
        val envelope = storage.deserializeEnvelope(rawJson)
        if (envelope == null || envelope.header.identifier.isBlank() || envelope.vault.ciphertext.isBlank()) {
            showToast("File is not a valid ${AppConfig.DRIVE_BACKUP_FILENAME} encrypted backup.", isError = true)
            return
        }
        BiometricKeyManager.deleteKey()
        storage.clearBiometricDekWrap()
        storage.writeEnvelope(envelope)
        storage.resetLockout()
        _uiState.update {
            it.copy(
                hasLocalVault = true,
                biometricEnabled = false,
                accountHeader = envelope.header,
                route = AppRoute.SignIn(false),
                authError = null
            )
        }
        showToast("Encrypted backup loaded. Sign in with your password to decrypt.")
    }

    fun connectDriveWithToken(accessToken: String, email: String?) {
        driveAccessTokenInMemory = accessToken.trim()
        val currentMeta = _uiState.value.driveMeta
        storage.writeDriveMetadata(email ?: currentMeta.connectedEmail, currentMeta.fileId, currentMeta.lastBackupIso)
        _uiState.update {
            it.copy(driveMeta = it.driveMeta.copy(connectedEmail = email ?: "Connected", accessToken = "active"))
        }
        triggerDriveBackupNow()
    }

    fun triggerDriveBackupNow() {
        val token = driveAccessTokenInMemory
        if (token.isNullOrBlank()) {
            val msg = if (!driveManager.isOAuthConfigured()) {
                "Configure DRIVE_OAUTH_CLIENT_ID in AppConfig.kt for Google Drive, or export your encrypted ${AppConfig.DRIVE_BACKUP_FILENAME} locally."
            } else {
                "Connect your Google Drive token first to back up ciphertext to appDataFolder."
            }
            showToast(msg)
            return
        }
        val envelope = storage.readEnvelope() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDriveSyncing = true) }
            when (val res = driveManager.syncLastWriteWins(token, envelope)) {
                is DriveSyncManager.SyncResult.Uploaded -> {
                    storage.writeDriveMetadata(_uiState.value.driveMeta.connectedEmail, res.fileId, res.updatedAt)
                    _uiState.update {
                        it.copy(
                            isDriveSyncing = false,
                            driveMeta = it.driveMeta.copy(fileId = res.fileId, lastBackupIso = res.updatedAt)
                        )
                    }
                    showToast("Encrypted vault backed up to Google Drive.")
                }
                is DriveSyncManager.SyncResult.Downloaded -> {
                    // Remote was newer — decrypt with current DEK if possible
                    val dek = unlockedDek
                    if (dek != null) {
                        val decrypted = try {
                            storage.deserializePayload(VaultCrypto.decryptVault(res.envelope.vault, dek))
                        } catch (_: Exception) {
                            null
                        }
                        if (decrypted != null) {
                            storage.writeEnvelope(res.envelope)
                            storage.writeDriveMetadata(_uiState.value.driveMeta.connectedEmail, res.fileId, res.envelope.vault.updatedAt)
                            _uiState.update {
                                it.copy(
                                    isDriveSyncing = false,
                                    accountHeader = res.envelope.header,
                                    payload = decrypted,
                                    stats = StatsAnalytics.compute(decrypted),
                                    driveMeta = it.driveMeta.copy(fileId = res.fileId, lastBackupIso = res.envelope.vault.updatedAt)
                                )
                            }
                            showToast("Synced newer encrypted backup from Google Drive.")
                        } else {
                            _uiState.update { it.copy(isDriveSyncing = false) }
                            showToast("Remote backup uses a different encryption key.", isError = true)
                        }
                    } else {
                        _uiState.update { it.copy(isDriveSyncing = false) }
                    }
                }
                is DriveSyncManager.SyncResult.Error -> {
                    _uiState.update { it.copy(isDriveSyncing = false) }
                    showToast(res.message, isError = true)
                }
                else -> _uiState.update { it.copy(isDriveSyncing = false) }
            }
        }
    }

    fun restoreFromDriveToken(accessToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDriveSyncing = true, authError = null) }
            when (val res = driveManager.downloadEncryptedVault(accessToken.trim())) {
                is DriveSyncManager.SyncResult.Downloaded -> {
                    BiometricKeyManager.deleteKey()
                    storage.clearBiometricDekWrap()
                    storage.writeEnvelope(res.envelope)
                    storage.writeDriveMetadata(null, res.fileId, res.envelope.vault.updatedAt)
                    driveAccessTokenInMemory = accessToken.trim()
                    _uiState.update {
                        it.copy(
                            isDriveSyncing = false,
                            hasLocalVault = true,
                            biometricEnabled = false,
                            accountHeader = res.envelope.header,
                            route = AppRoute.SignIn(false),
                            driveMeta = it.driveMeta.copy(fileId = res.fileId, lastBackupIso = res.envelope.vault.updatedAt)
                        )
                    }
                    showToast("Backup downloaded. Sign in with your password or recovery key.")
                }
                is DriveSyncManager.SyncResult.NotFound -> {
                    _uiState.update { it.copy(isDriveSyncing = false, authError = res.message) }
                }
                is DriveSyncManager.SyncResult.Error -> {
                    _uiState.update { it.copy(isDriveSyncing = false, authError = res.message) }
                }
                else -> _uiState.update { it.copy(isDriveSyncing = false) }
            }
        }
    }

    fun deleteDriveBackup() {
        val token = driveAccessTokenInMemory
        if (token.isNullOrBlank()) {
            showToast("Connect Google Drive first to delete your cloud backup.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isDriveSyncing = true) }
            when (val res = driveManager.deleteCloudBackup(token, _uiState.value.driveMeta.fileId)) {
                is DriveSyncManager.SyncResult.Deleted, is DriveSyncManager.SyncResult.NotFound -> {
                    storage.writeDriveMetadata(_uiState.value.driveMeta.connectedEmail, null, null)
                    _uiState.update {
                        it.copy(
                            isDriveSyncing = false,
                            driveMeta = it.driveMeta.copy(fileId = null, lastBackupIso = null)
                        )
                    }
                    showToast("Cloud backup deleted from Google Drive.")
                }
                is DriveSyncManager.SyncResult.Error -> {
                    _uiState.update { it.copy(isDriveSyncing = false) }
                    showToast(res.message, isError = true)
                }
                else -> _uiState.update { it.copy(isDriveSyncing = false) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // INTERNAL VAULT ENCRYPTION, LOCKOUT & HELPERS
    // -------------------------------------------------------------------------

    private fun mutateVault(
        overrideHeader: AccountHeader? = null,
        transform: (VaultPayload) -> VaultPayload
    ) {
        val current = _uiState.value.payload ?: return
        val header = overrideHeader ?: _uiState.value.accountHeader ?: return
        val updated = transform(current)
        val stats = StatsAnalytics.compute(updated)
        _uiState.update {
            it.copy(
                accountHeader = header,
                payload = updated,
                stats = stats
            )
        }
        persistCurrentVault(updated, header)
        if (updated.onboardingComplete) {
            reminderScheduler.rescheduleAll(updated.reminders, stats.peakCravingHour)
        }
        scheduleDebouncedDriveBackup(updated)
    }

    private fun persistCurrentVault(payload: VaultPayload, header: AccountHeader) {
        val dek = unlockedDek ?: return
        val dekCopy = dek.copyOf()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val existingVersion = storage.readEnvelope()?.vault?.version ?: 0
                val nowIso = nowIsoUtc()
                val json = storage.serializePayload(payload)
                val encrypted = VaultCrypto.encryptVault(json, dekCopy, existingVersion + 1, nowIso)
                storage.writeEnvelope(VaultFileEnvelope(AppConfig.VAULT_SCHEMA_VERSION, header, encrypted))
            } finally {
                VaultCrypto.wipe(dekCopy)
            }
        }
    }

    private fun scheduleDebouncedDriveBackup(payload: VaultPayload) {
        if (!payload.uiPrefs.autoBackupDrive || driveAccessTokenInMemory.isNullOrBlank()) return
        driveDebounceJob?.cancel()
        driveDebounceJob = viewModelScope.launch {
            delay(3_000L)
            triggerDriveBackupNow()
        }
    }

    private fun isLockedOutNow(): Boolean {
        val state = storage.readLockoutState()
        return state.lockedUntilMs > System.currentTimeMillis()
    }

    private fun recordAuthFailure() {
        val current = storage.readLockoutState()
        val nextFailures = current.failedAttempts + 1
        val lockMs = AuthValidator.lockoutDurationMsForFailures(nextFailures)
        val lockedUntil = if (lockMs > 0L) System.currentTimeMillis() + lockMs else 0L
        val next = LockoutState(failedAttempts = nextFailures, lockedUntilMs = lockedUntil)
        storage.writeLockoutState(next)
        _uiState.update { it.copy(lockoutState = next) }
        startLockoutTickerIfNeeded(next)
    }

    private fun startLockoutTickerIfNeeded(state: LockoutState) {
        lockoutTickerJob?.cancel()
        if (state.lockedUntilMs <= System.currentTimeMillis()) {
            _uiState.update { it.copy(lockoutRemainingSeconds = 0L) }
            return
        }
        lockoutTickerJob = viewModelScope.launch {
            while (true) {
                val remMs = state.lockedUntilMs - System.currentTimeMillis()
                if (remMs <= 0L) {
                    _uiState.update { it.copy(lockoutRemainingSeconds = 0L) }
                    break
                }
                _uiState.update { it.copy(lockoutRemainingSeconds = (remMs + 999L) / 1000L) }
                delay(1_000L)
            }
        }
    }

    fun showToast(message: String, isError: Boolean = false) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message, toastIsError = isError) }
        toastJob = viewModelScope.launch {
            delay(3_400L)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    private fun nowIsoUtc(): String = synchronized(isoFormat) { isoFormat.format(Date()) }

    private fun wipePendingSignUp() {
        VaultCrypto.wipe(pendingSignUpPasswordChars)
        pendingSignUpPasswordChars = null
        pendingSignUpRecoveryKey = null
        pendingSignUpIdentifier = null
        pendingSignUpIdentifierType = null
        VaultCrypto.wipe(pendingDek)
        pendingDek = null
        pendingHeader = null
        pendingPayload = null
    }

    private fun wipeAllSensitiveRam() {
        VaultCrypto.wipe(unlockedDek)
        unlockedDek = null
        wipePendingSignUp()
    }

    override fun onCleared() {
        super.onCleared()
        wipeAllSensitiveRam()
        driveAccessTokenInMemory = null
    }
}
