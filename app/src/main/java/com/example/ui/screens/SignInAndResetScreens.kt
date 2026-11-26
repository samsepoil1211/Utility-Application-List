package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.auth.AuthValidator
import com.example.config.AppConfig
import com.example.ui.components.AmberPrimaryButton
import com.example.ui.components.InstrumentTextField
import com.example.ui.components.KeyDerivationBanner
import com.example.ui.components.QuietOutlineButton
import com.example.ui.icons.InstrumentIcons
import com.example.ui.theme.LocalInstrumentColors
import com.example.viewmodel.AppRoute
import com.example.viewmodel.DailyLogUiState
import com.example.viewmodel.ResetStep

@Composable
fun SignInScreen(
    route: AppRoute.SignIn,
    state: DailyLogUiState,
    onBack: () -> Unit,
    onPasswordSubmit: (String, String) -> Unit,
    onTwoFactorSubmit: (String) -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onClearError: () -> Unit
) {
    BackHandler { onBack() }
    val colors = LocalInstrumentColors.current
    var identifier by remember(state.accountHeader?.identifier) {
        mutableStateOf(state.accountHeader?.identifier.orEmpty())
    }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var totpCode by remember { mutableStateOf("") }

    val lockedOut = state.lockoutRemainingSeconds > 0L

    AuthScaffold(
        title = if (route.requireTwoFactorStep) "Two-factor verification" else "Sign in",
        subtitle = if (route.requireTwoFactorStep) {
            "Enter the 6-digit code from your authenticator app or an unused backup code."
        } else {
            "Unlock your local encrypted ${AppConfig.APP_NAME_PUBLIC} vault offline."
        },
        onBack = onBack
    ) {
        if (lockedOut) {
            LockoutNotice(remainingSeconds = state.lockoutRemainingSeconds)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!route.requireTwoFactorStep) {
            InstrumentTextField(
                value = identifier,
                onValueChange = { identifier = it; onClearError() },
                label = "Email or phone (E.164)",
                placeholder = "you@example.com or +15550192834",
                enabled = !lockedOut && !state.isCryptoBusy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.testTag("signin_identifier_input")
            )
            Spacer(modifier = Modifier.height(14.dp))
            InstrumentTextField(
                value = password,
                onValueChange = { password = it; onClearError() },
                label = "Password",
                isPassword = true,
                passwordVisible = showPassword,
                onTogglePassword = { showPassword = !showPassword },
                enabled = !lockedOut && !state.isCryptoBusy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.testTag("signin_password_input")
            )

            if (!state.authError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = state.authError, style = MaterialTheme.typography.bodySmall, color = colors.coral)
            }

            if (state.isCryptoBusy) {
                Spacer(modifier = Modifier.height(12.dp))
                KeyDerivationBanner(label = state.cryptoBusyLabel)
            }

            Spacer(modifier = Modifier.height(20.dp))
            AmberPrimaryButton(
                text = "Continue",
                loading = state.isCryptoBusy,
                enabled = !lockedOut && identifier.isNotBlank() && password.isNotBlank(),
                onClick = { onPasswordSubmit(identifier, password) },
                icon = InstrumentIcons.Lock,
                modifier = Modifier.testTag("signin_submit_button")
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onForgotPassword) {
                    Text("Reset password", color = colors.lilac)
                }
                if (!state.hasLocalVault) {
                    TextButton(onClick = onCreateAccount) {
                        Text("Create account", color = colors.muted)
                    }
                }
            }
        } else {
            InstrumentTextField(
                value = totpCode,
                onValueChange = { totpCode = it.uppercase().take(9); onClearError() },
                label = "6-digit code or backup code",
                placeholder = "123456 or XXXX-XXXX",
                enabled = !lockedOut && !state.isCryptoBusy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.testTag("signin_2fa_input")
            )
            if (!state.authError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = state.authError, style = MaterialTheme.typography.bodySmall, color = colors.coral)
            }
            Spacer(modifier = Modifier.height(20.dp))
            AmberPrimaryButton(
                text = "Verify and unlock",
                enabled = !lockedOut && totpCode.trim().length >= 6,
                onClick = { onTwoFactorSubmit(totpCode) },
                icon = InstrumentIcons.Shield,
                modifier = Modifier.testTag("signin_2fa_submit_button")
            )
        }
    }
}

@Composable
fun QuickUnlockScreen(
    state: DailyLogUiState,
    onUnlock: (String) -> Unit,
    onFullSignIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onClearError: () -> Unit
) {
    val colors = LocalInstrumentColors.current
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val lockedOut = state.lockoutRemainingSeconds > 0L

    AuthScaffold(
        title = "Vault locked",
        subtitle = "Locked automatically after inactivity. Enter your password to continue.",
        onBack = null
    ) {
        if (lockedOut) {
            LockoutNotice(remainingSeconds = state.lockoutRemainingSeconds)
            Spacer(modifier = Modifier.height(16.dp))
        }

        state.accountHeader?.identifier?.let { id ->
            Text(
                text = id,
                style = MaterialTheme.typography.titleSmall,
                color = colors.amber,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }

        InstrumentTextField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = "Password",
            isPassword = true,
            passwordVisible = showPassword,
            onTogglePassword = { showPassword = !showPassword },
            enabled = !lockedOut && !state.isCryptoBusy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.testTag("quick_unlock_password_input")
        )

        if (!state.authError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = state.authError, style = MaterialTheme.typography.bodySmall, color = colors.coral)
        }

        if (state.isCryptoBusy) {
            Spacer(modifier = Modifier.height(12.dp))
            KeyDerivationBanner(label = state.cryptoBusyLabel)
        }

        Spacer(modifier = Modifier.height(20.dp))
        AmberPrimaryButton(
            text = "Unlock",
            loading = state.isCryptoBusy,
            enabled = !lockedOut && password.isNotBlank(),
            onClick = { onUnlock(password) },
            icon = InstrumentIcons.Lock,
            modifier = Modifier.testTag("quick_unlock_button")
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onForgotPassword) {
                Text("Reset password", color = colors.lilac)
            }
            TextButton(onClick = onFullSignIn) {
                Text("Full sign-in", color = colors.muted)
            }
        }
    }
}

@Composable
fun ResetPasswordScreen(
    route: AppRoute.ResetPassword,
    state: DailyLogUiState,
    onBack: () -> Unit,
    onVerifyRecoveryKey: (String, String) -> Unit,
    onVerify2Fa: (String) -> Unit,
    onCommitNewPassword: (String, String) -> Unit,
    onClearError: () -> Unit
) {
    BackHandler { onBack() }
    val colors = LocalInstrumentColors.current
    var identifier by remember(state.accountHeader?.identifier) {
        mutableStateOf(state.accountHeader?.identifier.orEmpty())
    }
    var recoveryKey by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }

    val strength = remember(newPassword, identifier) {
        AuthValidator.evaluatePassword(newPassword, identifier)
    }

    AuthScaffold(
        title = "Reset password",
        subtitle = "Recover access using your 24-character recovery key and 2FA code. Your encryption key will be re-wrapped without re-encrypting history.",
        onBack = onBack
    ) {
        when (route.step) {
            ResetStep.EnterRecoveryKey -> {
                InstrumentTextField(
                    value = identifier,
                    onValueChange = { identifier = it; onClearError() },
                    label = "Account email or phone"
                )
                Spacer(modifier = Modifier.height(14.dp))
                InstrumentTextField(
                    value = recoveryKey,
                    onValueChange = { recoveryKey = it.uppercase(); onClearError() },
                    label = "24-character recovery key",
                    placeholder = "XXXX-XXXX-XXXX-XXXX-XXXX-XXXX",
                    modifier = Modifier.testTag("reset_recovery_key_input")
                )
                if (!state.authError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(state.authError, style = MaterialTheme.typography.bodySmall, color = colors.coral)
                }
                if (state.isCryptoBusy) {
                    Spacer(modifier = Modifier.height(12.dp))
                    KeyDerivationBanner(label = state.cryptoBusyLabel)
                }
                Spacer(modifier = Modifier.height(20.dp))
                AmberPrimaryButton(
                    text = "Verify recovery key",
                    loading = state.isCryptoBusy,
                    enabled = identifier.isNotBlank() && recoveryKey.length >= 24,
                    onClick = { onVerifyRecoveryKey(identifier, recoveryKey) },
                    icon = InstrumentIcons.Key
                )
            }

            ResetStep.VerifyTwoFactor -> {
                InstrumentTextField(
                    value = totpCode,
                    onValueChange = { totpCode = it.uppercase().take(9); onClearError() },
                    label = "6-digit authenticator code or backup code",
                    placeholder = "123456"
                )
                if (!state.authError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(state.authError, style = MaterialTheme.typography.bodySmall, color = colors.coral)
                }
                Spacer(modifier = Modifier.height(20.dp))
                AmberPrimaryButton(
                    text = "Verify 2FA",
                    enabled = totpCode.trim().length >= 6,
                    onClick = { onVerify2Fa(totpCode) },
                    icon = InstrumentIcons.Shield
                )
            }

            ResetStep.SetNewPassword -> {
                InstrumentTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; onClearError() },
                    label = "New password (min 12 characters)",
                    isPassword = true,
                    passwordVisible = showNew,
                    onTogglePassword = { showNew = !showNew },
                    isError = newPassword.isNotEmpty() && !strength.isValid,
                    supportingText = strength.error
                )
                Spacer(modifier = Modifier.height(14.dp))
                InstrumentTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; onClearError() },
                    label = "Confirm new password",
                    isPassword = true,
                    passwordVisible = showNew,
                    onTogglePassword = { showNew = !showNew }
                )
                if (!state.authError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(state.authError, style = MaterialTheme.typography.bodySmall, color = colors.coral)
                }
                if (state.isCryptoBusy) {
                    Spacer(modifier = Modifier.height(12.dp))
                    KeyDerivationBanner(label = state.cryptoBusyLabel)
                }
                Spacer(modifier = Modifier.height(20.dp))
                AmberPrimaryButton(
                    text = "Save new password",
                    loading = state.isCryptoBusy,
                    enabled = strength.isValid && newPassword == confirmPassword,
                    onClick = { onCommitNewPassword(newPassword, confirmPassword) }
                )
            }
        }
    }
}

@Composable
private fun LockoutNotice(remainingSeconds: Long) {
    val colors = LocalInstrumentColors.current
    val mins = remainingSeconds / 60L
    val secs = remainingSeconds % 60L
    val formatted = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, RoundedCornerShape(12.dp))
            .border(1.dp, colors.coral.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "Sign-in temporarily paused",
            style = MaterialTheme.typography.titleSmall,
            color = colors.coral
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Try again in $formatted.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted
        )
    }
}
