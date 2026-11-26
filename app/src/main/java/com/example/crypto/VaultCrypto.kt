package com.example.crypto

import android.os.Build
import android.util.Base64
import com.example.config.AppConfig
import com.example.model.EncryptedVaultBlob
import com.example.model.WrappedKeyBlob
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption & account key-wrapping primitives using standard JCA / BoringSSL.
 *
 * Key hierarchy:
 * - Random 256-bit Data Encryption Key (DEK) encrypts the entire vault via AES-256-GCM with
 *   [EncryptedVaultBlob.updatedAt] authenticated as AAD.
 * - DEK is wrapped twice (AES-256-GCM):
 *   (a) Password KEK derived via PBKDF2-HMAC-SHA256 (310,000+ iterations, random salt mixed with identifier).
 *   (b) Recovery KEK derived via PBKDF2-HMAC-SHA256 (310,000+ iterations, separate random salt) from the
 *       one-time 24-character Recovery Key.
 */
object VaultCrypto {
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val DEK_BYTES = 32
    private const val SALT_BYTES = 16
    private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Unambiguous 32-char alphabet

    private val secureRandom = SecureRandom()

    fun generateDek(): ByteArray = ByteArray(DEK_BYTES).also { secureRandom.nextBytes(it) }

    fun generateSalt(length: Int = SALT_BYTES): ByteArray =
        ByteArray(length).also { secureRandom.nextBytes(it) }

    fun generateIv(): ByteArray = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }

    /**
     * Generates a 24-character Recovery Key formatted as 6 groups of 4 (120 bits of entropy).
     */
    fun generateRecoveryKey(): String {
        val sb = StringBuilder(29)
        for (i in 0 until 24) {
            if (i > 0 && i % 4 == 0) sb.append('-')
            sb.append(RECOVERY_ALPHABET[secureRandom.nextInt(RECOVERY_ALPHABET.length)])
        }
        return sb.toString()
    }

    fun normalizeRecoveryKey(raw: String): String =
        raw.uppercase().filter { it in RECOVERY_ALPHABET }

    /**
     * Derives a 256-bit Key Encryption Key using PBKDF2-HMAC-SHA256.
     * Mixes [identifierContext] into the salt via SHA-256 when provided so password KEKs are
     * domain-separated per account identifier.
     */
    fun deriveKek(
        secret: CharArray,
        salt: ByteArray,
        identifierContext: String? = null,
        iterations: Int = AppConfig.PBKDF2_ITERATIONS
    ): ByteArray {
        val effectiveSalt = if (!identifierContext.isNullOrBlank()) {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.update(0.toByte())
            md.update(identifierContext.trim().lowercase().toByteArray(StandardCharsets.UTF_8))
            md.digest()
        } else {
            salt
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val spec = PBEKeySpec(secret, effectiveSalt, iterations, 256)
            try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        } else {
            pbkdf2HmacSha256Fallback(secret, effectiveSalt, iterations, 32)
        }
    }

    fun wrapDek(
        dek: ByteArray,
        kek: ByteArray,
        salt: ByteArray,
        aad: String,
        iterations: Int = AppConfig.PBKDF2_ITERATIONS
    ): WrappedKeyBlob {
        val iv = generateIv()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(dek)
        return WrappedKeyBlob(
            salt = encodeB64(salt),
            iv = encodeB64(iv),
            ciphertext = encodeB64(ciphertext),
            iterations = iterations
        )
    }

    fun unwrapDek(
        wrapped: WrappedKeyBlob,
        kek: ByteArray,
        aad: String
    ): ByteArray {
        val iv = decodeB64(wrapped.iv)
        val ciphertext = decodeB64(wrapped.ciphertext)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }

    fun encryptVault(
        plaintextJson: String,
        dek: ByteArray,
        version: Int,
        updatedAt: String
    ): EncryptedVaultBlob {
        val iv = generateIv()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        // Authenticate updatedAt as AAD to prevent timestamp tampering / rollback spoofing
        cipher.updateAAD(updatedAt.toByteArray(StandardCharsets.UTF_8))
        val plaintextBytes = plaintextJson.toByteArray(StandardCharsets.UTF_8)
        val ciphertext = try {
            cipher.doFinal(plaintextBytes)
        } finally {
            Arrays.fill(plaintextBytes, 0.toByte())
        }
        return EncryptedVaultBlob(
            version = version,
            updatedAt = updatedAt,
            iv = encodeB64(iv),
            ciphertext = encodeB64(ciphertext)
        )
    }

    fun decryptVault(blob: EncryptedVaultBlob, dek: ByteArray): String {
        val iv = decodeB64(blob.iv)
        val ciphertext = decodeB64(blob.ciphertext)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(blob.updatedAt.toByteArray(StandardCharsets.UTF_8))
        val plaintextBytes = cipher.doFinal(ciphertext)
        return try {
            String(plaintextBytes, StandardCharsets.UTF_8)
        } finally {
            Arrays.fill(plaintextBytes, 0.toByte())
        }
    }

    fun hashBackupCode(code: String, saltB64: String): String {
        val normalized = code.uppercase().replace("-", "").replace(" ", "").trim()
        val md = MessageDigest.getInstance("SHA-256")
        md.update(decodeB64(saltB64))
        md.update(normalized.toByteArray(StandardCharsets.UTF_8))
        return encodeB64(md.digest())
    }

    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(
            a.toByteArray(StandardCharsets.UTF_8),
            b.toByteArray(StandardCharsets.UTF_8)
        )

    fun encodeB64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decodeB64(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.NO_WRAP)

    fun wipe(bytes: ByteArray?) {
        if (bytes != null) Arrays.fill(bytes, 0.toByte())
    }

    fun wipe(chars: CharArray?) {
        if (chars != null) Arrays.fill(chars, '\u0000')
    }

    /**
     * RFC 2898 PBKDF2-HMAC-SHA256 fallback for API 24-25 where PBKDF2WithHmacSHA256
     * is unavailable from SecretKeyFactory.
     */
    private fun pbkdf2HmacSha256Fallback(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        dkLen: Int
    ): ByteArray {
        val passwordBytes = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(password))
        val keyBytes = ByteArray(passwordBytes.remaining())
        passwordBytes.get(keyBytes)
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
            val hLen = mac.macLength
            val blocks = (dkLen + hLen - 1) / hLen
            val output = ByteArray(blocks * hLen)
            val blockIndex = ByteArray(4)

            for (i in 1..blocks) {
                ByteBuffer.wrap(blockIndex).putInt(i)
                mac.update(salt)
                mac.update(blockIndex)
                var u = mac.doFinal()
                val t = u.copyOf()
                for (c in 1 until iterations) {
                    u = mac.doFinal(u)
                    for (j in t.indices) {
                        t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                    }
                }
                System.arraycopy(t, 0, output, (i - 1) * hLen, hLen)
            }
            return output.copyOf(dkLen)
        } finally {
            Arrays.fill(keyBytes, 0.toByte())
        }
    }
}
