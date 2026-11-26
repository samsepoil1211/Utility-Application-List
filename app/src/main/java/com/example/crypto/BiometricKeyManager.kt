package com.example.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.config.AppConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore + BiometricPrompt manager for fingerprint unlock.
 *
 * Security model:
 * - Generates a non-exportable AES-256-GCM key inside Android Keystore requiring Class 3
 *   biometric authentication ([BiometricManager.Authenticators.BIOMETRIC_STRONG]).
 * - Automatically invalidates if new biometric credentials are enrolled on the device.
 * - Uses [BiometricPrompt.CryptoObject] so the Data Encryption Key (DEK) can ONLY be
 *   unwrapped after the Android biometric subsystem unlocks the Keystore cipher.
 */
object BiometricKeyManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "dailylog_biometric_dek_kek"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    enum class Availability {
        Available,
        NoneEnrolled,
        Unavailable
    }

    fun checkAvailability(context: Context): Availability {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NoneEnrolled
            else -> Availability.Unavailable
        }
    }

    fun deleteKey() {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) {
                ks.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {
            // Ignore cleanup failure
        }
    }

    /**
     * Generates a fresh biometric-bound Keystore key and returns an initialized ENCRYPT cipher
     * ready to pass to [BiometricPrompt.CryptoObject].
     */
    fun createEncryptCipher(): Cipher {
        deleteKey()
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        keyGenerator.init(spec)
        val secretKey = keyGenerator.generateKey()
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
        }
    }

    /**
     * Returns a DECRYPT cipher initialized with the stored IV, or null if the Keystore key
     * was missing or permanently invalidated (e.g., new fingerprint added).
     */
    fun createDecryptCipher(ivBase64: String): Cipher? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = ks.getKey(KEY_ALIAS, null) as? SecretKey ?: return null
            val iv = VaultCrypto.decodeB64(ivBase64)
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteKey()
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Launches the system BiometricPrompt bound to [cipher].
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Use password",
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authedCipher = result.cryptoObject?.cipher
                    if (authedCipher != null) {
                        onSuccess(authedCipher)
                    } else {
                        onError("Biometric cipher unavailable. Use your password instead.")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // System prompt already displays "Not recognized" inline; keep listening
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    fun defaultPromptTitle(): String = AppConfig.APP_NAME_PUBLIC
}
