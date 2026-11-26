package com.example.auth

data class CountryCode(
    val iso: String,
    val name: String,
    val dialCode: String
)

data class PasswordStrength(
    val score: Int, // 0..4
    val label: String,
    val isValid: Boolean,
    val error: String? = null
)

object AuthValidator {
    const val MIN_PASSWORD_LENGTH = 12

    val COUNTRY_CODES = listOf(
        CountryCode("US", "United States", "+1"),
        CountryCode("IN", "India", "+91"),
        CountryCode("GB", "United Kingdom", "+44"),
        CountryCode("CA", "Canada", "+1"),
        CountryCode("AU", "Australia", "+61"),
        CountryCode("DE", "Germany", "+49"),
        CountryCode("FR", "France", "+33"),
        CountryCode("NL", "Netherlands", "+31"),
        CountryCode("BR", "Brazil", "+55"),
        CountryCode("JP", "Japan", "+81"),
        CountryCode("SG", "Singapore", "+65"),
        CountryCode("AE", "United Arab Emirates", "+971")
    )

    // Bundled local common-password rejection list (offline, zero network calls)
    private val COMMON_PASSWORDS = setOf(
        "password1234", "password12345", "123456789012", "1234567890123",
        "qwertyuiop12", "qwerty123456", "letmein12345", "welcome12345",
        "admin1234567", "iloveyou1234", "monkey123456", "dragon123456",
        "baseball1234", "football1234", "sunshine1234", "princess1234",
        "shadow123456", "superman1234", "trustno12345", "passw0rd1234",
        "abc123456789", "000000000000", "111111111111", "aaaaaaaaaaaa",
        "zzzzzzzzzzzz", "qwertyuiopas", "asdfghjklzxc", "correcthorse",
        "batterystaple", "dailylog1234", "quitweed1234", "changeme1234",
        "master123456", "michael12345", "charlie12345", "donald123456",
        "ashley123456", "bailey123456", "starwars1234", "freedom12345",
        "whatever1234", "computer1234", "internet1234", "secret123456",
        "mysecretpass", "passwordpassword", "123412341234", "987654321098"
    )

    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9._%+\\-]{1,64}@[A-Za-z0-9.\\-]{1,253}\\.[A-Za-z]{2,24}$"
    )

    private val E164_REGEX = Regex("^\\+[1-9][0-9]{6,14}$")

    fun isValidEmail(input: String): Boolean = EMAIL_REGEX.matches(input.trim())

    fun normalizePhoneToE164(dialCode: String, nationalNumber: String): String {
        val digits = nationalNumber.filter { it.isDigit() }
        val cleanDial = dialCode.trim().let { if (it.startsWith("+")) it else "+$it" }
        return "$cleanDial$digits"
    }

    fun isValidE164(phone: String): Boolean = E164_REGEX.matches(phone.trim())

    fun validateIdentifier(type: String, rawEmail: String, dialCode: String, nationalPhone: String): Pair<String?, String?> {
        return if (type == "email") {
            val trimmed = rawEmail.trim().lowercase()
            if (isValidEmail(trimmed)) trimmed to null
            else null to "Enter a valid email address."
        } else {
            val e164 = normalizePhoneToE164(dialCode, nationalPhone)
            if (isValidE164(e164)) e164 to null
            else null to "Enter a valid phone number (7 to 15 digits)."
        }
    }

    fun evaluatePassword(password: String, identifier: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(0, "Enter a password", false, null)
        }
        val lower = password.lowercase()
        val compact = lower.replace(Regex("[\\s\\-_.]"), "")

        if (password.length < MIN_PASSWORD_LENGTH) {
            return PasswordStrength(
                score = 1,
                label = "Too short",
                isValid = false,
                error = "Use at least $MIN_PASSWORD_LENGTH characters."
            )
        }

        if (lower in COMMON_PASSWORDS || compact in COMMON_PASSWORDS) {
            return PasswordStrength(
                score = 1,
                label = "Common password",
                isValid = false,
                error = "This password appears on a common password list."
            )
        }

        // Check if password contains identifier segments (email local part or phone digits)
        val trimmedId = identifier.trim().lowercase()
        if (trimmedId.isNotBlank()) {
            val localPart = trimmedId.substringBefore("@").filter { it.isLetterOrDigit() }
            val digitsOnly = trimmedId.filter { it.isDigit() }
            val containsLocal = localPart.length >= 4 && lower.contains(localPart)
            val containsPhone = digitsOnly.length >= 6 && password.filter { it.isDigit() }.contains(digitsOnly.takeLast(6))
            if (containsLocal || containsPhone) {
                return PasswordStrength(
                    score = 1,
                    label = "Contains account info",
                    isValid = false,
                    error = "Password cannot contain your email or phone number."
                )
            }
        }

        var classes = 0
        if (password.any { it.isLowerCase() }) classes++
        if (password.any { it.isUpperCase() }) classes++
        if (password.any { it.isDigit() }) classes++
        if (password.any { !it.isLetterOrDigit() }) classes++

        val uniqueChars = password.toSet().size
        val score = when {
            password.length >= 16 && classes >= 3 && uniqueChars >= 10 -> 4
            password.length >= 14 && classes >= 3 && uniqueChars >= 8 -> 3
            password.length >= 12 && (classes >= 2 || uniqueChars >= 8) -> 2
            else -> 1
        }

        val label = when (score) {
            4 -> "Strong"
            3 -> "Good"
            2 -> "Fair"
            else -> "Weak"
        }

        val valid = score >= 2
        val error = if (!valid) "Mix letters, numbers, or symbols for a stronger password." else null
        return PasswordStrength(score = score, label = label, isValid = valid, error = error)
    }

    /**
     * Exponential lockout durations after 5 consecutive failed attempts:
     * 5 -> 30s, 6 -> 1m, 7 -> 5m, 8 -> 15m, 9 -> 30m, 10+ -> 60m.
     */
    fun lockoutDurationMsForFailures(failedAttempts: Int): Long {
        if (failedAttempts < 5) return 0L
        return when (failedAttempts) {
            5 -> 30_000L
            6 -> 60_000L
            7 -> 5 * 60_000L
            8 -> 15 * 60_000L
            9 -> 30 * 60_000L
            else -> 60 * 60_000L
        }
    }
}
