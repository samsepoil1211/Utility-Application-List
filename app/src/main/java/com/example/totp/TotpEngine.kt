package com.example.totp

import com.example.config.AppConfig
import com.example.crypto.VaultCrypto
import com.example.model.BackupCodeHash
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Offline RFC 6238 TOTP implementation (HMAC-SHA1, 6 digits, 30-second window, ±1 step tolerance)
 * plus single-use backup code generation and verification.
 */
object TotpEngine {
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val BACKUP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val PERIOD_SECONDS = 30L
    private const val DIGITS = 6
    private val secureRandom = SecureRandom()

    fun generateSecret(byteLength: Int = 20): String {
        val raw = ByteArray(byteLength)
        secureRandom.nextBytes(raw)
        return try {
            base32Encode(raw)
        } finally {
            VaultCrypto.wipe(raw)
        }
    }

    fun formatSecretForDisplay(secret: String): String =
        secret.chunked(4).joinToString(" ")

    fun buildOtpAuthUri(
        identifier: String,
        secret: String,
        issuer: String = AppConfig.APP_NAME_PUBLIC
    ): String {
        val encIssuer = URLEncoder.encode(issuer, "UTF-8").replace("+", "%20")
        val encAccount = URLEncoder.encode(identifier, "UTF-8").replace("+", "%20")
        return "otpauth://totp/$encIssuer:$encAccount?secret=$secret&issuer=$encIssuer&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS"
    }

    fun generateCode(secretBase32: String, timeMillis: Long = System.currentTimeMillis()): String {
        val counter = (timeMillis / 1000L) / PERIOD_SECONDS
        return generateCodeForCounter(secretBase32, counter)
    }

    fun verifyCode(
        secretBase32: String,
        userInput: String,
        timeMillis: Long = System.currentTimeMillis(),
        window: Int = 1
    ): Boolean {
        val cleaned = userInput.filter { it.isDigit() }
        if (cleaned.length != DIGITS || secretBase32.isBlank()) return false
        val currentCounter = (timeMillis / 1000L) / PERIOD_SECONDS
        var matched = false
        for (offset in -window..window) {
            val expected = generateCodeForCounter(secretBase32, currentCounter + offset)
            if (VaultCrypto.constantTimeEquals(cleaned, expected)) {
                matched = true
            }
        }
        return matched
    }

    fun secondsRemaining(timeMillis: Long = System.currentTimeMillis()): Int {
        val elapsed = ((timeMillis / 1000L) % PERIOD_SECONDS).toInt()
        return (PERIOD_SECONDS.toInt() - elapsed).coerceIn(1, PERIOD_SECONDS.toInt())
    }

    /**
     * Generates 10 single-use backup codes (plain text to show once) and their salted hashes
     * to store inside the encrypted vault.
     */
    fun generateBackupCodes(count: Int = 10): Pair<List<String>, List<BackupCodeHash>> {
        val plainCodes = mutableListOf<String>()
        val hashes = mutableListOf<BackupCodeHash>()
        repeat(count) {
            val raw = CharArray(8) { BACKUP_ALPHABET[secureRandom.nextInt(BACKUP_ALPHABET.length)] }
            val formatted = "${String(raw, 0, 4)}-${String(raw, 4, 4)}"
            VaultCrypto.wipe(raw)
            val saltB64 = VaultCrypto.encodeB64(VaultCrypto.generateSalt(12))
            val hash = VaultCrypto.hashBackupCode(formatted, saltB64)
            plainCodes.add(formatted)
            hashes.add(BackupCodeHash(salt = saltB64, hash = hash, used = false))
        }
        return plainCodes to hashes
    }

    /**
     * Checks if [input] matches any unused backup code hash.
     * Returns index of the matching code, or -1 if invalid or already used.
     */
    fun findMatchingBackupCodeIndex(input: String, hashes: List<BackupCodeHash>): Int {
        val cleaned = input.uppercase().replace("-", "").replace(" ", "").trim()
        if (cleaned.length < 8) return -1
        for (i in hashes.indices) {
            val entry = hashes[i]
            if (!entry.used) {
                val candidate = VaultCrypto.hashBackupCode(cleaned, entry.salt)
                if (VaultCrypto.constantTimeEquals(candidate, entry.hash)) {
                    return i
                }
            }
        }
        return -1
    }

    private fun generateCodeForCounter(secretBase32: String, counter: Long): String {
        val keyBytes = base32Decode(secretBase32)
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        return try {
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
            val hmac = mac.doFinal(counterBytes)
            val offset = hmac[hmac.size - 1].toInt() and 0x0F
            val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
                ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                (hmac[offset + 3].toInt() and 0xFF)
            val mod = 10.0.pow(DIGITS).toInt()
            val otp = binary % mod
            otp.toString().padStart(DIGITS, '0')
        } finally {
            VaultCrypto.wipe(keyBytes)
        }
    }

    fun base32Encode(data: ByteArray): String {
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(BASE32_ALPHABET[(buffer ushr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString()
    }

    fun base32Decode(encoded: String): ByteArray {
        val cleaned = encoded.uppercase().filter { it in BASE32_ALPHABET }
        val out = ByteArray((cleaned.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var idx = 0
        for (ch in cleaned) {
            val value = BASE32_ALPHABET.indexOf(ch)
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                if (idx < out.size) {
                    out[idx++] = ((buffer ushr bitsLeft) and 0xFF).toByte()
                }
            }
        }
        return out
    }
}
