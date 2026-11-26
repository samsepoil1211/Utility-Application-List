package com.example

import com.example.auth.AuthValidator
import com.example.crypto.VaultCrypto
import com.example.totp.QrCodeGenerator
import com.example.totp.TotpEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VaultSecurityRobolectricTest {

    @Test
    fun aesGcmVaultRoundTripAndAadTamperDetection() {
        val dek = VaultCrypto.generateDek()
        val plaintext = """{"onboardingComplete":true,"cravings":[]}"""
        val updatedAt = "2026-11-26T16:30:00.000Z"

        val blob = VaultCrypto.encryptVault(plaintext, dek, version = 1, updatedAt = updatedAt)
        val decrypted = VaultCrypto.decryptVault(blob, dek)
        assertEquals(plaintext, decrypted)

        // Tampering with updatedAt MUST fail GCM AAD authentication
        val tampered = blob.copy(updatedAt = "2026-11-27T00:00:00.000Z")
        try {
            VaultCrypto.decryptVault(tampered, dek)
            fail("Expected AEADBadTagException when updatedAt AAD is tampered")
        } catch (_: AEADBadTagException) {
            // Expected
        }
    }

    @Test
    fun dualDekWrappingWithPasswordAndRecoveryKey() {
        val dek = VaultCrypto.generateDek()
        val id = "person@example.com"
        val pwSalt = VaultCrypto.generateSalt()
        val recSalt = VaultCrypto.generateSalt()
        val recoveryKey = VaultCrypto.generateRecoveryKey()

        // Use smaller iteration count in unit test for sub-second execution while verifying full path
        val pwKek = VaultCrypto.deriveKek("Correct-Horse-Battery-99!".toCharArray(), pwSalt, id, 1_000)
        val recKek = VaultCrypto.deriveKek(VaultCrypto.normalizeRecoveryKey(recoveryKey).toCharArray(), recSalt, null, 1_000)

        val pwWrap = VaultCrypto.wrapDek(dek, pwKek, pwSalt, "pw:$id", 1_000)
        val recWrap = VaultCrypto.wrapDek(dek, recKek, recSalt, "rec:$id", 1_000)

        assertArrayEquals(dek, VaultCrypto.unwrapDek(pwWrap, pwKek, "pw:$id"))
        assertArrayEquals(dek, VaultCrypto.unwrapDek(recWrap, recKek, "rec:$id"))
    }

    @Test
    fun totpRfc6238AndBackupCodesWorkOffline() {
        // RFC 6238 / RFC 4226 standard ASCII secret "12345678901234567890" -> Base32 "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val codeAt59s = TotpEngine.generateCode(rfcSecret, 59_000L)
        assertEquals("287082", codeAt59s)
        assertTrue(TotpEngine.verifyCode(rfcSecret, "287082", 59_000L, window = 1))
        assertTrue(TotpEngine.verifyCode(rfcSecret, "287082", 89_000L, window = 1)) // +1 step window
        assertFalse(TotpEngine.verifyCode(rfcSecret, "000000", 59_000L, window = 1))

        val (plainCodes, hashes) = TotpEngine.generateBackupCodes(10)
        assertEquals(10, plainCodes.size)
        assertEquals(10, hashes.size)
        val matchIdx = TotpEngine.findMatchingBackupCodeIndex(plainCodes[3], hashes)
        assertEquals(3, matchIdx)
        val usedHashes = hashes.mapIndexed { idx, h -> if (idx == 3) h.copy(used = true) else h }
        assertEquals(-1, TotpEngine.findMatchingBackupCodeIndex(plainCodes[3], usedHashes))
    }

    @Test
    fun localQrGeneratorEncodesOtpAuthUri() {
        val secret = TotpEngine.generateSecret()
        val uri = TotpEngine.buildOtpAuthUri("person@example.com", secret)
        val matrix = QrCodeGenerator.encode(uri)
        assertTrue(matrix.size >= 29)
        // Top-left 7x7 finder pattern must have dark outer border & dark center
        assertTrue(matrix.modules[0][0])
        assertTrue(matrix.modules[3][3])
        assertFalse(matrix.modules[1][1])
    }

    @Test
    fun authValidatorEnforcesPolicyAndExponentialLockout() {
        assertFalse(AuthValidator.evaluatePassword("short123", "a@b.com").isValid)
        assertFalse(AuthValidator.evaluatePassword("password1234", "a@b.com").isValid)
        assertFalse(AuthValidator.evaluatePassword("myalexuser99!!", "alexuser@example.com").isValid)
        assertTrue(AuthValidator.evaluatePassword("Quiet-Dusk-7491!", "alex@example.com").isValid)

        assertTrue(AuthValidator.isValidE164(AuthValidator.normalizePhoneToE164("+91", "98765 43210")))
        assertEquals(0L, AuthValidator.lockoutDurationMsForFailures(4))
        assertEquals(30_000L, AuthValidator.lockoutDurationMsForFailures(5))
        assertEquals(60_000L, AuthValidator.lockoutDurationMsForFailures(6))
        assertEquals(5 * 60_000L, AuthValidator.lockoutDurationMsForFailures(7))
        assertNotEquals(0L, AuthValidator.lockoutDurationMsForFailures(8))
    }
}
